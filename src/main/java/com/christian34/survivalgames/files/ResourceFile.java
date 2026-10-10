package com.christian34.survivalgames.files;

import com.christian34.survivalgames.SurvivalGames;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A file of the server owner (config.yml, loot.yml), created and updated from the bundled defaults.
 *
 * @author Christian34
 */
public class ResourceFile extends PluginFile {
    private final String name;
    private final List<String> openSections;

    /**
     * @param openSections sections the server owner fills with keys of his own (the defaults aren't added to them again)
     */
    ResourceFile(String name, String... openSections) {
        super(new File(FileManager.getPluginFolder(), name), false);
        this.name = name;
        this.openSections = List.of(openSections);
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
        ConfigUpdater.update(SurvivalGames.getInstance(), name, getSourceFile(), openSections);
    }

}
