package com.christian34.survivalgames.files;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Adds keys that exist in the bundled default resource but are missing in the user's file.
 * Existing values and comments are kept (Bukkit's YAML parser preserves comments since 1.18).
 */
public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static void update(Plugin plugin, String resourceName, File file) throws IOException {
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
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || current.contains(key, true)) continue;
            current.set(key, defaults.get(key));
            current.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            current.save(file);
        }
    }

}
