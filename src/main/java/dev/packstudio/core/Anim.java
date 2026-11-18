package dev.packstudio.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;

/** Item-Textur inkl. Vanilla-.mcmeta-Animation (Zeit in Ticks, 20 Ticks = 1 s). */
public final class Anim {
    public final String item;
    private final List<BufferedImage> frames;
    private final int[] seq;
    private final int[] times;
    private final int total;

    private Anim(String item, List<BufferedImage> frames, int[] seq, int[] times) {
        this.item = item; this.frames = frames; this.seq = seq; this.times = times;
        int t = 0; for (int x : times) t += x;
        this.total = Math.max(1, t);
    }

    public static Anim load(Pack p, String item) throws IOException {
        byte[] png = p.itemPng(item);
        if (png == null) throw new FileNotFoundException(item);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null) throw new IOException("kein PNG: " + item);
        byte[] meta = p.itemMeta(item);
        List<BufferedImage> fr = new ArrayList<>();
        int[] seq; int[] times;
        Map<String, Object> an = null;
        if (meta != null) {
            try { an = Json.obj(Json.obj(Json.parse(new String(meta, StandardCharsets.UTF_8))).get("animation")); }
            catch (RuntimeException ignored) { }
        }
        if (an == null) {
            fr.add(img);
            return new Anim(item, fr, new int[]{0}, new int[]{1});
        }
        int fw = Json.num(an.get("width"), img.getWidth());
        int fh = Json.num(an.get("height"), fw);
        fh = Math.min(fh, img.getHeight());
        fw = Math.min(fw, img.getWidth());
        int count = Math.max(1, img.getHeight() / fh);
        for (int i = 0; i < count; i++) fr.add(img.getSubimage(0, i * fh, fw, fh));
        int def = Math.max(1, Json.num(an.get("frametime"), 1));
        List<Object> fl = Json.arr(an.get("frames"));
        if (fl == null || fl.isEmpty()) {
            seq = new int[count]; times = new int[count];
            for (int i = 0; i < count; i++) { seq[i] = i; times[i] = def; }
        } else {
            seq = new int[fl.size()]; times = new int[fl.size()];
            for (int i = 0; i < fl.size(); i++) {
                Object o = fl.get(i);
                if (o instanceof Number n) { seq[i] = n.intValue(); times[i] = def; }
                else { Map<String, Object> m = Json.obj(o); seq[i] = Json.num(m.get("index"), 0); times[i] = Math.max(1, Json.num(m.get("time"), def)); }
                seq[i] = Math.floorMod(seq[i], count);
            }
        }
        return new Anim(item, fr, seq, times);
    }

    public int totalTicks() { return total; }
    public boolean animated() { return seq.length > 1; }

    public BufferedImage at(int tick) {
        int t = Math.floorMod(tick, total);
        for (int i = 0; i < seq.length; i++) {
            if (t < times[i]) return frames.get(seq[i]);
            t -= times[i];
        }
        return frames.get(seq[0]);
    }

    public Anim recolor(int rgb, float strength) {
        List<BufferedImage> out = new ArrayList<>();
        for (BufferedImage f : frames) out.add(Recolor.tint(f, rgb, strength));
        return new Anim(item, out, seq, times);
    }

    /** Durchschnittsfarbe (nur sichtbare Pixel) des ersten Frames. */
    public int avgRgb() {
        BufferedImage f = frames.get(0);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < f.getHeight(); y++) for (int x = 0; x < f.getWidth(); x++) {
            int a = f.getRGB(x, y);
            if ((a >>> 24) < 40) continue;
            r += (a >> 16) & 255; g += (a >> 8) & 255; b += a & 255; n++;
        }
        if (n == 0) return 0x808080;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }
}
