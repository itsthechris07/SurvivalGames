package com.christian34.survivalgames.stats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The statistics in stats.yml: &lt;uuid&gt;.name, &lt;uuid&gt;.&lt;game type&gt;.&lt;statistic&gt; and
 * &lt;uuid&gt;.achievements.&lt;achievement&gt; (when the player got it) and &lt;uuid&gt;.settings.&lt;key&gt;.
 *
 * @author Christian34
 */
public class FileStorage implements StatsStorage {
    private static final String ACHIEVEMENTS = "achievements";
    private static final String SETTINGS = "settings";
    private final File file;
    private final YamlConfiguration data;

    public FileStorage(File file) {
        this.file = file;
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public void add(UUID uuid, String name, String type, Stat stat, int amount) {
        String path = uuid + "." + type + "." + stat.getKey();
        data.set(uuid + ".name", name);
        data.set(path, data.getInt(path) + amount);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Couldn't save " + file, ex);
        }
    }

    @Override
    public Map<String, PlayerStats> load(UUID uuid) {
        Map<String, PlayerStats> stats = new HashMap<>();
        ConfigurationSection player = data.getConfigurationSection(uuid.toString());
        if (player == null) return stats;
        String name = player.getString("name", uuid.toString());
        for (String type : player.getKeys(false)) {
            ConfigurationSection section = player.getConfigurationSection(type);
            if (section == null || type.equals(ACHIEVEMENTS) || type.equals(SETTINGS)) continue;
            stats.put(type, new PlayerStats(uuid, name, section.getInt(Stat.GAMES.getKey()),
                    section.getInt(Stat.WINS.getKey()), section.getInt(Stat.KILLS.getKey()),
                    section.getInt(Stat.DEATHS.getKey()), section.getInt(Stat.RATING.getKey())));
        }
        return stats;
    }

    @Override
    @Nullable
    public UUID find(String name) {
        for (String key : data.getKeys(false)) {
            if (!name.equalsIgnoreCase(data.getString(key + ".name"))) continue;
            try {
                return UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    @Override
    public List<PlayerStats> top(Stat stat, @Nullable String type, int limit) {
        List<PlayerStats> players = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Map<String, PlayerStats> stats = load(uuid);
            PlayerStats value = type != null ? stats.get(type)
                    : PlayerStats.total(uuid, data.getString(key + ".name", key), stats.values());
            if (value != null && value.isRanked(stat)) players.add(value);
        }
        return players.stream().sorted(Comparator.comparingInt((PlayerStats p) -> p.get(stat)).reversed())
                .limit(limit).toList();
    }

    @Override
    public Map<String, Long> loadAchievements(UUID uuid) {
        Map<String, Long> achievements = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection(uuid + "." + ACHIEVEMENTS);
        if (section == null) return achievements;
        for (String key : section.getKeys(false)) {
            achievements.put(key, section.getLong(key));
        }
        return achievements;
    }

    @Override
    public boolean addAchievement(UUID uuid, String name, String achievement, long time) {
        String path = uuid + "." + ACHIEVEMENTS + "." + achievement;
        if (data.contains(path)) return false;
        data.set(uuid + ".name", name);
        data.set(path, time);
        save();
        return true;
    }

    @Override
    public Map<String, String> loadSettings(UUID uuid) {
        Map<String, String> settings = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection(uuid + "." + SETTINGS);
        if (section == null) return settings;
        for (String key : section.getKeys(false)) {
            settings.put(key, section.getString(key, ""));
        }
        return settings;
    }

    @Override
    public void setSetting(UUID uuid, String name, String key, @Nullable String value) {
        data.set(uuid + ".name", name);
        data.set(uuid + "." + SETTINGS + "." + key, value);
        save();
    }

    @Override
    public void reset(Collection<Stat> stats) {
        for (String key : data.getKeys(false)) {
            ConfigurationSection player = data.getConfigurationSection(key);
            if (player == null) continue;
            for (String type : player.getKeys(false)) {
                ConfigurationSection section = player.getConfigurationSection(type);
                if (section == null || type.equals(ACHIEVEMENTS) || type.equals(SETTINGS)) continue;
                for (Stat stat : stats) {
                    section.set(stat.getKey(), null);
                }
            }
        }
        save();
    }

    @Override
    public void close() {
    }

}
