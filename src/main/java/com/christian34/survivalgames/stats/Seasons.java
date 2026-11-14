package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Rewards;
import com.christian34.survivalgames.integrations.Discord;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Seasons of the statistics (stats.seasons, /sg season): an admin ends the season, the best players of it are kept in
 * seasons.yml, the best ones of stats.seasons.ranking get the rewards and the statistics of stats.seasons.reset start
 * at 0 again.
 *
 * @author Christian34
 */
public class Seasons {
    private final SurvivalGames plugin;
    private final File file;
    private final YamlConfiguration data;
    private boolean ending;

    /**
     * a player in the list of the best players of a past season
     */
    public record Entry(String name, String uuid, int value) {
    }

    public Seasons(SurvivalGames plugin) {
        this.plugin = plugin;
        this.file = new File(FileManager.getPluginFolder(), "seasons.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    /**
     * @return the number of the current season (the first one is 1)
     */
    public int getNumber() {
        return Math.max(1, data.getInt("current", 1));
    }

    /**
     * @return when the current season began (milliseconds since 1970), 0 if it isn't known (the first one)
     */
    public long getStarted() {
        return data.getLong("started");
    }

    /**
     * @return the statistic the players of a season are ranked by (stats.seasons.ranking, the rating by default)
     */
    public Stat getRanking() {
        Stat stat = Stat.parse(config().getString("stats.seasons.ranking"));
        return stat == null ? Stat.RATING : stat;
    }

    /**
     * @return the numbers of the past seasons
     */
    public Set<Integer> getPast() {
        Set<Integer> past = new TreeSet<>();
        var archive = data.getConfigurationSection("archive");
        if (archive == null) return past;
        for (String key : archive.getKeys(false)) {
            try {
                past.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return past;
    }

    /**
     * @return the best players of the past season in the statistic, null if there is no such season
     */
    @Nullable
    public List<Entry> getTop(int season, Stat stat) {
        String path = "archive." + season;
        if (!data.contains(path)) return null;
        List<Entry> top = new ArrayList<>();
        for (Map<?, ?> entry : data.getMapList(path + ".top." + stat.getKey())) {
            Object value = entry.get("value");
            top.add(new Entry(String.valueOf(entry.get("name")), String.valueOf(entry.get("uuid")),
                    value instanceof Number number ? number.intValue() : 0));
        }
        return top;
    }

    /**
     * @return when the past season was over, 0 if there is no such season
     */
    public long getEnded(int season) {
        return data.getLong("archive." + season + ".ended");
    }

    /**
     * ends the current season: the best players are kept, get their rewards, the statistics are reset and the next
     * season begins
     */
    public void end(CommandSender sender) {
        StatsManager stats = plugin.getStats();
        if (!stats.isEnabled()) {
            sender.sendMessage(I.prefixed(sender, LangText.STATS_DISABLED));
            return;
        }
        if (ending) return;
        this.ending = true;
        stats.then(stats.topOfAll(), top -> {
            int number = getNumber();
            String path = "archive." + number;
            data.set(path + ".started", getStarted());
            data.set(path + ".ended", System.currentTimeMillis());
            for (Map.Entry<Stat, List<PlayerStats>> entry : top.entrySet()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (PlayerStats player : entry.getValue()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", player.name());
                    map.put("uuid", player.uuid().toString());
                    map.put("value", player.get(entry.getKey()));
                    list.add(map);
                }
                data.set(path + ".top." + entry.getKey().getKey(), list);
            }
            data.set("current", number + 1);
            data.set("started", System.currentTimeMillis());
            save();
            Stat ranking = getRanking();
            List<PlayerStats> best = top.getOrDefault(ranking, List.of());
            for (int place = 1; place <= best.size(); place++) {
                PlayerStats player = best.get(place - 1);
                Rewards.dispatch(plugin, config().getData().getStringList("stats.seasons.rewards." + place),
                        Map.of("{player}", player.name(), "{uuid}", player.uuid().toString(),
                                "{place}", String.valueOf(place), "{season}", String.valueOf(number),
                                "{value}", String.valueOf(player.get(ranking))));
            }
            List<Stat> reset = new ArrayList<>();
            for (String key : config().getData().getStringList("stats.seasons.reset")) {
                Stat stat = Stat.parse(key);
                if (stat != null) reset.add(stat);
            }
            stats.then(stats.reset(reset), ignored -> {
                this.ending = false;
                String winner = best.isEmpty() ? "-" : best.getFirst().name();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(I.prefixed(player, LangText.SEASON_ENDED, number, winner, number + 1));
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(I.prefixed(sender, LangText.SEASON_ENDED, number, winner, number + 1));
                }
                plugin.getDiscord().post(Discord.Event.SEASON, null,
                        I.plain(LangText.SEASON_ENDED, number, winner, number + 1));
            }, () -> {
                this.ending = false;
                sender.sendMessage(I.prefixed(sender, LangText.STATS_ERROR));
            });
        }, () -> {
            this.ending = false;
            sender.sendMessage(I.prefixed(sender, LangText.STATS_ERROR));
        });
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save " + file, ex);
        }
    }

}
