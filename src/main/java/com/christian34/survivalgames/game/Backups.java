package com.christian34.survivalgames.game;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Zip files of a Varo: its world (world/...) and its saved game (game.yml).
 *
 * @author Christian34
 */
final class Backups {
    private static final String WORLD_ENTRIES = "world/";
    private static final String GAME_ENTRY = "game.yml";

    private Backups() {
    }

    /**
     * @param world the folder of the world
     * @param game  the saved game (games/&lt;arena&gt;.yml)
     */
    static void zip(File world, File game, File target) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temp.toPath()); ZipOutputStream zip = new ZipOutputStream(out)) {
            Path root = world.toPath();
            List<Path> files = List.of();
            if (world.isDirectory()) {
                try (Stream<Path> walk = Files.walk(root)) {
                    files = walk.filter(Files::isRegularFile).toList();
                }
            }
            for (Path file : files) {
                // locked by the server while the world is loaded
                if (file.getFileName().toString().equals("session.lock")) continue;
                add(zip, WORLD_ENTRIES + root.relativize(file).toString().replace('\\', '/'), file);
            }
            if (game.isFile()) add(zip, GAME_ENTRY, game.toPath());
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void add(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    /**
     * unpacks a backup: world/... into the (empty) folder of the world, game.yml to the saved game
     */
    static void unzip(File backup, File world, File game) throws IOException {
        Path root = world.toPath().toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(backup)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                Path target;
                if (entry.getName().equals(GAME_ENTRY)) {
                    target = game.toPath();
                } else if (entry.getName().startsWith(WORLD_ENTRIES)) {
                    target = root.resolve(entry.getName().substring(WORLD_ENTRIES.length())).normalize();
                    // a zip file from somewhere else must not write outside of the world
                    if (!target.startsWith(root)) throw new IOException("Invalid entry " + entry.getName());
                } else {
                    continue;
                }
                if (target.getParent() != null) Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * @return the backups in the folder, the newest one first
     */
    static List<File> list(File folder) {
        File[] backups = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        if (backups == null) return List.of();
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        return List.of(backups);
    }

    /**
     * deletes the oldest backups, the newest ones are kept
     */
    static void prune(File folder, int keep) {
        List<File> backups = list(folder);
        for (int i = keep; i < backups.size(); i++) {
            if (!backups.get(i).delete()) backups.get(i).deleteOnExit();
        }
    }

}
