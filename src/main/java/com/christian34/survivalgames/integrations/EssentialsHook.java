package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.ess3.api.IUser;
import net.ess3.api.events.AfkStatusChangeEvent;
import net.ess3.api.events.FlyStatusChangeEvent;
import net.ess3.api.events.GodStatusChangeEvent;
import net.ess3.api.events.StatusChangeEvent;
import net.ess3.api.events.VanishStatusChangeEvent;
import net.ess3.api.events.teleport.PreTeleportEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EssentialsX (https://essentialsx.net), see {@link EssentialsSupport}
 */
class EssentialsHook implements EssentialsSupport, Listener {
    static final String PLUGIN = "Essentials";
    private final Essentials essentials;
    /**
     * what was turned off when the players joined a game (turned on again when they leave it)
     */
    private final Map<UUID, Set<State>> turnedOff = new HashMap<>();

    private enum State {
        GOD, VANISH
    }

    EssentialsHook() {
        this.essentials = (Essentials) Bukkit.getPluginManager().getPlugin(PLUGIN);
        if (essentials == null) throw new IllegalStateException("Essentials isn't enabled");
    }

    @Override
    public void joinGame(@NotNull Player player, boolean spectator) {
        User user = essentials.getUser(player);
        if (user == null) return;
        if (user.isAfk()) user.setAfk(false, AfkStatusChangeEvent.Cause.UNKNOWN);
        if (spectator) return;
        Set<State> states = turnedOff.computeIfAbsent(player.getUniqueId(), uuid -> EnumSet.noneOf(State.class));
        if (user.isGodModeEnabled()) {
            user.setGodModeEnabled(false);
            states.add(State.GOD);
        }
        if (user.isVanished()) {
            user.setVanished(false);
            states.add(State.VANISH);
        }
    }

    @Override
    public void leaveGame(@NotNull Player player) {
        Set<State> states = turnedOff.remove(player.getUniqueId());
        if (states == null || states.isEmpty() || !player.isOnline()) return;
        User user = essentials.getUser(player);
        if (user == null) return;
        if (states.contains(State.GOD)) user.setGodModeEnabled(true);
        if (states.contains(State.VANISH)) user.setVanished(true);
    }

    @Override
    public boolean isVanished(@NotNull Player player) {
        User user = essentials.getUser(player);
        return user != null && user.isVanished();
    }

    @EventHandler(ignoreCancelled = true)
    public void onGod(GodStatusChangeEvent e) {
        blockWhilePlaying(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFly(FlyStatusChangeEvent e) {
        blockWhilePlaying(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVanish(VanishStatusChangeEvent e) {
        blockWhilePlaying(e);
    }

    /**
     * god mode, flying and vanish can't be turned on while playing
     */
    private void blockWhilePlaying(StatusChangeEvent e) {
        Player player = e.getAffected().getBase();
        if (!e.getValue() || player == null || !EssentialsSupport.isPlaying(player)) return;
        e.setCancelled(true);
        IUser controller = e.getController();
        Player receiver = controller != null && controller.getBase() != null ? controller.getBase() : player;
        receiver.sendMessage(I.prefixed(LangText.ESSENTIALS_BLOCKED_IN_GAME));
    }

    /**
     * /back, /tpa, /home, ... don't lead into the worlds of games
     */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PreTeleportEvent e) {
        Player player = e.getTeleportee().getBase();
        if (player == null || e.getTarget() == null) return;
        if (EssentialsSupport.blocksTeleport(player, e.getTarget().getLocation())) {
            e.setCancelled(true);
            player.sendMessage(I.prefixed(LangText.TELEPORT_INTO_GAME_DENIED));
        }
    }

}
