package com.christian34.survivalgames.game;

import com.christian34.survivalgames.lib.lang.LangText;

/**
 * The phases of a game.
 *
 * @author Christian34
 */
public enum GameState {
    /**
     * the players wait in the lobby and choose their teams
     */
    LOBBY(LangText.STATE_LOBBY),
    /**
     * the players stand on their spawn points and wait for the countdown
     */
    STARTING(LangText.STATE_STARTING),
    /**
     * the game is running: out after the first death
     */
    RUNNING(LangText.STATE_RUNNING),
    /**
     * only one team is left
     */
    ENDED(LangText.STATE_ENDED);

    private final LangText displayName;

    GameState(LangText displayName) {
        this.displayName = displayName;
    }

    public LangText getDisplayName() {
        return displayName;
    }

}
