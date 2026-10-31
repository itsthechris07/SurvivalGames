package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Kits;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A duel: two players (or two teams) on a small map, everybody gets the same kit, the winner gains rating. The
 * players come from the queue (/sg duel) or a challenge (/sg duel &lt;player&gt;) - a challenge reserves the arena for
 * the two of them. The rest (countdowns, end, next round) works like a round of Survival Games.
 *
 * @author Christian34
 */
public class DuelsGame extends SurvivalGame {
    /**
     * the players of a challenge (empty: everybody can join)
     */
    private Set<UUID> reserved = Set.of();
    /**
     * the kit of a challenge, null: duels.kit
     */
    @Nullable
    private String kit;

    public DuelsGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena);
    }

    @Override
    public GameType getType() {
        return GameType.DUELS;
    }

    /**
     * only the players of the challenge can join
     *
     * @param kit the kit everybody gets, null: duels.kit
     */
    public void reserve(Set<UUID> players, @Nullable String kit) {
        this.reserved = Set.copyOf(players);
        this.kit = kit;
    }

    public boolean isReserved() {
        return !reserved.isEmpty();
    }

    /**
     * @return true if nobody waits in the duel and it isn't reserved (for a challenge)
     */
    public boolean isFree() {
        return !isStopped() && getState() == GameState.LOBBY && participants.isEmpty() && !isReserved();
    }

    @Override
    public boolean isJoinable() {
        return !isReserved() && super.isJoinable();
    }

    @Override
    @Nullable
    public Component getJoinDenial(UUID uuid) {
        if (isReserved() && !reserved.contains(uuid) && getState() == GameState.LOBBY) {
            return i18n(LangText.DUEL_PRIVATE);
        }
        return super.getJoinDenial(uuid);
    }

    /**
     * a player of a challenge leaves the lobby: the duel is open for everybody again
     */
    @Override
    protected void onLeave(GamePlayer gamePlayer, LeaveReason reason) {
        super.onLeave(gamePlayer, reason);
        if (getState() == GameState.LOBBY && isReserved()) {
            this.reserved = Set.of();
            this.kit = null;
        }
    }

    /**
     * @return the name of the kit everybody gets (empty: none)
     */
    public String getKitName() {
        if (kit != null) return kit;
        String name = settings().getString("kit");
        return name == null ? "" : name.trim();
    }

    @Override
    protected void onBegin() {
        super.onBegin();
        Kits.Kit duelKit = plugin.getKits().get(getKitName());
        if (duelKit == null) return;
        for (GamePlayer member : getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = getParticipant(player.getUniqueId());
            if (participant != null && participant.isAlive()) Kits.apply(player, duelKit);
        }
    }

    /**
     * the chests of the map keep what they have
     */
    @Override
    public void fillContainer(Inventory inventory) {
    }

}
