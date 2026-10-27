package com.christian34.survivalgames.network;

import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.VaroGame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * The state of the game on a game server. Game servers send it as their MOTD ("SG;LOBBY;3;24;castle;survival-games",
 * a running Varo outside of its play hours adds the epoch second they begin at: "...;varo;1793210400"), the lobby
 * server reads it with {@link ServerPinger}.
 *
 * @param type  the key of the game type (survival-games, varo)
 * @param opens the time a running Varo can be played again (play hours), null if it can be played now
 * @author Christian34
 */
public record ServerStatus(State state, int players, int maxPlayers, String arena, String type,
                           @Nullable Instant opens) {
    public static final String MOTD_PREFIX = "SG";
    public static final ServerStatus OFFLINE = new ServerStatus(State.OFFLINE, 0, 0, "", "");

    /**
     * a game that can be played right now
     */
    public ServerStatus(State state, int players, int maxPlayers, String arena, String type) {
        this(state, players, maxPlayers, arena, type, null);
    }

    public enum State {
        /**
         * waiting for players
         */
        LOBBY,
        /**
         * starting or running
         */
        INGAME,
        /**
         * the game is over
         */
        ENDING,
        /**
         * the server isn't reachable or has no game
         */
        OFFLINE
    }

    /**
     * @return the status of the game (OFFLINE if there is none)
     */
    public static ServerStatus of(@Nullable Game game) {
        if (game == null || game.isStopped()) return OFFLINE;
        State state = switch (game.getState()) {
            case LOBBY -> State.LOBBY;
            case STARTING, RUNNING -> State.INGAME;
            case ENDED -> State.ENDING;
        };
        int players = state == State.LOBBY ? game.getParticipants().size() : game.getPlayers().size();
        Instant opens = state == State.INGAME && game instanceof VaroGame varo ? varo.getOpening() : null;
        return new ServerStatus(state, players, game.getMaxPlayers(), game.getArena().getName(), game.getType().getKey(),
                opens);
    }

    public String toMotd() {
        String motd = String.join(";", MOTD_PREFIX, state.name(), players + "", maxPlayers + "", arena, type);
        // only when needed: older lobby servers read the rest as the type
        return opens == null ? motd : motd + ";" + opens.getEpochSecond();
    }

    /**
     * @return the status in the MOTD, OFFLINE if it isn't one (e.g. not a game server)
     */
    @NotNull
    public static ServerStatus parse(@Nullable String motd) {
        if (motd == null) return OFFLINE;
        String[] parts = motd.trim().split(";", 7);
        if (parts.length < 6 || !parts[0].equals(MOTD_PREFIX)) return OFFLINE;
        try {
            Instant opens = parts.length == 7 ? Instant.ofEpochSecond(Long.parseLong(parts[6])) : null;
            return new ServerStatus(State.valueOf(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    parts[4], parts[5], opens);
        } catch (IllegalArgumentException ex) {
            return OFFLINE;
        }
    }

    /**
     * @return true if new players can join the game
     */
    public boolean isJoinable() {
        return state == State.LOBBY && players < maxPlayers;
    }

    /**
     * @return true if the game is running (players can watch it, a Varo: its participants can play)
     */
    public boolean isRunning() {
        return state == State.INGAME;
    }

}
