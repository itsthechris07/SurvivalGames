package com.christian34.survivalgames.lib.lang;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigUpdater;
import com.christian34.survivalgames.files.FileManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;


/**
 * The messages of the language set in config.yml: messages.yml (en) or messages_&lt;language&gt;.yml. Bundled languages
 * are created and updated from the jar, other languages can be added as a file; missing keys fall back to English.
 */
public class TextContainer {
    private static final String DEFAULT_FILE = "messages.yml";
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}([_-][a-z0-9]+)?");
    private final YamlConfiguration data;

    public TextContainer() {
        SurvivalGames plugin = SurvivalGames.getInstance();
        String fileName = fileName(plugin.getFileManager().getConfigFile().getString("language"));
        File file = new File(FileManager.getPluginFolder(), fileName);
        if (plugin.getResource(fileName) != null) {
            try {
                ConfigUpdater.update(plugin, fileName, file);
            } catch (IOException ex) {
                Debug.warn("Couldn't update file '" + fileName + "'", ex);
            }
        } else if (!file.exists()) {
            Debug.warn("There is no " + fileName + " for the language in config.yml - using English");
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration english = bundledEnglish(plugin);
        if (english != null) data.setDefaults(english);
    }

    /**
     * @return messages.yml for English (or nothing set), otherwise messages_&lt;language&gt;.yml
     */
    static String fileName(@Nullable String language) {
        if (language == null) return DEFAULT_FILE;
        language = language.trim().toLowerCase(Locale.ROOT);
        if (language.isEmpty() || language.equals("en")) return DEFAULT_FILE;
        if (!LANGUAGE.matcher(language).matches()) {
            Debug.warn("Invalid language '" + language + "' in config.yml - using English");
            return DEFAULT_FILE;
        }
        return "messages_" + language + ".yml";
    }

    @Nullable
    private static YamlConfiguration bundledEnglish(SurvivalGames plugin) {
        InputStream resource = plugin.getResource(DEFAULT_FILE);
        if (resource == null) return null;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return null;
        }
    }

    public String getString(String key) {
        return data.getString(key);
    }

}
