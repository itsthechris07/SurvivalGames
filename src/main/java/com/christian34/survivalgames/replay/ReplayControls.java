package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * What the viewers do with the replay they watch (commands and menu) - the others are told who did it.
 *
 * @author Christian34
 */
public final class ReplayControls {

    private ReplayControls() {
    }

    private static void tell(Playback playback, Component message) {
        for (Player viewer : playback.getViewers()) {
            viewer.sendMessage(message);
        }
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
            player.sendMessage(I.prefixed(LangText.REPLAY_FOLLOWING, name));
        } else {
            player.sendMessage(I.prefixed(LangText.REPLAY_PLAYER_NOT_FOUND, name));
        }
    }

}
