package com.christian34.survivalgames.files;

import com.christian34.survivalgames.SurvivalGames;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * The files in the data folder: config.yml (settings), arenas.yml (setup of the arenas), loot.yml (chest loot of
 * Survival Games), games/&lt;arena&gt;.yml (state of a Varo, which runs for days) and the folder maps/ with the worlds of
 * the arenas.
 *
 * @author Christian34
 */
public class FileManager {
    /**
     * files of a running world that must not be copied: its lock and uuid, the chunks kept loaded and the raids
     */
    private static final Set<String> SKIPPED_FILES = Set.of("uid.dat", "session.lock", "chunk_tickets.dat", "raids.dat");
    private ConfigFile configFile;
    private DataFile arenasFile;
    private ResourceFile lootFile;

    public FileManager() {
        load();
    }

    public static File getPluginFolder() {
        return SurvivalGames.getInstance().getDataFolder();
    }

    /**
     * the worlds of the arenas (copied for every game)
     */
    public static File getMapsFolder() {
        return new File(getPluginFolder(), "maps");
    }

    /**
     * the saved Varo games
     */
    public static File getGamesFolder() {
        return new File(getPluginFolder(), "games");
    }

    /**
     * reloads config.yml and loot.yml (the data files are only written by the plugin, it keeps their content in memory)
     */
    public void reloadConfig() {
        this.configFile = new ConfigFile();
        this.lootFile = new ResourceFile("loot.yml", "containers", "upgrades");
    }

    private void load() {
        reloadConfig();
        this.arenasFile = new DataFile("arenas.yml");
        //noinspection ResultOfMethodCallIgnored
        getMapsFolder().mkdirs();
    }

    public ConfigFile getConfigFile() {
        return configFile;
    }

    public DataFile getArenasFile() {
        return arenasFile;
    }

    public ResourceFile getLootFile() {
        return lootFile;
    }

    /**
     * copies a folder (a world without the files that belong to the running world)
     */
    public static boolean copyFolder(File source, File destination) {
        try {
            if (source.isDirectory()) {
                if (!destination.isDirectory() && !destination.mkdirs()) return false;
                String[] files = source.list();
                if (files == null) return false;
                for (String file : files) {
                    if (SKIPPED_FILES.contains(file)) continue;
                    if (!copyFolder(new File(source, file), new File(destination, file))) return false;
                }
            } else {
                Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void deleteDirectory(File path) {
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        path.delete();
    }

    /**
     * a file the plugin writes itself (no defaults)
     */
    public static class DataFile extends PluginFile {

        public DataFile(File file) {
            super(file);
        }

        DataFile(String name) {
            this(new File(getPluginFolder(), name));
        }

    }

}
