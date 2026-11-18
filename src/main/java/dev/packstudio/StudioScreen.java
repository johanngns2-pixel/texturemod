package dev.packstudio;

import dev.packstudio.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;

/** Vanilla-Stil: Standard-Buttons/Textfelder. Links Packs+Optionen, Mitte Live-Vorschau, rechts Item-Waehler. */
public class StudioScreen extends Screen {
    private static final Identifier PREVIEW = Identifier.of("packstudio", "preview");
    private static final Identifier PICKER = Identifier.of("packstudio", "picker");
    private static final int PT = 36, PG = 3; // Picker-Kachel, Abstand
    private static final String DEFAULT_VARIANTS = "Crimson:#8A0C05, Blossom:#5F173A, Violet:#35105E, Void:#19191E, Mica:#B8860B, Silver:#A7A9AC";

    private final Screen parent;
    private List<Pack> packs = new ArrayList<>();
    private int packIdx = 0;
    private List<String> allItems = new ArrayList<>();
    private final Map<String, Anim> animCache = new HashMap<>();
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private int mode = 0; // 0 Farbvarianten, 1 Packs, 2 Raum
    private int scroll = 0;
    private String filter = "";
    private String status = "";
    private volatile int progress = -1;

    private TextFieldWidget fTitle, fFilter, fVariants;
    private int pickX, pickY, pickW, pickH, prevX, prevY, prevW, prevH;

    // Vorschau-Rendering im Hintergrund
    private volatile BufferedImage nextPreview;
    private volatile boolean dirty = true;
    private volatile boolean running = true;
    private Thread worker;
    private NativeImageBackedTexture previewTex, pickerTex;
    private int pickerCols = 1;
    private List<String> shown = new ArrayList<>();
    private int pw, ph;

    public StudioScreen(Screen parent) {
        super(Text.translatable("packstudio.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        packs = Pack.scan(PackStudio.packsDir());
        if (packIdx >= packs.size()) packIdx = 0;
        loadPack();
        int lx = 8, lw = 170, y = 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.pack", packs.isEmpty() ? "-" : Text.literal(packs.get(packIdx).name).getString()), b -> {
            if (packs.isEmpty()) return;
            packIdx = (packIdx + 1) % packs.size(); loadPack(); clearAnimCache(); b.setMessage(Text.translatable("packstudio.pack", packs.get(packIdx).name)); dirty = true;
        }).dimensions(lx, y, lw, 20).build()); y += 24;
        addDrawableChild(ButtonWidget.builder(modeText(), b -> { mode = (mode + 1) % 3; b.setMessage(modeText()); dirty = true; }).dimensions(lx, y, lw, 20).build()); y += 28;
        fTitle = new TextFieldWidget(textRenderer, lx, y, lw, 18, Text.translatable("packstudio.titlefield"));
        fTitle.setMaxLength(60); fTitle.setText(fTitle == null ? "" : "Kino's Ribbon - Colour Variants"); fTitle.setChangedListener(s -> dirty = true);
        addDrawableChild(fTitle); y += 28;
        fVariants = new TextFieldWidget(textRenderer, lx, y, lw, 18, Text.translatable("packstudio.variants"));
        fVariants.setMaxLength(400); fVariants.setText(DEFAULT_VARIANTS); fVariants.setChangedListener(s -> dirty = true);
        addDrawableChild(fVariants); y += 28;
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.export"), b -> export(true)).dimensions(lx, y, lw, 20).build()); y += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.png"), b -> export(false)).dimensions(lx, y, 82, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.folder"), b -> openFolder()).dimensions(lx + 88, y, 82, 20).build()); y += 24;
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.close"), b -> close()).dimensions(lx, height - 28, lw, 20).build());

        pickW = 240; pickX = width - pickW - 8; pickY = 52; pickH = height - pickY - 36;
        fFilter = new TextFieldWidget(textRenderer, pickX, 24, pickW - 78, 18, Text.translatable("packstudio.search"));
        fFilter.setChangedListener(s -> { filter = s.toLowerCase(Locale.ROOT); scroll = 0; rebuildPicker(); });
        addDrawableChild(fFilter);
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.all"), b -> { selected.addAll(shown); rebuildPicker(); dirty = true; }).dimensions(pickX + pickW - 74, 23, 36, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("packstudio.none"), b -> { selected.clear(); rebuildPicker(); dirty = true; }).dimensions(pickX + pickW - 36, 23, 36, 20).build());

        prevX = lx + lw + 8; prevY = 24; prevW = pickX - prevX - 8; prevH = height - prevY - 36;
        rebuildPicker();
        if (worker == null) {
            worker = new Thread(this::loop, "packstudio-preview"); worker.setDaemon(true); worker.start();
        }
    }

    private Text modeText() {
        return Text.translatable(mode == 0 ? "packstudio.mode.variants" : mode == 1 ? "packstudio.mode.packs" : "packstudio.mode.room");
    }

    private void loadPack() {
        allItems = packs.isEmpty() ? new ArrayList<>() : packs.get(packIdx).itemNames();
        selected.removeIf(s -> !allItems.contains(s));
        scroll = 0;
    }

    private void clearAnimCache() { synchronized (animCache) { animCache.clear(); } }

    private Anim anim(Pack p, String item) {
        String key = p.name + "/" + item;
        synchronized (animCache) {
            Anim a = animCache.get(key);
            if (a == null) {
                try { a = Anim.load(p, item); } catch (Exception e) { return null; }
                animCache.put(key, a);
            }
            return a;
        }
    }

    // ---------- Vorschau / Export (gleiche Render-Funktion) ----------
    private BufferedImage renderFrame(int tick, String title, String variants, int mode, List<String> items, Pack pack) {
        List<Anim> base = new ArrayList<>();
        for (String it : items) { Anim a = anim(pack, it); if (a != null) base.add(a); }
        if (mode == 2) return Room.render(base, tick);
        List<Showcase.Row> rows = new ArrayList<>();
        if (mode == 0) {
            for (String part : variants.split(",")) {
                String[] kv = part.trim().split(":");
                String name = kv.length > 1 ? kv[0].trim() : "";
                String hex = (kv.length > 1 ? kv[1] : kv[0]).trim().replace("#", "");
                int rgb; try { rgb = Integer.parseInt(hex, 16); } catch (Exception e) { continue; }
                if (name.isEmpty()) name = "#" + hex;
                List<Anim> l = new ArrayList<>();
                for (Anim a : base) l.add(a.recolor(rgb, 1f));
                rows.add(new Showcase.Row(name, rgb, l));
            }
        } else {
            for (Pack p : packs) {
                List<Anim> l = new ArrayList<>();
                for (String it : items) { Anim a = anim(p, it); if (a != null) l.add(a); }
                rows.add(new Showcase.Row(p.name, l.isEmpty() ? 0x808080 : l.get(0).avgRgb(), l));
                if (rows.size() >= 8) break;
            }
        }
        return Showcase.render(title, rows, Math.max(1, Math.min(items.size(), 12)), tick);
    }

    private void loop() {
        long t0 = System.currentTimeMillis();
        int lastTick = -1;
        while (running) {
            try {
                int tick = (int) ((System.currentTimeMillis() - t0) / 50);
                if ((dirty || tick / 2 != lastTick) && !packs.isEmpty() && nextPreview == null) {
                    dirty = false; lastTick = tick / 2;
                    List<String> items = new ArrayList<>(selected);
                    if (items.isEmpty()) items = new ArrayList<>(allItems.subList(0, Math.min(12, allItems.size())));
                    BufferedImage img = renderFrame(tick, fTitle.getText(), fVariants.getText(), mode, items, packs.get(packIdx));
                    nextPreview = img;
                }
                Thread.sleep(40);
            } catch (Exception e) { status = Text.translatable("packstudio.error", String.valueOf(e.getMessage())).getString(); }
        }
    }

    private void export(boolean gif) {
        if (packs.isEmpty() || progress >= 0) return;
        final List<String> items = selected.isEmpty() ? new ArrayList<>(allItems.subList(0, Math.min(12, allItems.size()))) : new ArrayList<>(selected);
        final String title = fTitle.getText(), variants = fVariants.getText();
        final int m = mode; final Pack pack = packs.get(packIdx);
        progress = 0;
        Thread t = new Thread(() -> {
            try {
                Path out = PackStudio.exportDir().resolve("showcase_" + Exporter.stamp() + (gif ? ".gif" : ".png"));
                if (gif) Exporter.exportGif(i -> renderFrame(i, title, variants, m, items, pack), 100, out, p -> progress = p, null);
                else Exporter.exportPng(renderFrame(0, title, variants, m, items, pack), out);
                status = Text.translatable("packstudio.done", out.getFileName().toString()).getString();
            } catch (Exception e) {
                status = Text.translatable("packstudio.error", String.valueOf(e.getMessage())).getString();
            } finally { progress = -1; }
        }, "packstudio-export");
        t.setDaemon(true); t.start();
    }

    private void openFolder() {
        try { java.nio.file.Files.createDirectories(PackStudio.exportDir()); Util.getOperatingSystem().open(PackStudio.exportDir().toFile()); } catch (Exception ignored) { }
    }

    // ---------- Item-Waehler (als Textur gerendert) ----------
    private void rebuildPicker() {
        shown = new ArrayList<>();
        for (String s : allItems) if (filter.isEmpty() || s.contains(filter)) shown.add(s);
        pickerCols = Math.max(1, (pickW + PG) / (PT + PG));
        int rowsVis = Math.max(1, (pickH + PG) / (PT + PG));
        int maxScroll = Math.max(0, (shown.size() + pickerCols - 1) / pickerCols - rowsVis);
        scroll = Math.min(scroll, maxScroll);
        if (packs.isEmpty()) return;
        pw = pickerCols * (PT + PG); ph = rowsVis * (PT + PG);
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < pickerCols * rowsVis; i++) {
            int idx = scroll * pickerCols + i;
            if (idx >= shown.size()) break;
            int x = (i % pickerCols) * (PT + PG), y = (i / pickerCols) * (PT + PG);
            String it = shown.get(idx);
            g.setColor(new java.awt.Color(selected.contains(it) ? 0x55FFFFFF : 0x66000000, true));
            g.fillRect(x, y, PT, PT);
            g.setColor(selected.contains(it) ? java.awt.Color.WHITE : new java.awt.Color(0x555555));
            g.drawRect(x, y, PT - 1, PT - 1);
            Anim a = anim(packs.get(packIdx), it);
            if (a != null) {
                BufferedImage f = a.at(0);
                int s = Math.max(f.getWidth(), f.getHeight()), box = PT - 6;
                int px = s <= box ? Math.max(1, box / s) * s : box;
                g.drawImage(f, x + (PT - f.getWidth() * px / s) / 2, y + (PT - f.getHeight() * px / s) / 2, f.getWidth() * px / s, f.getHeight() * px / s, null);
            }
        }
        g.dispose();
        pickerTex = upload(img, PICKER, pickerTex);
    }

    private NativeImageBackedTexture upload(BufferedImage img, Identifier id, NativeImageBackedTexture old) {
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) ni.setColorArgb(x, y, img.getRGB(x, y));
        NativeImageBackedTexture t = new NativeImageBackedTexture(() -> id.toString(), ni);
        if (old != null) client.getTextureManager().destroyTexture(id);
        client.getTextureManager().registerTexture(id, t);
        return t;
    }

    private int texW, texH;

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        BufferedImage n = nextPreview;
        if (n != null) { nextPreview = null; texW = n.getWidth(); texH = n.getHeight(); previewTex = upload(n, PREVIEW, previewTex); }
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        ctx.fill(prevX - 1, prevY - 1, prevX + prevW + 1, prevY + prevH + 1, 0xFF555555);
        ctx.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF000000);
        if (packs.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("packstudio.nopacks"), prevX + prevW / 2, prevY + prevH / 2, 0xFFAAAAAA);
        } else if (previewTex != null && texW > 0) {
            float s = Math.min(prevW / (float) texW, prevH / (float) texH);
            int dw = Math.round(texW * s), dh = Math.round(texH * s);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PREVIEW, prevX + (prevW - dw) / 2, prevY + (prevH - dh) / 2, 0f, 0f, dw, dh, texW, texH, texW, texH);
        }
        if (pickerTex != null && !packs.isEmpty())
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PICKER, pickX, pickY, 0f, 0f, pw, ph, pw, ph, pw, ph);
        String line = progress >= 0 ? Text.translatable("packstudio.rendering", progress).getString() : status;
        ctx.drawTextWithShadow(textRenderer, Text.literal(line), prevX, height - 24, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("packstudio.selected", selected.size()), pickX, height - 24, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (mx >= pickX && mx < pickX + pw && my >= pickY && my < pickY + ph) {
            int c = (int) ((mx - pickX) / (PT + PG)), r = (int) ((my - pickY) / (PT + PG));
            int idx = (scroll + r) * pickerCols + c;
            if (c < pickerCols && idx < shown.size()) {
                String it = shown.get(idx);
                if (!selected.remove(it)) selected.add(it);
                rebuildPicker(); dirty = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= pickX && mx < pickX + pickW) { scroll = Math.max(0, scroll - (int) Math.signum(v)); rebuildPicker(); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public void close() { running = false; client.setScreen(parent); }
}
