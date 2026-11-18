package dev.packstudio.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Virtueller (flacher) Raum: Sternenhimmel-Void mit 5x4 Item-Frame-Wand. Es wird nichts in der Welt gebaut. */
public final class Room {
    public static final int W = 1280, H = 720, COLS = 5, ROWS = 4;
    private Room() { }

    public static BufferedImage render(List<Anim> items, int tick) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x090B16)); g.fillRect(0, 0, W, H);
        Random r = new Random(7);
        g.setColor(new Color(0x5A5C66));
        for (int i = 0; i < 90; i++) g.fillRect(r.nextInt(W), r.nextInt(H), 3 + r.nextInt(3), 2);
        int cell = 96, gap = 24;
        int x0 = (W - (COLS * cell + (COLS - 1) * gap)) / 2, y0 = (H - (ROWS * cell + (ROWS - 1) * gap)) / 2;
        for (int i = 0; i < COLS * ROWS; i++) {
            int x = x0 + (i % COLS) * (cell + gap), y = y0 + (i / COLS) * (cell + gap);
            g.setColor(new Color(0x9C5A24)); g.fillRect(x, y, cell, cell);
            g.setColor(new Color(0xD08A3C)); g.drawRect(x, y, cell - 1, cell - 1);
            g.setColor(new Color(0x7A3F14)); g.fillRect(x + 10, y + 10, cell - 20, cell - 20);
            if (i < items.size()) {
                BufferedImage f = items.get(i).at(tick);
                int s = Math.max(f.getWidth(), f.getHeight());
                int box = cell - 24;
                int px = s <= box ? (box / s) * s : box;
                int dw = f.getWidth() * px / s, dh = f.getHeight() * px / s;
                g.drawImage(f, x + (cell - dw) / 2, y + (cell - dh) / 2, dw, dh, null);
            }
        }
        g.dispose();
        return img;
    }
}
