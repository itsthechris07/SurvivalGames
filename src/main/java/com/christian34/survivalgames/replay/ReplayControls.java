package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the viewers do with the replay they watch (commands, menu and the items of their hotbar) - the others are told
 * who did it.
 *
 * @author Christian34
 */
public final class ReplayControls {
    static final String BACK = "replay_back";
    static final String SLOWER = "replay_slower";
    static final String PAUSE = "replay_pause";
    static final String FASTER = "replay_faster";
    static final String FORWARD = "replay_forward";
    static final String EVENTS = "replay_events";
    static final String PLAYERS = "replay_players";
    static final String LEAVE = "replay_leave";

    private ReplayControls() {
    }

    private static void tell(Playback playback, Component message) {
        for (Player viewer : playback.getViewers()) {
            viewer.sendMessage(message);
        }
        refresh(playback);
    }

    /**
     * pauses the replay or goes on with it
     */
    public static void pause(Player player, Playback playback) {
        playback.setPaused(!playback.isPaused());
        tell(playback, I.prefixed(playback.isPaused() ? LangText.REPLAY_PAUSED : LangText.REPLAY_RESUMED,
                player.getName()));
    }

    public static void speed(Player player, Playback playback, double speed) {
        if (playback.getSpeed() == speed) return;
        playback.setSpeed(speed);
        tell(playback, I.prefixed(LangText.REPLAY_SPEED, player.getName(), Playback.formatSpeed(speed)));
    }

    /**
     * @param seconds forward, negative: back
     */
    public static void skip(Player player, Playback playback, int seconds) {
        jump(player, playback, playback.getTick() / 20 + seconds);
    }

    /**
     * @param second the time of the recording to jump to
     */
    public static void jump(Player player, Playback playback, int second) {
        // far beyond the end: no overflow
        playback.seek(Math.clamp(second, 0, playback.getLength() / 20 + 1) * 20);
        tell(playback, I.prefixed(LangText.REPLAY_TIME, player.getName(), Game.formatTime(playback.getTick() / 20)));
    }

    /**
     * the viewer watches through the eyes of a player of the recording
     */
    public static void follow(Player player, Playback playback, String name) {
        if (playback.follow(player, name)) {
            player.sendMessage(I.prefixed(player, LangText.REPLAY_FOLLOWING, name));
        } else {
            player.sendMessage(I.prefixed(player, LangText.REPLAY_PLAYER_NOT_FOUND, name));
        }
    }

    /**
     * everybody watches the death from a few seconds before - the viewer through the eyes of the player
     */
    public static void watch(Player player, Playback playback, Playback.Event event) {
        playback.watch(player, event);
        tell(playback, I.prefixed(LangText.REPLAY_EVENT_WATCHED, player.getName(), event.actor().name(),
                Game.formatTime(event.tick() / 20)));
        player.sendMessage(I.prefixed(player, LangText.REPLAY_FOLLOWING, event.actor().name()));
    }

    // ---------------------------------------------------------------- the viewers

    /**
     * the viewer flies around the arena (adventure mode, so he has the items of the controls) - only while he
     * watches through the eyes of somebody he is a spectator
     */
    static void fly(Player viewer, Playback playback) {
        if (viewer.getGameMode() == GameMode.SPECTATOR) viewer.setSpectatorTarget(null);
        viewer.setGameMode(GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        giveItems(viewer, playback);
    }

    /**
     * the controls in the hotbar: back, slower, pause, faster, forward, deaths, players and leave
     */
    static void giveItems(Player viewer, Playback playback) {
        PlayerInventory inventory = viewer.getInventory();
        inventory.setItem(0, item(Material.ARROW, I.i18n(LangText.GUI_REPLAY_BACK, ReplayMenus.SKIP_SECONDS), BACK));
        inventory.setItem(1, item(Material.SOUL_SAND, I.i18n(LangText.GUI_REPLAY_SLOWER,
                Playback.formatSpeed(playback.getNextSpeed(false))), SLOWER));
        inventory.setItem(2, item(playback.isPaused() ? Material.LIME_DYE : Material.GRAY_DYE,
                I.i18n(playback.isPaused() ? LangText.GUI_REPLAY_PLAY : LangText.GUI_REPLAY_PAUSE), PAUSE));
        inventory.setItem(3, item(Material.SUGAR, I.i18n(LangText.GUI_REPLAY_FASTER,
                Playback.formatSpeed(playback.getNextSpeed(true))), FASTER));
        inventory.setItem(4, item(Material.SPECTRAL_ARROW, I.i18n(LangText.GUI_REPLAY_FORWARD, ReplayMenus.SKIP_SECONDS),
                FORWARD));
        inventory.setItem(6, item(Material.SKELETON_SKULL, I.i18n(LangText.GUI_REPLAY_EVENTS_OPEN), EVENTS));
        inventory.setItem(7, item(Material.PLAYER_HEAD, I.i18n(LangText.GUI_REPLAY_PLAYERS_OPEN), PLAYERS));
        inventory.setItem(8, item(Material.BARRIER, I.i18n(LangText.GUI_REPLAY_LEAVE), LEAVE));
    }

    private static ItemStack item(Material material, Component name, String marker) {
        return Items.create(material, name, I.itemLines(LangText.ITEM_REPLAY_LORE), marker);
    }

    /**
     * the items show the state of the replay (paused, the next speeds)
     */
    static void refresh(Playback playback) {
        for (Player viewer : playback.getViewers()) {
            if (viewer.getGameMode() != GameMode.SPECTATOR) giveItems(viewer, playback);
        }
    }

    /**
     * a viewer has used an item of his hotbar
     *
     * @return false if it isn't one of the controls
     */
    static boolean use(Player viewer, Playback playback, @Nullable ItemStack item) {
        String marker = marker(item);
        if (marker == null) return false;
        switch (marker) {
            case BACK -> skip(viewer, playback, -ReplayMenus.SKIP_SECONDS);
            case SLOWER -> speed(viewer, playback, playback.getNextSpeed(false));
            case PAUSE -> pause(viewer, playback);
            case FASTER -> speed(viewer, playback, playback.getNextSpeed(true));
            case FORWARD -> skip(viewer, playback, ReplayMenus.SKIP_SECONDS);
            case EVENTS -> ReplayMenus.openEvents(viewer, playback);
            case PLAYERS -> ReplayMenus.openPlayers(viewer, playback);
            case LEAVE -> {
                if (SurvivalGames.getInstance().getReplays().leave(viewer)) {
                    viewer.sendMessage(I.prefixed(viewer, LangText.REPLAY_LEFT));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static String marker(@Nullable ItemStack item) {
        for (String marker : List.of(BACK, SLOWER, PAUSE, FASTER, FORWARD, EVENTS, PLAYERS, LEAVE)) {
            if (Items.is(item, marker)) return marker;
        }
        return null;
    }

}
