package com.christian34.survivalgames.network;

import com.christian34.survivalgames.game.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The state of the game on a game server. Game servers send it as their MOTD ("SG;LOBBY;3;24;castle;survival-games"),
 * the lobby server reads it with {@link ServerPinger}.
 *
 * @param type the key of the game type (survival-games, varo)
 * @author Christian34
 */
public record ServerStatus(State state, int players, int maxPlayers, String arena, String type) {
    public static final String MOTD_PREFIX = "SG";
    public static final ServerStatus OFFLINE = new ServerStatus(State.OFFLINE, 0, 0, "", "");

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
        return new ServerStatus(state, players, game.getMaxPlayers(), game.getArena().getName(), game.getType().getKey());
    }

    public String toMotd() {
        return String.join(";", MOTD_PREFIX, state.name(), players + "", maxPlayers + "", arena, type);
    }

    /**
     * @return the status in the MOTD, OFFLINE if it isn't one (e.g. not a game server)
     */
    @NotNull
    public static ServerStatus parse(@Nullable String motd) {
        if (motd == null) return OFFLINE;
        String[] parts = motd.trim().split(";", 6);
        if (parts.length != 6 || !parts[0].equals(MOTD_PREFIX)) return OFFLINE;
        try {
            return new ServerStatus(State.valueOf(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    parts[4], parts[5]);
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
