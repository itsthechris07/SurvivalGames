package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alliances (modules.alliances) in games where everybody plays alone: two players (or more, max-size) agree not to
 * hurt each other - /sg ally &lt;player&gt; asks, the same command of the other player accepts. Allies can't hurt each
 * other and the tracker ignores them. /sg ally leave breaks the alliance (everybody hears about the betrayal), all
 * alliances break once only break-at players are left, in the deathmatch or when nobody else is left.
 *
 * @author Christian34
 */
public class Alliances extends GameModule {
    public static final String MODULE = "alliances";

    public Alliances(SurvivalGames plugin) {
        super(plugin, MODULE);
    }

    /**
     * @return true if the game has alliances: the module is on for it and everybody plays alone
     */
    @Override
    public boolean isUsed(@Nullable Game game) {
        return game != null && game.getArena().getTeamSize() == 1 && isEnabled(game);
    }

    /**
     * @return the allies of the player (without him), empty if he has none
     */
    public Set<UUID> getAllies(Game game, UUID player) {
        Set<UUID> alliance = game.getAlliances().get(player);
        if (alliance == null) return Set.of();
        Set<UUID> allies = new HashSet<>(alliance);
        allies.remove(player);
        return allies;
    }

    public boolean areAllies(Game game, UUID player, UUID other) {
        Set<UUID> alliance = game.getAlliances().get(player);
        return alliance != null && !player.equals(other) && alliance.contains(other);
    }

    /**
     * the player asks the target for an alliance - or accepts his request
     */
    public void request(Player player, Game game, Participant target) {
        if (!isUsed(game)) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_DISABLED));
            return;
        }
        Participant self = game.getParticipant(player.getUniqueId());
        if (self == null || !self.isAlive() || game.getState() != GameState.RUNNING) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_NOT_NOW));
            return;
        }
        if (isBroken(game)) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_TOO_LATE));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!target.isAlive() || target.getUuid().equals(uuid)) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_INVALID_TARGET, target.getName()));
            return;
        }
        if (areAllies(game, uuid, target.getUuid())) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_ALREADY, target.getName()));
            return;
        }
        int maxSize = Math.max(2, config().getInt("max-size", 2));
        Set<UUID> mine = game.getAlliances().getOrDefault(uuid, Set.of(uuid));
        Set<UUID> theirs = game.getAlliances().getOrDefault(target.getUuid(), Set.of(target.getUuid()));
        if (mine.size() + theirs.size() > maxSize) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_FULL, maxSize));
            return;
        }
        Map<UUID, Set<UUID>> requests = game.getAllyRequests();
        Set<UUID> asked = requests.get(uuid);
        if (asked != null && asked.remove(target.getUuid())) {
            join(game, mine, theirs);
            return;
        }
        if (!requests.computeIfAbsent(target.getUuid(), key -> ConcurrentHashMap.newKeySet()).add(uuid)) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_ALREADY_ASKED, target.getName()));
            return;
        }
        player.sendMessage(I.prefixed(player, LangText.ALLY_ASKED, target.getName()));
        Player other = target.getPlayer();
        if (other != null) other.sendMessage(I.prefixed(other, LangText.ALLY_REQUEST, player.getName()));
    }

    private void join(Game game, Set<UUID> first, Set<UUID> second) {
        Set<UUID> alliance = ConcurrentHashMap.newKeySet();
        alliance.addAll(first);
        alliance.addAll(second);
        for (UUID member : alliance) {
            game.getAlliances().put(member, alliance);
            game.getAllyRequests().remove(member);
        }
        plugin.getUsageStats().used(UsageStats.Feature.ALLIANCE);
        game.broadcast(LangText.ALLY_FORMED, names(game, alliance));
    }

    private static String names(Game game, Set<UUID> alliance) {
        return String.join(", ", alliance.stream().map(game::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    /**
     * the player leaves his alliance (betrays his allies)
     */
    public void leave(Player player, Game game) {
        Set<UUID> allies = getAllies(game, player.getUniqueId());
        if (allies.isEmpty()) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_NONE));
            return;
        }
        remove(game, player.getUniqueId());
        game.broadcast(LangText.ALLY_BETRAYED, player.getName(), String.join(", ",
                allies.stream().map(game::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList()));
    }

    /**
     * the player isn't in an alliance anymore (the rest of it stays together if there are at least two)
     */
    private void remove(Game game, UUID player) {
        game.getAllyRequests().remove(player);
        game.getAllyRequests().values().forEach(asked -> asked.remove(player));
        Set<UUID> alliance = game.getAlliances().remove(player);
        if (alliance == null) return;
        alliance.remove(player);
        if (alliance.size() < 2) alliance.forEach(member -> game.getAlliances().remove(member));
    }

    /**
     * @return true if no alliances are possible anymore (only break-at players left, the deathmatch)
     */
    private boolean isBroken(Game game) {
        int breakAt = config().getInt("break-at", 3);
        return breakAt > 0 && game.getAlive().size() <= breakAt || game.isFinalPhase();
    }

    /**
     * the participant is out: he leaves his alliance, all alliances break when it is time (break-at, deathmatch) or
     * when only one alliance is left
     */
    public void onEliminated(Game game, Participant participant) {
        if (game.getAlliances().isEmpty() && game.getAllyRequests().isEmpty()) return;
        remove(game, participant.getUuid());
        check(game);
    }

    /**
     * all alliances break when it is time (break-at, deathmatch) or nobody but one alliance is left
     */
    @Override
    public void tick(Game game) {
        check(game);
    }

    private void check(Game game) {
        if (game.getAlliances().isEmpty()) return;
        List<Participant> alive = game.getAlive();
        boolean onlyAllies = !alive.isEmpty() && alive.stream().allMatch(other -> areAllies(game,
                alive.getFirst().getUuid(), other.getUuid()) || other == alive.getFirst());
        if (!isBroken(game) && !onlyAllies) return;
        game.getAlliances().clear();
        game.getAllyRequests().clear();
        game.broadcast(LangText.ALLY_ALL_BROKEN);
    }

}
