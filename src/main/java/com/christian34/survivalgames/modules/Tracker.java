package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The player tracker: a compass that points at the nearest enemy, holding it shows his name and distance. In Survival
 * Games every player alive gets one when the grace period is over, in a Varo when he is on his spawn point.
 * <p>
 * It uses the compass target of the player (restored when he leaves the game), so it works like any compass.
 *
 * @author Christian34
 */
public class Tracker {
    public static final String MODULE = "tracker";
    private final SurvivalGames plugin;

    public Tracker(SurvivalGames plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * the player gets a tracker if the game uses them and he hasn't got one yet
     */
    public void give(Game game, Player player) {
        if (!plugin.isModuleEnabled(MODULE, game) || hasTracker(player.getInventory())) return;
        player.getInventory().addItem(Items.create(Material.COMPASS, i18n(LangText.ITEM_TRACKER),
                I.itemLines(LangText.ITEM_TRACKER_LORE), Items.TRACKER));
        player.sendMessage(I.prefixed(LangText.TRACKER_GIVEN));
    }

    private static boolean hasTracker(PlayerInventory inventory) {
        for (var item : inventory.getContents()) {
            if (Items.is(item, Items.TRACKER)) return true;
        }
        return false;
    }

    /**
     * @return the nearest player alive in the game who isn't in the team of the player, null if there is none
     */
    @Nullable
    public static Player findTarget(Game game, Player player) {
        GameTeam team = game.getTeam(player.getUniqueId());
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (GamePlayer member : game.getPlayers()) {
            Player other = member.getPlayer();
            if (other == player || !other.getWorld().equals(player.getWorld()) || other.isDead()) continue;
            if (team != null && team.isMember(other.getUniqueId())) continue;
            Participant participant = game.getParticipant(other.getUniqueId());
            if (participant == null || !participant.isAlive()) continue;
            double otherDistance = other.getLocation().distanceSquared(player.getLocation());
            if (otherDistance < distance) {
                distance = otherDistance;
                nearest = other;
            }
        }
        return nearest;
    }

    /**
     * every second: the compasses point at the nearest enemy, who holds a tracker sees his name and distance
     */
    private void tick() {
        for (Game game : plugin.getGameManager().getGames()) {
            if (game.getState() != GameState.RUNNING || !plugin.isModuleEnabled(MODULE, game)) continue;
            for (GamePlayer member : game.getPlayers()) {
                Player player = member.getPlayer();
                PlayerInventory inventory = player.getInventory();
                boolean holding = Items.is(inventory.getItemInMainHand(), Items.TRACKER)
                        || Items.is(inventory.getItemInOffHand(), Items.TRACKER);
                if (!holding && !hasTracker(inventory)) continue;
                Player target = findTarget(game, player);
                if (target != null) player.setCompassTarget(target.getLocation());
                if (!holding) continue;
                if (target == null) {
                    player.sendActionBar(i18n(LangText.TRACKER_NO_TARGET));
                } else {
                    player.sendActionBar(i18n(LangText.TRACKER_TARGET, target.getName(),
                            (int) Math.round(target.getLocation().distance(player.getLocation()))));
                }
            }
        }
    }

}
