package com.christian34.survivalgames.user;

import com.christian34.survivalgames.game.Game;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * An online player: the game he is in (playing or watching) and his state outside of the plugin.
 *
 * @author Christian34
 */
public class GamePlayer {
    private final Player player;
    private final UserStorage storage;
    @Nullable
    private Game game;
    private boolean spectator;

    public GamePlayer(Player player) {
        this.player = player;
        this.storage = new UserStorage(player);
    }

    public Player getPlayer() {
        return player;
    }

    public UserStorage getStorage() {
        return storage;
    }

    /**
     * @return the game the player is in (also as spectator), null if none
     */
    @Nullable
    public Game getGame() {
        return game;
    }

    public void setGame(@Nullable Game game) {
        this.game = game;
    }

    /**
     * @return true if the player only watches his game
     */
    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    /**
     * @return true if the player plays in a game (not only watching)
     */
    public boolean isPlaying() {
        return game != null && !spectator;
    }

}
