package dev.packstudio.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Rendert die "Farbvarianten-Uebersicht" (dunkler Hintergrund, Label links, Kachelraster rechts). */
public final class Showcase {
    public static final int TILE = 96, STEP = 108, LEFT = 320, TOP = 76;
    public static final Color BG = new Color(0x0A0A0F), TILE_BG = new Color(0x0E0E14), TILE_BORDER = new Color(0x3A3A44);

    public record Row(String label, int rgb, List<Anim> anims) { }

    private Showcase() { }

    public static int width(int cols) { return LEFT + cols * STEP - (STEP - TILE) + 32; }
    public static int height(int rows) { return TOP + rows * STEP - (STEP - TILE) + 32; }

    public static BufferedImage render(String title, List<Row> rows, int cols, int tick) {
        cols = Math.max(1, cols);
        int w = width(cols), h = height(Math.max(1, rows.size()));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(BG); g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        g.drawString(title == null ? "" : title.toUpperCase(Locale.ROOT), 20, 36);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int y = TOP + r * STEP;
            g.setColor(new Color(row.rgb()));
            g.fillRect(20, y + 10, 32, 32);
            g.setColor(TILE_BORDER);
            g.drawRect(20, y + 10, 31, 31);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
            g.drawString(shorten(row.label().toUpperCase(Locale.ROOT), 14), 63, y + 24);
            g.setColor(new Color(0x9A9AA6));
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            g.drawString(String.format("#%06X", row.rgb() & 0xFFFFFF), 63, y + 42);
            for (int c = 0; c < cols && c < row.anims().size(); c++) drawTile(g, LEFT + c * STEP, y, row.anims().get(c), tick);
            for (int c = row.anims().size(); c < cols; c++) drawTile(g, LEFT + c * STEP, y, null, tick);
        }
        g.dispose();
        return img;
    }

    public static void drawTile(Graphics2D g, int x, int y, Anim a, int tick) {
        g.setColor(TILE_BG); g.fillRect(x, y, TILE, TILE);
        g.setColor(TILE_BORDER); g.drawRect(x, y, TILE - 1, TILE - 1);
        if (a == null) return;
        BufferedImage f = a.at(tick);
        int box = TILE - 16;
        int s = Math.max(f.getWidth(), f.getHeight());
        int px = s <= box ? (box / s) * s : box; // ganzzahlig skalieren
        int dw = f.getWidth() * px / s, dh = f.getHeight() * px / s;
        g.drawImage(f, x + (TILE - dw) / 2, y + (TILE - dh) / 2, dw, dh, null);
    }

    public static String shorten(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }
}
