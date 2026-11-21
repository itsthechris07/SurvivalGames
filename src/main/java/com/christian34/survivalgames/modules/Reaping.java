package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reaping (modules.reaping): more players than max-players can wait in the lobby (applicants * max-players), when
 * the game starts max-players of them are drawn. Everybody has one lot, plus missed-lots for every reaping he wasn't
 * drawn in (until he is) and the lots of his permission (sg.reaping.lots.&lt;n&gt;: n lots). Who isn't drawn watches the
 * game.
 *
 * @author Christian34
 */
public class Reaping {
    public static final String MODULE = "reaping";
    public static final String LOTS_PERMISSION = "sg.reaping.lots.";
    private final SurvivalGames plugin;
    /**
     * the reapings every player wasn't drawn in since he was drawn last
     */
    private final Map<UUID, Integer> missed = new ConcurrentHashMap<>();
    private Random random = ThreadLocalRandom.current();

    public Reaping(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public boolean isUsed(@Nullable Game game) {
        return plugin.isModuleEnabled(MODULE, game);
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection("modules." + MODULE);
        return section != null ? section : plugin.getFileManager().getConfigFile().getData()
                .createSection("modules." + MODULE);
    }

    /**
     * tests: a random with a seed
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * @return how many players can wait in the lobby of the game (more than max-players with a reaping)
     */
    public int getLobbyLimit(Game game) {
        int max = game.getMaxPlayers();
        if (!isUsed(game)) return max;
        return Math.max(max, (int) Math.floor(max * Math.max(1, config().getDouble("applicants", 2))));
    }

    /**
     * @return the lots of the player in the next reaping
     */
    public int getLots(UUID uuid, @Nullable Player player) {
        int lots = 1 + missed.getOrDefault(uuid, 0) * Math.max(0, config().getInt("missed-lots", 1));
        if (player != null) {
            for (int extra = 10; extra > 1; extra--) {
                if (player.hasPermission(LOTS_PERMISSION + extra)) {
                    lots += extra - 1;
                    break;
                }
            }
        }
        return lots;
    }

    /**
     * the game starts with more participants than max-players: max-players of them are drawn (with their lots)
     *
     * @return the participants who haven't been drawn (they don't take part anymore)
     */
    public List<Participant> reap(Game game) {
        List<Participant> applicants = new ArrayList<>(game.getParticipants());
        int places = game.getMaxPlayers();
        if (!isUsed(game) || applicants.size() <= places) return List.of();
        plugin.getTelemetry().used(Telemetry.Feature.REAPING);
        Set<Participant> drawn = new HashSet<>();
        List<Participant> pool = new ArrayList<>(applicants);
        while (drawn.size() < places && !pool.isEmpty()) {
            int total = pool.stream().mapToInt(this::lots).sum();
            int lot = random.nextInt(total);
            for (Participant participant : pool) {
                lot -= lots(participant);
                if (lot < 0) {
                    drawn.add(participant);
                    pool.remove(participant);
                    break;
                }
            }
        }
        List<String> names = drawn.stream().map(Participant::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        game.broadcast(LangText.REAPING, applicants.size(), places, String.join(", ", names));
        for (Participant participant : applicants) {
            boolean chosen = drawn.contains(participant);
            if (chosen) {
                missed.remove(participant.getUuid());
            } else {
                missed.merge(participant.getUuid(), 1, Integer::sum);
            }
            Player player = participant.getPlayer();
            if (player == null) continue;
            player.showTitle(Title.title(I.i18n(chosen ? LangText.REAPING_DRAWN_TITLE : LangText.REAPING_NOT_DRAWN_TITLE),
                    chosen ? Component.empty() : I.i18n(LangText.REAPING_NOT_DRAWN_SUBTITLE)));
            player.playSound(player, chosen ? Sound.BLOCK_BELL_USE : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            if (!chosen) player.sendMessage(game.prefixed(LangText.REAPING_NOT_DRAWN, getLots(participant.getUuid(), player)));
        }
        return pool;
    }

    private int lots(Participant participant) {
        return getLots(participant.getUuid(), participant.getPlayer());
    }

}
