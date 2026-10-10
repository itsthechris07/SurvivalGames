package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.Teams;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * /sg game &lt;arena&gt;: admins start, stop and reset the game of an arena, revive players, give strikes (Varo) and
 * manage its teams.
 *
 * @author Christian34
 */
public class CommandGame {
    static final String GAME = ROOT + " game <arena>";

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    private static void send(CommandSender sender, LangText message, Object... arguments) {
        sender.sendMessage(I.prefixed(message, arguments));
    }

    /**
     * @return the game of the arena, null if it has none (the sender got a message)
     */
    @Nullable
    private static Game game(CommandSender sender, Arena arena) {
        Game game = plugin().getGameManager().getGame(arena);
        if (game == null) send(sender, LangText.NO_GAME_IN_ARENA, arena.getName());
        return game;
    }

    /**
     * starts the game right away (also during the lobby countdown)
     *
     * @param force also with fewer players than min-players
     */
    static void start(CommandSender sender, Game game, boolean force) {
        Component denial = game.checkStart(force);
        if (denial != null) {
            send(sender, LangText.CANNOT_START, denial);
            return;
        }
        game.broadcast(LangText.GAME_STARTING, sender.getName());
        game.start();
        if (!(sender instanceof Player player) || !game.isMember(player)) {
            send(sender, LangText.GAME_STARTED_BY_YOU, game.getArena().getName());
        }
    }

    @Command(GAME + " start")
    @CommandDescription("command_game_start")
    @Permission(ADMIN)
    public void startCmd(CommandSender sender, @Argument("arena") Arena arena, @Flag("force") boolean force) {
        Game game = game(sender, arena);
        if (game != null) start(sender, game, force);
    }

    @Command(GAME + " stop")
    @CommandDescription("command_game_stop")
    @Permission(ADMIN)
    public void stopCmd(CommandSender sender, @Argument("arena") Arena arena) {
        Game game = game(sender, arena);
        if (game == null) return;
        // a new round of Survival Games if the arena gets one on start
        boolean nextRound = arena.hasAutoGame() || plugin().getNetworkManager().isGameServer();
        plugin().getGameManager().stopGame(game, nextRound);
        send(sender, game instanceof VaroGame ? LangText.VARO_STOPPED : LangText.GAME_STOPPED_ADMIN, arena.getName());
    }

    @Command(GAME + " create")
    @CommandDescription("command_game_create")
    @Permission(ADMIN)
    public void createCmd(CommandSender sender, @Argument("arena") Arena arena) {
        CommandArena.createGame(sender, arena);
    }

    @Command(GAME + " reset")
    @CommandDescription("command_game_reset")
    @Permission(ADMIN)
    public void resetCmd(CommandSender sender, @Argument("arena") Arena arena, @Flag("teams") boolean teams,
                         @Flag("confirm") boolean confirm) {
        Game game = game(sender, arena);
        if (game == null) return;
        if (!(game instanceof VaroGame varo)) {
            send(sender, LangText.ONLY_VARO);
            return;
        }
        if (!confirm) {
            send(sender, teams ? LangText.RESET_CONFIRM_TEAMS : LangText.RESET_CONFIRM, arena.getName());
            return;
        }
        VaroGame next = plugin().getGameManager().resetVaro(varo, !teams);
        next.broadcast(LangText.RESET_DONE, sender.getName());
        send(sender, LangText.RESET_DONE_ADMIN, arena.getName());
    }

    @Suggestions("out-players")
    public List<String> outPlayers(CommandContext<CommandSender> context, String input) {
        Arena arena = context.getOrDefault("arena", null);
        Game game = arena == null ? null : plugin().getGameManager().getGame(arena);
        if (game == null) return List.of();
        return game.getParticipants().stream().filter(p -> !p.isAlive()).map(Participant::getName).toList();
    }

    @Command(GAME + " revive <player>")
    @CommandDescription("command_game_revive")
    @Permission(ADMIN)
    public void reviveCmd(CommandSender sender, @Argument("arena") Arena arena,
                          @Argument(value = "player", suggestions = "out-players") String name) {
        Game game = game(sender, arena);
        if (game == null) return;
        Participant participant = game.findParticipant(name);
        if (participant == null) {
            send(sender, LangText.PLAYER_NOT_FOUND);
            return;
        }
        if (participant.isAlive()) {
            send(sender, LangText.PLAYER_NOT_OUT, participant.getName());
            return;
        }
        if (game.getState() != GameState.RUNNING) {
            send(sender, LangText.NOT_RUNNING);
            return;
        }
        game.revive(participant);
        game.broadcast(LangText.PLAYER_REVIVED, participant.getName());
        if (!(sender instanceof Player player) || !game.isMember(player)) {
            send(sender, LangText.PLAYER_REVIVED, participant.getName());
        }
    }

    @Command(GAME + " deathmatch")
    @CommandDescription("command_game_deathmatch")
    @Permission(ADMIN)
    public void deathmatchCmd(CommandSender sender, @Argument("arena") Arena arena) {
        Game game = game(sender, arena);
        if (game == null) return;
        if (!(game instanceof SurvivalGame sg)) {
            send(sender, LangText.ONLY_SURVIVAL_GAMES);
            return;
        }
        if (sg.getState() != GameState.RUNNING || sg.isDeathmatch()) {
            send(sender, LangText.NOT_RUNNING);
            return;
        }
        sg.startDeathmatch();
    }

    // ---------------------------------------------------------------- strikes (Varo)

    /**
     * @return the Varo of the arena, null if it has none (the sender got a message)
     */
    @Nullable
    private static VaroGame varo(CommandSender sender, Arena arena) {
        Game game = game(sender, arena);
        if (game == null || game instanceof VaroGame) return (VaroGame) game;
        send(sender, LangText.ONLY_VARO);
        return null;
    }

    @Suggestions("participants")
    public List<String> participants(CommandContext<CommandSender> context, String input) {
        Arena arena = context.getOrDefault("arena", null);
        Game game = arena == null ? null : plugin().getGameManager().getGame(arena);
        if (game == null) return List.of();
        return game.getParticipants().stream().map(Participant::getName).toList();
    }

    @Command(GAME + " strike <player> [reason]")
    @CommandDescription("command_game_strike")
    @Permission(ADMIN)
    public void strikeCmd(CommandSender sender, @Argument("arena") Arena arena,
                          @Argument(value = "player", suggestions = "participants") String name,
                          @Argument("reason") @Greedy @Nullable String reason) {
        VaroGame varo = varo(sender, arena);
        if (varo == null) return;
        Participant participant = varo.findParticipant(name);
        if (participant == null) {
            send(sender, LangText.PLAYER_NOT_FOUND);
            return;
        }
        if (varo.getState() != GameState.RUNNING) {
            send(sender, LangText.NOT_RUNNING);
            return;
        }
        String text = reason == null || reason.isBlank() ? I.plain(LangText.STRIKE_NO_REASON) : reason.trim();
        int number = varo.strike(participant, text, sender.getName());
        // the announcement only reaches the players of the Varo
        if (!(sender instanceof Player player) || varo.getParticipant(player.getUniqueId()) == null
                && !varo.isMember(player)) {
            send(sender, LangText.STRIKE_GIVEN, participant.getName(), number, text);
        }
    }

    @Command(GAME + " unstrike <player>")
    @CommandDescription("command_game_unstrike")
    @Permission(ADMIN)
    public void unstrikeCmd(CommandSender sender, @Argument("arena") Arena arena,
                            @Argument(value = "player", suggestions = "participants") String name) {
        VaroGame varo = varo(sender, arena);
        if (varo == null) return;
        Participant participant = varo.findParticipant(name);
        if (participant == null) {
            send(sender, LangText.PLAYER_NOT_FOUND);
        } else if (varo.removeStrike(participant) == null) {
            send(sender, LangText.NO_STRIKE_TO_REMOVE, participant.getName());
        } else {
            send(sender, LangText.STRIKE_REMOVED, participant.getName(), participant.getStrikes().size());
        }
    }

    @Command(GAME + " strikes [player]")
    @CommandDescription("command_game_strikes")
    @Permission(ADMIN)
    public void strikesCmd(CommandSender sender, @Argument("arena") Arena arena,
                           @Argument(value = "player", suggestions = "participants") @Nullable String name) {
        VaroGame varo = varo(sender, arena);
        if (varo == null) return;
        if (name != null) {
            Participant participant = varo.findParticipant(name);
            if (participant == null) {
                send(sender, LangText.PLAYER_NOT_FOUND);
            } else {
                CommandSurvivalGames.showStrikes(sender, participant);
            }
            return;
        }
        List<Participant> struck = varo.getParticipants().stream().filter(p -> !p.getStrikes().isEmpty()).toList();
        if (struck.isEmpty()) {
            send(sender, LangText.NO_STRIKES, arena.getName());
            return;
        }
        sender.sendMessage(I.i18n(LangText.STRIKES_LIST_TITLE, arena.getName()));
        for (Participant participant : struck) {
            sender.sendMessage(I.i18n(LangText.STRIKES_LIST_ENTRY, participant.getName(),
                    participant.getStrikes().size(), arena.getName()));
        }
    }

    // ---------------------------------------------------------------- teams

    @Command(GAME + " teams")
    @CommandDescription("command_game_teams")
    @Permission(ADMIN)
    public void teamsCmd(CommandSender sender, @Argument("arena") Arena arena) {
        Game game = game(sender, arena);
        if (game != null) CommandTeam.list(sender, game);
    }

    /**
     * @return false if the teams can't be changed anymore (the sender got a message)
     */
    private static boolean checkLobby(CommandSender sender, Game game) {
        if (game.getState() == GameState.LOBBY) return true;
        send(sender, LangText.TEAMS_LOCKED);
        return false;
    }

    @Command(GAME + " team create <name> [color]")
    @CommandDescription("command_game_team_create")
    @Permission(ADMIN)
    public void teamCreateCmd(CommandSender sender, @Argument("arena") Arena arena, @Argument("name") String name,
                              @Argument(value = "color", suggestions = "team-colors") @Nullable String colorName) {
        Game game = game(sender, arena);
        if (game == null || !checkLobby(sender, game)) return;
        NamedTextColor color = Teams.parseColor(colorName);
        if (colorName != null && color == null) {
            send(sender, LangText.INVALID_COLOR, colorName);
            return;
        }
        if (!Teams.NAME.matcher(name).matches()) {
            send(sender, LangText.TEAM_NAME_INVALID);
            return;
        }
        GameTeam team = game.createTeam(name, color);
        if (team == null) {
            send(sender, LangText.TEAM_EXISTS, name);
            return;
        }
        send(sender, LangText.TEAM_CREATED_ADMIN, team.getDisplayName(), arena.getName(), team.getName());
    }

    @Command(GAME + " team add <team> <player>")
    @CommandDescription("command_game_team_add")
    @Permission(ADMIN)
    public void teamAddCmd(CommandSender sender, @Argument("arena") Arena arena, @Argument("team") GameTeam team,
                           @Argument("player") String name) {
        Game game = game(sender, arena);
        if (game == null || !checkLobby(sender, game)) return;
        UUID uuid;
        String playerName;
        Participant participant = game.findParticipant(name);
        if (participant != null) {
            uuid = participant.getUuid();
            playerName = participant.getName();
        } else {
            Player online = Bukkit.getPlayerExact(name);
            OfflinePlayer player = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
            if (player == null || player.getName() == null) {
                send(sender, LangText.PLAYER_NOT_FOUND);
                return;
            }
            uuid = player.getUniqueId();
            playerName = player.getName();
        }
        if (!game.addToTeam(uuid, playerName, team)) {
            send(sender, LangText.PLAYER_NOT_IN_GAME, playerName, arena.getName());
            return;
        }
        send(sender, LangText.TEAM_PLAYER_ADDED, playerName, team.getDisplayName());
    }

    @Command(GAME + " team remove <player>")
    @CommandDescription("command_game_team_remove")
    @Permission(ADMIN)
    public void teamRemoveCmd(CommandSender sender, @Argument("arena") Arena arena, @Argument("player") String name) {
        Game game = game(sender, arena);
        if (game == null || !checkLobby(sender, game)) return;
        Participant participant = game.findParticipant(name);
        GameTeam team = participant == null ? null : game.leaveTeam(participant.getUuid());
        if (team == null) {
            send(sender, LangText.PLAYER_NOT_IN_TEAM, name);
            return;
        }
        send(sender, LangText.TEAM_PLAYER_REMOVED, participant.getName(), team.getDisplayName());
    }

    @Command(GAME + " team delete <team>")
    @CommandDescription("command_game_team_delete")
    @Permission(ADMIN)
    public void teamDeleteCmd(CommandSender sender, @Argument("arena") Arena arena, @Argument("team") GameTeam team) {
        Game game = game(sender, arena);
        if (game == null || !checkLobby(sender, game)) return;
        game.deleteTeam(team);
        send(sender, LangText.TEAM_DELETED, team.getDisplayName());
    }

}
