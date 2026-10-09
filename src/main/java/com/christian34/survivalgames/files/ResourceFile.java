package com.christian34.survivalgames.files;

import com.christian34.survivalgames.SurvivalGames;

import java.io.File;
import java.io.IOException;

/**
 * A file of the server owner (config.yml, loot.yml), created and updated from the bundled defaults.
 *
 * @author Christian34
 */
public class ResourceFile extends PluginFile {
    private final String name;

    ResourceFile(String name) {
        super(new File(FileManager.getPluginFolder(), name), false);
        this.name = name;
        load();
    }

    @Override
    protected void createFile() throws IOException {
        try {
            SurvivalGames.getInstance().saveResource(name, true);
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    @Override
    protected void update() throws IOException {
        ConfigUpdater.update(SurvivalGames.getInstance(), name, getSourceFile());
    }

}
