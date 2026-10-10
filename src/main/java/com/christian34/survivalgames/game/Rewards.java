package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.integrations.EconomySupport;
import com.christian34.survivalgames.lib.Items;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewards for kills, wins and games: money (rewards.*), commands of the console (rewards.commands.*) and fireworks for
 * the winners (&lt;game type&gt;.fireworks).
 *
 * @author Christian34
 */
public final class Rewards {
    /**
     * "[10s] say hi": the command runs 10 seconds later
     */
    private static final Pattern DELAY = Pattern.compile("^\\[(\\d+)s]\\s*(.+)$");
    /**
     * seconds the fireworks go up
     */
    private static final int FIREWORK_SECONDS = 5;
    private static final String FIREWORK = "reward_firework";

    private Rewards() {
    }

    static void give(SurvivalGames plugin, Game game, UUID player, String name, EconomySupport.Reward reward) {
        plugin.getEconomySupport().reward(player, reward);
        List<String> commands = plugin.getFileManager().getConfigFile().getData()
                .getStringList("rewards.commands." + reward.getKey());
        for (String command : commands) {
            String resolved = command.replace("{player}", name).replace("{uuid}", player.toString())
                    .replace("{arena}", game.getArena().getName()).replace("{type}", game.getType().getKey());
            Matcher delay = DELAY.matcher(resolved);
            if (delay.matches()) {
                String delayed = delay.group(2);
                Bukkit.getScheduler().runTaskLater(plugin, () -> dispatch(delayed), 20L * Integer.parseInt(delay.group(1)));
            } else {
                dispatch(resolved);
            }
        }
    }

    private static void dispatch(String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't run the reward command '" + line + "'", ex);
        }
    }

    /**
     * fireworks in the team color go up around the winners for some seconds
     */
    static void fireworks(SurvivalGames plugin, Game game, Collection<Player> winners, TextColor teamColor) {
        if (winners.isEmpty() || !game.settings().getBoolean("fireworks", true)) return;
        Color color = Color.fromRGB(teamColor.value());
        UUID world = game.getWorld().getUID();
        BukkitTask[] task = new BukkitTask[1];
        int[] seconds = {0};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // the world is unloaded with the next round
            if (++seconds[0] > FIREWORK_SECONDS || Bukkit.getWorld(world) == null) {
                task[0].cancel();
                return;
            }
            for (Player winner : winners) {
                if (!winner.isOnline() || !winner.getWorld().getUID().equals(world)) continue;
                ThreadLocalRandom random = ThreadLocalRandom.current();
                winner.getWorld().spawn(winner.getLocation().add(random.nextDouble(-3, 3), 1, random.nextDouble(-3, 3)),
                        Firework.class, firework -> {
                            FireworkMeta meta = firework.getFireworkMeta();
                            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.values()[random.nextInt(
                                            FireworkEffect.Type.values().length)]).withColor(color, Color.WHITE)
                                    .withFade(Color.YELLOW).flicker(random.nextBoolean()).trail(true).build());
                            meta.setPower(1);
                            firework.setFireworkMeta(meta);
                            firework.getPersistentDataContainer().set(Items.key(FIREWORK), PersistentDataType.BOOLEAN, true);
                        });
            }
        }, 0L, 20L);
    }

    /**
     * @return true if it is a firework for the winners (it doesn't hurt anybody)
     */
    public static boolean isRewardFirework(Entity entity) {
        return entity instanceof Firework && entity.getPersistentDataContainer().has(Items.key(FIREWORK));
    }

}
