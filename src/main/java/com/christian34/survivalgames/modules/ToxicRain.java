package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Toxic rain: some of the rains in the world of a game (modules.toxic-rain.chance) hurt the players under the open sky.
 * It never kills - it only leaves the players with little health.
 *
 * @author Christian34
 */
public class ToxicRain extends GameModule implements Listener {
    public static final String MODULE = "toxic-rain";
    /**
     * the worlds where it rains toxic right now
     */
    private final Set<World> toxic = new HashSet<>();

    public ToxicRain(SurvivalGames plugin) {
        super(plugin, MODULE);
        Bukkit.getScheduler().runTaskTimer(plugin, this::damageAll, 40L, 40L);
    }

    /**
     * the rain gets toxic or harmless
     */
    public void setToxic(World world, boolean toxic) {
        if (toxic ? !this.toxic.add(world) : !this.toxic.remove(world)) return;
        if (toxic) plugin.getUsageStats().used(UsageStats.Feature.TOXIC_RAIN);
        for (Player player : world.getPlayers()) {
            player.sendMessage(I.prefixed(player, toxic ? LangText.TOXIC_RAIN_STARTED : LangText.TOXIC_RAIN_STOPPED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent e) {
        Game game = plugin.getGameManager().getGame(e.getWorld());
        if (game == null) return;
        boolean toxicRain = e.toWeatherState() && isEnabled(game)
                && game.getState() == GameState.RUNNING
                && ThreadLocalRandom.current().nextInt(100) < config().getInt("chance");
        setToxic(e.getWorld(), toxicRain);
    }

    private void damageAll() {
        toxic.removeIf(world -> plugin.getGameManager().getGame(world) == null);
        double damage = config().getDouble("damage");
        for (World world : toxic) {
            Game game = plugin.getGameManager().getGame(world);
            if (game == null || !world.hasStorm() || game.getState() != GameState.RUNNING) continue;
            for (Player player : world.getPlayers()) {
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
                if (!isUnderOpenSky(player.getLocation()) || player.getHealth() <= damage + 1) continue;
                player.damage(damage);
            }
        }
    }

    private static boolean isUnderOpenSky(Location location) {
        return location.getWorld().getHighestBlockYAt(location) <= location.getBlockY();
    }

}
