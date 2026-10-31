package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.files.ConfigFile;
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
public class ToxicRain implements Listener {
    public static final String MODULE = "toxic-rain";
    private final SurvivalGames plugin;
    /**
     * the worlds where it rains toxic right now
     */
    private final Set<World> toxic = new HashSet<>();

    public ToxicRain(SurvivalGames plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    public boolean isToxic(World world) {
        return toxic.contains(world);
    }

    /**
     * the rain gets toxic or harmless
     */
    public void setToxic(World world, boolean toxic) {
        if (toxic ? !this.toxic.add(world) : !this.toxic.remove(world)) return;
        if (toxic) plugin.getTelemetry().used(Telemetry.Feature.TOXIC_RAIN);
        for (Player player : world.getPlayers()) {
            player.sendMessage(I.prefixed(player, toxic ? LangText.TOXIC_RAIN_STARTED : LangText.TOXIC_RAIN_STOPPED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent e) {
        Game game = plugin.getGameManager().getGame(e.getWorld());
        if (game == null) return;
        boolean toxicRain = e.toWeatherState() && plugin.isModuleEnabled(MODULE, game)
                && game.getState() == GameState.RUNNING
                && ThreadLocalRandom.current().nextInt(100) < config().getInt("modules.toxic-rain.chance");
        setToxic(e.getWorld(), toxicRain);
    }

    private void tick() {
        toxic.removeIf(world -> plugin.getGameManager().getGame(world) == null);
        double damage = config().getDouble("modules.toxic-rain.damage");
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
