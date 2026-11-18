package dev.packstudio.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

/** Ein Resourcepack (Ordner oder .zip). */
public final class Pack {
    private static final String ITEMS = "assets/minecraft/textures/item/";
    public final String name;
    public final Path path;

    public Pack(String name, Path path) { this.name = name; this.path = path; }

    public static List<Pack> scan(Path dir) {
        List<Pack> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.sorted().forEach(p -> {
                String n = p.getFileName().toString();
                if (Files.isDirectory(p)) out.add(new Pack(n, p));
                else if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) out.add(new Pack(n.substring(0, n.length() - 4), p));
            });
        } catch (IOException ignored) { }
        return out;
    }

    public boolean isZip() { return Files.isRegularFile(path); }

    public List<String> itemNames() {
        List<String> r = new ArrayList<>();
        try {
            if (isZip()) {
                try (ZipFile z = new ZipFile(path.toFile())) {
                    for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                        String n = e.nextElement().getName().replace('\\', '/');
                        if (n.startsWith(ITEMS) && n.endsWith(".png")) {
                            String rest = n.substring(ITEMS.length(), n.length() - 4);
                            if (rest.indexOf('/') < 0) r.add(rest);
                        }
                    }
                }
            } else {
                Path d = path.resolve(ITEMS);
                if (Files.isDirectory(d)) try (Stream<Path> s = Files.list(d)) {
                    s.forEach(p -> {
                        String n = p.getFileName().toString();
                        if (n.endsWith(".png")) r.add(n.substring(0, n.length() - 4));
                    });
                }
            }
        } catch (IOException ignored) { }
        Collections.sort(r);
        return r;
    }

    public byte[] read(String rel) throws IOException {
        if (isZip()) {
            try (ZipFile z = new ZipFile(path.toFile())) {
                ZipEntry e = z.getEntry(rel);
                if (e == null) return null;
                try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); }
            }
        }
        Path f = path.resolve(rel);
        return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
    }

    public byte[] itemPng(String item) throws IOException { return read(ITEMS + item + ".png"); }
    public byte[] itemMeta(String item) throws IOException { return read(ITEMS + item + ".png.mcmeta"); }
    public byte[] packMeta() throws IOException { return read("pack.mcmeta"); }
}
