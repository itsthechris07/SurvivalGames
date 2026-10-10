package com.christian34.survivalgames.game;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zip files of a Varo: its world (world/...) and its saved game (game.yml).
 *
 * @author Christian34
 */
final class Backups {

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
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                // locked by the server while the world is loaded
                if (file.getFileName().toString().equals("session.lock")) continue;
                add(zip, "world/" + root.relativize(file).toString().replace('\\', '/'), file);
            }
            if (game.isFile()) add(zip, "game.yml", game.toPath());
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void add(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    /**
     * deletes the oldest backups, the newest ones are kept
     */
    static void prune(File folder, int keep) {
        File[] backups = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        if (backups == null || backups.length <= keep) return;
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = keep; i < backups.length; i++) {
            if (!backups[i].delete()) backups[i].deleteOnExit();
        }
    }

}
