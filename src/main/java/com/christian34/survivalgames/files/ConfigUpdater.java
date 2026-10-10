package com.christian34.survivalgames.files;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Adds keys that exist in the bundled default resource but are missing in the user's file.
 * Existing values and comments are kept (Bukkit's YAML parser preserves comments since 1.18).
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void update(Plugin plugin, String resourceName, File file) throws IOException {
        update(plugin, resourceName, file, List.of());
    }

    /**
     * @param openSections sections the server owner fills with keys of his own (e.g. names with their values): once
     *                     such a section exists, the keys of the defaults aren't added to it again
     */
    public static void update(Plugin plugin, String resourceName, File file, Collection<String> openSections)
            throws IOException {
        InputStream resource = plugin.getResource(resourceName);
        if (resource == null) {
            throw new IOException("Resource '" + resourceName + "' not found in plugin jar");
        }
        YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        }
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        // the open sections of the file as it is, not the ones this update creates
        List<String> filled = openSections.stream().filter(current::isConfigurationSection).toList();
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (current.contains(key, true) || filled.stream().anyMatch(section -> key.startsWith(section + "."))) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                // a new section: its comment would get lost, an empty one has no keys that create it
                current.createSection(key);
            } else {
                current.set(key, defaults.get(key));
            }
            current.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            current.save(file);
        }
    }

}
