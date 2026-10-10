package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.listeners.PlayerListener;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A villager stands in for a player who has left a Varo for some seconds (varo.offline-npc-seconds) - nobody can
 * escape from a fight by leaving the server. Whoever kills it kills the player: he is out and his items drop. Damage
 * the villager took stays when the player comes back.
 *
 * @author Christian34
 */
public class OfflineNpc implements Listener {
    private static final String KEY = "offline_npc";
    private final SurvivalGames plugin;
    /**
     * the villager of every player who is away
     */
    private final Map<UUID, Villager> npcs = new ConcurrentHashMap<>();

    public OfflineNpc(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    /**
     * a villager stands in for the player who is leaving the game for the seconds
     */
    public void spawn(Player player, int seconds) {
        remove(player.getUniqueId());
        Villager npc;
        try {
            npc = player.getWorld().spawn(player.getLocation(), Villager.class, villager -> {
                villager.setAI(false);
                villager.setSilent(true);
                villager.setPersistent(false);
                villager.customName(I.i18n(LangText.OFFLINE_NPC_NAME, player.getName()));
                villager.setCustomNameVisible(true);
                AttributeInstance maxHealth = villager.getAttribute(Attribute.MAX_HEALTH);
                AttributeInstance playerMax = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null && playerMax != null) maxHealth.setBaseValue(playerMax.getValue());
                villager.setHealth(Math.max(1, Math.min(player.getHealth(), maxHealth == null ? 20 : maxHealth.getValue())));
                villager.getPersistentDataContainer().set(Items.key(KEY), PersistentDataType.STRING,
                        player.getUniqueId().toString());
            });
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't spawn the villager standing in for " + player.getName(), ex);
            return;
        }
        npcs.put(player.getUniqueId(), npc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (npcs.remove(player.getUniqueId(), npc)) npc.remove();
        }, 20L * seconds);
    }

    /**
     * the player is back: his villager goes away
     *
     * @return the health of the villager, null if there was none
     */
    @Nullable
    public Double remove(UUID player) {
        Villager npc = npcs.remove(player);
        if (npc == null || npc.isDead() || !npc.isValid()) return null;
        double health = npc.getHealth();
        npc.remove();
        return health;
    }

    /**
     * the plugin is disabled: all villagers go away
     */
    public void removeAll() {
        npcs.values().forEach(Entity::remove);
        npcs.clear();
    }

    @Nullable
    private static UUID owner(Entity entity) {
        String uuid = entity.getPersistentDataContainer().get(Items.key(KEY), PersistentDataType.STRING);
        try {
            return uuid == null ? null : UUID.fromString(uuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * only players alive of the Varo who are enemies can hurt the villager
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        UUID owner = owner(e.getEntity());
        if (owner == null) return;
        Player attacker = e instanceof EntityDamageByEntityEvent byEntity ? PlayerListener.getAttacker(byEntity.getDamager())
                : null;
        Game game = plugin.getGameManager().getGame(e.getEntity().getWorld());
        if (attacker == null || !(game instanceof VaroGame varo) || game.getState() != GameState.RUNNING
                || varo.isPeaceful()) {
            e.setCancelled(true);
            return;
        }
        GamePlayer user = plugin.getUser(attacker);
        Participant participant = game.getParticipant(attacker.getUniqueId());
        GameTeam team = game.getTeam(owner);
        if (user.getGame() != game || user.isSpectator() || participant == null || !participant.isAlive()
                || team != null && team.isMember(attacker.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        UUID owner = owner(e.getEntity());
        if (owner == null) return;
        npcs.remove(owner);
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (plugin.getGameManager().getGame(e.getEntity().getWorld()) instanceof VaroGame varo) {
            Participant participant = varo.getParticipant(owner);
            if (participant != null) varo.killOffline(participant, e.getEntity().getKiller(), e.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        // no trading
        if (owner(e.getRightClicked()) != null) e.setCancelled(true);
    }

}
