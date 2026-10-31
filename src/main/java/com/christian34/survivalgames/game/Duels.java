package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Kits;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Duels: the queue (/sg duel - the duel with a player waiting first) and challenges (/sg duel &lt;player&gt; [kit]),
 * which the challenged player accepts or denies within duels.invite-seconds. An accepted challenge takes a free duel
 * arena and reserves it for the two players.
 *
 * @author Christian34
 */
public class Duels {
    private final SurvivalGames plugin;
    /**
     * the open challenges: challenged player -> challenger -> challenge
     */
    private final Map<UUID, Map<UUID, Challenge>> challenges = new HashMap<>();
    private Clock clock = Clock.systemDefaultZone();

    /**
     * @param kit     the kit of the duel, null: duels.kit
     * @param expires the time (milliseconds) the challenge is over
     */
    record Challenge(UUID from, String fromName, @Nullable String kit, long expires) {
    }

    public Duels(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    /**
     * only for the tests: the time challenges expire against
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return a duel nobody waits in, null if there is none
     */
    @Nullable
    private DuelsGame findFreeGame() {
        return plugin.getGameManager().getGames().stream()
                .filter(game -> game instanceof DuelsGame duel && duel.isFree() && !duel.isStopped())
                .map(DuelsGame.class::cast).findFirst().orElse(null);
    }

    private boolean hasDuels() {
        return plugin.getGameManager().getGames().stream().anyMatch(game -> game instanceof DuelsGame);
    }

    /**
     * the player waits for an opponent: in the duel somebody waits in already, otherwise in a free one
     */
    public void queue(Player player) {
        GamePlayer gamePlayer = plugin.getUser(player);
        if (gamePlayer.getGame() != null) {
            player.sendMessage(I.prefixed(LangText.ALREADY_IN_GAME, gamePlayer.getGame().getArena().getName()));
            return;
        }
        DuelsGame game = plugin.getGameManager().getGames().stream()
                .filter(other -> other instanceof DuelsGame && other.isJoinable())
                .map(DuelsGame.class::cast)
                .max(Comparator.comparingInt(other -> other.getParticipants().size())).orElse(null);
        if (game == null) {
            player.sendMessage(I.prefixed(LangText.DUEL_NO_ARENA));
            return;
        }
        plugin.getTelemetry().joined(Telemetry.Join.COMMAND);
        plugin.getGameManager().join(gamePlayer, game.getArena());
    }

    /**
     * the player challenges the target to a duel
     *
     * @param kitName the kit of the duel, null: duels.kit
     */
    public void challenge(Player player, Player target, @Nullable String kitName) {
        if (player.equals(target)) {
            player.sendMessage(I.prefixed(LangText.DUEL_SELF));
            return;
        }
        if (plugin.getUser(player).getGame() != null) {
            player.sendMessage(I.prefixed(LangText.ALREADY_IN_GAME, plugin.getUser(player).getGame().getArena().getName()));
            return;
        }
        if (plugin.getUser(target).getGame() != null) {
            player.sendMessage(I.prefixed(LangText.DUEL_BUSY, target.getName()));
            return;
        }
        if (!hasDuels()) {
            player.sendMessage(I.prefixed(LangText.DUEL_NO_ARENA));
            return;
        }
        String kit = null;
        if (kitName != null) {
            Kits.Kit found = plugin.getKits().get(kitName);
            if (found == null) {
                player.sendMessage(I.prefixed(LangText.KIT_NOT_FOUND, kitName));
                return;
            }
            kit = found.name();
        }
        long seconds = Math.max(10, plugin.getFileManager().getConfigFile().getInt("duels.invite-seconds"));
        challenges.computeIfAbsent(target.getUniqueId(), uuid -> new HashMap<>()).put(player.getUniqueId(),
                new Challenge(player.getUniqueId(), player.getName(), kit, clock.millis() + seconds * 1000));
        player.sendMessage(I.prefixed(LangText.DUEL_SENT, target.getName(), seconds));
        target.sendMessage(kit == null ? I.prefixed(LangText.DUEL_RECEIVED, player.getName(), seconds)
                : I.prefixed(LangText.DUEL_RECEIVED_KIT, player.getName(), seconds, kit));
    }

    /**
     * @return the challenge of the player (by his name) that hasn't expired yet - it is removed
     */
    @Nullable
    private Challenge take(Player target, String fromName) {
        Map<UUID, Challenge> received = challenges.get(target.getUniqueId());
        if (received == null) return null;
        received.values().removeIf(challenge -> challenge.expires() < clock.millis());
        Challenge challenge = received.values().stream()
                .filter(other -> other.fromName().equalsIgnoreCase(fromName)).findFirst().orElse(null);
        if (challenge != null) received.remove(challenge.from());
        if (received.isEmpty()) challenges.remove(target.getUniqueId());
        return challenge;
    }

    /**
     * the target accepts the challenge: both of them join a free duel
     */
    public void accept(Player target, String fromName) {
        Challenge challenge = take(target, fromName);
        Player challenger = challenge == null ? null : Bukkit.getPlayer(challenge.from());
        if (challenge == null || challenger == null) {
            target.sendMessage(I.prefixed(LangText.DUEL_NO_CHALLENGE, fromName));
            return;
        }
        for (Player player : new Player[]{target, challenger}) {
            Game game = plugin.getUser(player).getGame();
            if (game != null) {
                target.sendMessage(I.prefixed(LangText.DUEL_BUSY, player.getName()));
                return;
            }
        }
        DuelsGame game = findFreeGame();
        if (game == null) {
            target.sendMessage(I.prefixed(LangText.DUEL_NO_ARENA));
            challenger.sendMessage(I.prefixed(LangText.DUEL_NO_ARENA));
            return;
        }
        game.reserve(Set.of(target.getUniqueId(), challenger.getUniqueId()), challenge.kit());
        challenger.sendMessage(I.prefixed(LangText.DUEL_ACCEPTED, target.getName()));
        plugin.getGameManager().join(plugin.getUser(challenger), game.getArena());
        plugin.getGameManager().join(plugin.getUser(target), game.getArena());
    }

    public void deny(Player target, String fromName) {
        Challenge challenge = take(target, fromName);
        if (challenge == null) {
            target.sendMessage(I.prefixed(LangText.DUEL_NO_CHALLENGE, fromName));
            return;
        }
        target.sendMessage(I.prefixed(LangText.DUEL_DENIED_SELF, challenge.fromName()));
        Player challenger = Bukkit.getPlayer(challenge.from());
        if (challenger != null) challenger.sendMessage(I.prefixed(LangText.DUEL_DENIED, target.getName()));
    }

    /**
     * the player has left the server: his challenges are gone
     */
    public void forget(UUID uuid) {
        challenges.remove(uuid);
        challenges.values().forEach(received -> received.remove(uuid));
        challenges.values().removeIf(Map::isEmpty);
    }

}
