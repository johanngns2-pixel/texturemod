package dev.packstudio.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;
import javax.imageio.ImageIO;

/** Export ohne externe Programme: GIF, PNG, PNG-Sequenz. Frame i = Tick i (50 ms). */
public final class Exporter {
    private Exporter() { }

    public static String stamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")); }

    public static Path exportGif(IntFunction<BufferedImage> frame, int count, Path out, IntConsumer progress, BooleanSupplier cancelled) throws IOException {
        List<BufferedImage> samples = new ArrayList<>();
        int step = Math.max(1, count / 10);
        for (int i = 0; i < count; i += step) samples.add(frame.apply(i));
        var pal = GifWriter.buildPalette(samples);
        samples = null;
        try (GifWriter gw = new GifWriter(out, pal, 50)) {
            for (int i = 0; i < count; i++) {
                if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("abgebrochen");
                gw.add(frame.apply(i));
                if (progress != null) progress.accept((i + 1) * 100 / count);
            }
        }
        return out;
    }

    public static Path exportPng(BufferedImage img, Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        ImageIO.write(img, "png", out.toFile());
        return out;
    }
}
