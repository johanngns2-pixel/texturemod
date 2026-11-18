package dev.packstudio.core;

import java.awt.image.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;

/** Animierter GIF-Writer in reinem Java (globale Palette, Endlosschleife). Kein ffmpeg noetig. */
public final class GifWriter implements AutoCloseable {
    private final ImageWriter w;
    private final ImageOutputStream out;
    private final IndexColorModel icm;
    private final int delayCs;
    private final Map<Integer, Byte> cache = new HashMap<>();
    private boolean first = true;

    public GifWriter(Path file, IndexColorModel icm, int delayMs) throws IOException {
        this.icm = icm;
        this.delayCs = Math.max(2, Math.round(delayMs / 10f));
        w = ImageIO.getImageWritersByFormatName("gif").next();
        Files.createDirectories(file.toAbsolutePath().getParent());
        out = ImageIO.createImageOutputStream(file.toFile());
        w.setOutput(out);
        w.prepareWriteSequence(null);
    }

    /** Palette aus den haeufigsten Farben der Beispiel-Frames (max 255). */
    public static IndexColorModel buildPalette(List<BufferedImage> samples) {
        Map<Integer, Integer> hist = new HashMap<>();
        for (BufferedImage im : samples) {
            int[] px = im.getRGB(0, 0, im.getWidth(), im.getHeight(), null, 0, im.getWidth());
            for (int p : px) hist.merge(p & 0xFFFFFF, 1, Integer::sum);
        }
        List<Map.Entry<Integer, Integer>> es = new ArrayList<>(hist.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue());
        int n = Math.min(255, es.size());
        byte[] r = new byte[n + 1], g = new byte[n + 1], b = new byte[n + 1];
        for (int i = 0; i < n; i++) {
            int c = es.get(i).getKey();
            r[i] = (byte) (c >> 16); g[i] = (byte) (c >> 8); b[i] = (byte) c;
        }
        return new IndexColorModel(8, n + 1, r, g, b);
    }

    private byte nearest(int rgb) {
        Byte c = cache.get(rgb);
        if (c != null) return c;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, best = 0, bd = Integer.MAX_VALUE;
        for (int i = 0; i < icm.getMapSize(); i++) {
            int dr = icm.getRed(i) - r, dg = icm.getGreen(i) - g, db = icm.getBlue(i) - b;
            int d = dr * dr + dg * dg + db * db;
            if (d < bd) { bd = d; best = i; if (d == 0) break; }
        }
        byte res = (byte) best;
        cache.put(rgb, res);
        return res;
    }

    public void add(BufferedImage rgb) throws IOException {
        int wd = rgb.getWidth(), ht = rgb.getHeight();
        BufferedImage idx = new BufferedImage(wd, ht, BufferedImage.TYPE_BYTE_INDEXED, icm);
        byte[] data = ((DataBufferByte) idx.getRaster().getDataBuffer()).getData();
        int[] px = rgb.getRGB(0, 0, wd, ht, null, 0, wd);
        for (int i = 0; i < px.length; i++) data[i] = nearest(px[i] & 0xFFFFFF);
        ImageWriteParam p = w.getDefaultWriteParam();
        IIOMetadata md = w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(idx), p);
        String f = "javax_imageio_gif_image_1.0";
        IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(f);
        IIOMetadataNode gce = child(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(delayCs));
        gce.setAttribute("transparentColorIndex", "0");
        if (first) {
            IIOMetadataNode apps = child(root, "ApplicationExtensions");
            IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
            app.setAttribute("applicationID", "NETSCAPE");
            app.setAttribute("authenticationCode", "2.0");
            app.setUserObject(new byte[]{1, 0, 0});
            apps.appendChild(app);
            first = false;
        }
        md.setFromTree(f, root);
        w.writeToSequence(new IIOImage(idx, null, md), p);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
        IIOMetadataNode n = new IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }

    @Override public void close() throws IOException {
        try { w.endWriteSequence(); } finally { out.close(); w.dispose(); }
    }
}
