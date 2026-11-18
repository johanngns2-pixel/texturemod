package dev.packstudio.core;

import java.awt.image.BufferedImage;

public final class Recolor {
    private Recolor() { }

    /** Faerbt nach Helligkeit: dunkel -> schwarz, Mitte -> Zielfarbe, hell -> weiss. Alpha bleibt. */
    public static BufferedImage tint(BufferedImage src, int rgb, float strength) {
        strength = Math.max(0f, Math.min(1f, strength));
        int tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
        BufferedImage o = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            int p = src.getRGB(x, y);
            int a = p >>> 24;
            if (a == 0) continue;
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            float l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
            float nr, ng, nb;
            if (l < 0.5f) { float k = l * 2f; nr = tr * k; ng = tg * k; nb = tb * k; }
            else { float k = (l - 0.5f) * 2f; nr = tr + (255 - tr) * k; ng = tg + (255 - tg) * k; nb = tb + (255 - tb) * k; }
            int rr = Math.round(r + (nr - r) * strength), gg = Math.round(g + (ng - g) * strength), bb = Math.round(b + (nb - b) * strength);
            o.setRGB(x, y, (a << 24) | (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb));
        }
        return o;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
