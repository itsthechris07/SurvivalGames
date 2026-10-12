package com.christian34.survivalgames.lib;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.LangText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The sidebar of the players in a game (players, kills, play time, ...), the team colors of the name tags and the
 * names in the tab list. Every player in a game gets an own scoreboard, updated every second.
 *
 * @author Christian34
 */
public class Sidebar {
    private static final int MAX_LINES = 15;
    private static final String OBJECTIVE = "sg";
    private static final String TEAM_PREFIX = "sg_";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final SurvivalGames plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /**
     * false if the server can't show custom names of scores (MockBukkit): the lines are entries then
     */
    private boolean customNames = true;

    public Sidebar(SurvivalGames plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    private boolean isEnabled() {
        return plugin.getFileManager().getConfigFile().getBoolean("scoreboard");
    }

    private void updateAll() {
        boolean enabled = isEnabled();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Game game = plugin.getUser(player).getGame();
            Component tabName = getTabName(player, game);
            if (tabName != null && !tabName.equals(player.playerListName())) player.playerListName(tabName);
            if (game != null) {
                plugin.getTabSupport().takeOver(player, tabName, enabled);
            } else {
                plugin.getTabSupport().release(player);
            }
            if (enabled && game != null) {
                update(player, game);
            } else {
                remove(player);
            }
        }
    }

    /**
     * the player gets the main scoreboard back (and the board is forgotten when the player quits)
     */
    public void remove(Player player) {
        if (boards.remove(player.getUniqueId()) != null && player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * @return the name of the player in the tab list (with his team), null if he isn't in a team of a game
     */
    @Nullable
    private static Component getTabName(Player player, @Nullable Game game) {
        GameTeam team = game == null ? null : game.getTeam(player.getUniqueId());
        if (team == null) return null;
        Participant participant = game.getParticipant(player.getUniqueId());
        boolean alive = participant == null || participant.isAlive();
        return i18n(alive ? LangText.TABLIST_NAME : LangText.TABLIST_NAME_OUT, team.getDisplayName(), player.getName());
    }

    private void update(Player player, Game game) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), uuid -> createBoard());
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            Component title = i18n(LangText.SCOREBOARD_TITLE, i18n(game.getType().getDisplayName()));
            if (!title.equals(objective.displayName())) objective.displayName(title);
            setLines(objective, board, getLines(player, game));
        }
        updateTeams(board, game);
    }

    private Scoreboard createBoard() {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty(),
                RenderType.INTEGER);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            objective.numberFormat(NumberFormat.blank());
        } catch (RuntimeException ex) {
            // not implemented by MockBukkit - only hides the red numbers
            customNames = false;
        }
        return board;
    }

    private List<Component> getLines(Player player, Game game) {
        GameTeam team = game.getTeam(player.getUniqueId());
        Participant participant = game.getParticipant(player.getUniqueId());
        boolean teams = game.getArena().getTeamSize() > 1;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(i18n(LangText.SCOREBOARD_ARENA, game.getArena().getDisplayName()));
        switch (game.getState()) {
            case LOBBY, STARTING -> {
                lines.add(i18n(LangText.SCOREBOARD_PLAYERS, game.getParticipants().size(), game.getMaxPlayers()));
                if (teams) {
                    lines.add(i18n(LangText.SCOREBOARD_TEAM, team != null ? team.getDisplayName()
                            : i18n(LangText.SCOREBOARD_NO_TEAM)));
                }
                if (game.getCountdown() >= 0) {
                    lines.add(Component.empty());
                    lines.add(i18n(LangText.SCOREBOARD_COUNTDOWN, game.getCountdown()));
                }
            }
            case RUNNING -> {
                if (game instanceof VaroGame varo) lines.add(i18n(LangText.SCOREBOARD_DAY, varo.getDay()));
                if (game instanceof SurvivalGame sg) lines.add(i18n(LangText.SCOREBOARD_TIME, Game.formatTime(sg.getSeconds())));
                lines.add(Component.empty());
                if (teams) {
                    if (team != null) lines.add(i18n(LangText.SCOREBOARD_TEAM, team.getDisplayName()));
                    lines.add(i18n(LangText.SCOREBOARD_TEAMS_ALIVE, game.getAliveTeams().size()));
                }
                lines.add(i18n(LangText.SCOREBOARD_PLAYERS_ALIVE, game.getAlive().size()));
                int border = game.getBorderSize();
                if (border > 0) lines.add(i18n(LangText.SCOREBOARD_BORDER, border));
                if (participant != null) {
                    lines.add(Component.empty());
                    lines.add(i18n(LangText.SCOREBOARD_KILLS, participant.getKills()));
                    if (!participant.getStrikes().isEmpty()) {
                        lines.add(i18n(LangText.SCOREBOARD_STRIKES, participant.getStrikes().size()));
                    }
                    if (game instanceof VaroGame varo && participant.isAlive()) {
                        lines.add(i18n(LangText.SCOREBOARD_TIME_LEFT, Game.formatTime(varo.getSessionTime(participant))));
                    }
                }
                if (game instanceof SurvivalGame sg) {
                    if (sg.isDeathmatch()) {
                        lines.add(i18n(LangText.SCOREBOARD_DEATHMATCH));
                    } else if (sg.isGracePeriod()) {
                        lines.add(i18n(LangText.SCOREBOARD_GRACE));
                    }
                }
            }
            case ENDED -> {
                String winner = game.getWinner();
                GameTeam winnerTeam = winner == null ? null : game.getTeams().get(winner);
                lines.add(i18n(LangText.SCOREBOARD_WINNER, winnerTeam != null ? winnerTeam.getDisplayName()
                        : i18n(LangText.SCOREBOARD_NO_TEAM)));
            }
        }
        return lines;
    }

    /**
     * the lines are fixed entries with a custom name, so changing a line doesn't flicker
     */
    private void setLines(Objective objective, Scoreboard board, List<Component> lines) {
        if (!customNames) {
            for (String entry : board.getEntries()) {
                if (objective.getScore(entry).isScoreSet()) board.resetScores(entry);
            }
            for (int i = 0; i < lines.size(); i++) {
                // entries must be unique - empty lines differ by the number of spaces
                String text = LEGACY.serialize(lines.get(i));
                // entries may only have 40 characters
                text = text.substring(0, Math.min(text.length(), 40 - i)) + " ".repeat(i);
                objective.getScore(text).setScore(lines.size() - i);
            }
            return;
        }
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = "line" + i;
            if (i < lines.size()) {
                Score score = objective.getScore(entry);
                score.setScore(lines.size() - i);
                if (!lines.get(i).equals(score.customName())) {
                    score.customName(lines.get(i));
                }
            } else if (objective.getScore(entry).isScoreSet()) {
                board.resetScores(entry);
            }
        }
    }

    /**
     * the name tags have the color of the team
     */
    private void updateTeams(Scoreboard board, Game game) {
        for (GameTeam team : game.getTeams().getTeams()) {
            String name = TEAM_PREFIX + team.getKey();
            Team boardTeam = board.getTeam(name);
            if (boardTeam == null) {
                boardTeam = board.registerNewTeam(name);
                // the color of a new team can't be read before it is set
                boardTeam.color(team.getColor());
            } else if (boardTeam.color() != team.getColor()) {
                boardTeam.color(team.getColor());
            }
            for (UUID member : team.getMembers()) {
                String playerName = game.getName(member);
                if (!boardTeam.hasEntry(playerName)) boardTeam.addEntry(playerName);
            }
        }
        for (Team boardTeam : new ArrayList<>(board.getTeams())) {
            if (!boardTeam.getName().startsWith(TEAM_PREFIX)) continue;
            GameTeam team = game.getTeams().get(boardTeam.getName().substring(TEAM_PREFIX.length()));
            for (String entry : new ArrayList<>(boardTeam.getEntries())) {
                Player player = Bukkit.getPlayerExact(entry);
                if (team == null || player != null && !team.isMember(player.getUniqueId())) {
                    boardTeam.removeEntry(entry);
                }
            }
            if (team == null) boardTeam.unregister();
        }
    }

}
