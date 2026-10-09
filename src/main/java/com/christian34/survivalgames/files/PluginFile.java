package com.christian34.survivalgames.files;

import com.christian34.survivalgames.Debug;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A YAML file in the data folder of the plugin.
 *
 * @author Christian34
 */
public abstract class PluginFile {
    private final File sourceFile;
    private YamlConfiguration data;

    public PluginFile(File sourceFile) {
        this(sourceFile, true);
    }

    /**
     * @param load false: the subclass calls {@link #load()} itself (once its fields are set)
     */
    protected PluginFile(File sourceFile, boolean load) {
        this.sourceFile = sourceFile;
        if (load) load();
    }

    public YamlConfiguration getData() {
        return data;
    }

    protected final void load() {
        if (!sourceFile.exists()) {
            try {
                createFile();
            } catch (IOException ex) {
                Debug.warn("Couldn't create file '" + sourceFile.getName() + "'", ex);
            }
        }
        try {
            update();
        } catch (IOException ex) {
            Debug.warn("Couldn't update file '" + sourceFile.getName() + "'", ex);
        }
        this.data = YamlConfiguration.loadConfiguration(sourceFile);
    }

    /**
     * sets the value and saves the file
     */
    public void set(String path, @Nullable Object value) {
        data.set(path, value);
        save();
    }

    public void save() {
        try {
            data.save(sourceFile);
        } catch (IOException ex) {
            Debug.warn("Couldn't save file '" + sourceFile.getName() + "'", ex);
        }
    }

    public File getSourceFile() {
        return sourceFile;
    }

    @Nullable
    public String getString(@NotNull String key) {
        return data.getString(key);
    }

    public int getInt(@NotNull String key) {
        return data.getInt(key);
    }

    public boolean getBoolean(@NotNull String key) {
        return data.getBoolean(key);
    }

    public double getDouble(@NotNull String key) {
        return data.getDouble(key);
    }

    @Nullable
    public ConfigurationSection getSection(@NotNull String key) {
        return data.getConfigurationSection(key);
    }

    @NotNull
    public List<String> getList(@NotNull String key) {
        return data.getStringList(key);
    }

    /**
     * creates the file if it doesn't exist (a data file starts empty)
     */
    protected void createFile() throws IOException {
        File parent = sourceFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Couldn't create the folder " + parent);
        }
        if (!sourceFile.createNewFile()) {
            throw new IOException("Couldn't create " + sourceFile);
        }
    }

    /**
     * adds missing keys of the bundled defaults (data files have none)
     */
    protected void update() throws IOException {
    }

}
