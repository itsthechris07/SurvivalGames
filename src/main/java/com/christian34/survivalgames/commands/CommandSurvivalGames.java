package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.network.ServerStatus;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.user.GamePlayer;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The general commands of /sg: joining, watching and leaving games, the list of games, info, play time, ping, last
 * seen and the admin commands start, build, reload and debug.
 *
 * @author Christian34
 */
public class CommandSurvivalGames {
    static final String ROOT = "sg|survivalgames|varo";
    static final String ADMIN = SurvivalGames.ADMIN_PERMISSION;
    private static final Component DEBUG_HEADER = Component.text("---------------- ", NamedTextColor.DARK_GRAY)
            .append(Component.text("SurvivalGames", NamedTextColor.AQUA))
            .append(Component.text(" ----------------", NamedTextColor.DARK_GRAY));
    private final CommandManager commandManager;

    public CommandSurvivalGames(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    private static NetworkManager network() {
        return plugin().getNetworkManager();
    }

    @Command(ROOT)
    @CommandDescription("command_version")
    public void mainCmd(CommandSender sender) {
        sender.sendMessage(I.prefixed(LangText.PLUGIN_INFO, plugin().getPluginMeta().getVersion()));
    }

    @Command(ROOT + " help [query]")
    @CommandDescription("command_help")
    public void helpCmd(CommandSender sender, @Argument("query") @Greedy @Nullable String query) {
        commandManager.getMinecraftHelp().queryCommands(query == null ? "" : query, sender);
    }

    // ---------------------------------------------------------------- joining

    @Suggestions("join-targets")
    public List<String> joinTargets(CommandContext<CommandSender> context, String input) {
        if (network().isLobby()) return network().getServers().stream().map(NetworkManager.Server::name).toList();
        return plugin().getGameManager().getGames().stream().map(game -> game.getArena().getName()).toList();
    }

    @Command(ROOT + " join [target]")
    @CommandDescription("command_join")
    public void joinCmd(Player player, @Argument(value = "target", suggestions = "join-targets") @Nullable String target) {
        if (network().isLobby()) {
            if (target == null) {
                network().joinAnyServer(player, false);
            } else {
                network().joinServer(player, target);
            }
            return;
        }
        Arena arena = null;
        if (network().isGameServer()) {
            arena = network().getArena();
        } else if (target != null) {
            arena = plugin().getArenaManager().get(target);
            if (arena == null) {
                player.sendMessage(I.prefixed(LangText.ARENA_NOT_FOUND, target));
                return;
            }
        }
        plugin().getGameManager().join(plugin().getUser(player), arena);
    }

    @Command(ROOT + " spectate [target]")
    @CommandDescription("command_spectate")
    public void spectateCmd(Player player, @Argument(value = "target", suggestions = "join-targets") @Nullable String target) {
        if (network().isLobby()) {
            if (target == null) {
                network().joinAnyServer(player, true);
            } else {
                network().joinServer(player, target);
            }
            return;
        }
        GamePlayer gamePlayer = plugin().getUser(player);
        if (gamePlayer.getGame() != null) {
            player.sendMessage(I.prefixed(LangText.ALREADY_IN_GAME, gamePlayer.getGame().getArena().getName()));
            return;
        }
        Game game;
        if (target != null && !network().isGameServer()) {
            Arena arena = plugin().getArenaManager().get(target);
            if (arena == null) {
                player.sendMessage(I.prefixed(LangText.ARENA_NOT_FOUND, target));
                return;
            }
            game = plugin().getGameManager().getGame(arena);
        } else {
            game = network().isGameServer() ? network().getGame() : plugin().getGameManager().getGames().stream()
                    .filter(other -> other.maySpectate(player.getUniqueId())).findFirst().orElse(null);
        }
        if (game == null) {
            player.sendMessage(I.prefixed(LangText.NO_GAME_TO_SPECTATE));
            return;
        }
        game.spectate(gamePlayer);
    }

    @Command(ROOT + " leave")
    @CommandDescription("command_leave")
    public void leaveCmd(Player player) {
        GamePlayer gamePlayer = plugin().getUser(player);
        Game game = gamePlayer.getGame();
        if (game != null) {
            if (game instanceof VaroGame varo && !gamePlayer.isSpectator() && varo.isInCombat(player)) {
                player.sendMessage(I.prefixed(LangText.LEAVE_IN_COMBAT));
                return;
            }
            game.leave(gamePlayer, Game.LeaveReason.COMMAND);
            return;
        }
        if (plugin().getArenaManager().getEditedArena(player) != null) {
            plugin().getArenaManager().stopEditing(gamePlayer);
            player.sendMessage(I.prefixed(LangText.EDIT_STOPPED));
            return;
        }
        player.sendMessage(I.prefixed(LangText.NOT_IN_GAME));
    }

    @Command(ROOT + " list")
    @CommandDescription("command_list")
    public void listCmd(CommandSender sender) {
        if (network().isLobby()) {
            List<NetworkManager.Server> servers = network().getServers();
            if (servers.isEmpty()) {
                sender.sendMessage(I.prefixed(LangText.NO_GAMES));
                return;
            }
            sender.sendMessage(i18n(LangText.GAME_LIST_TITLE, servers.size()));
            for (NetworkManager.Server server : servers) {
                ServerStatus status = network().getStatus(server.name());
                sender.sendMessage(i18n(LangText.GAME_LIST_ENTRY, server.name(), SignManager.stateText(status),
                        status.players(), status.maxPlayers(), status.arena()));
            }
            return;
        }
        List<Game> games = plugin().getGameManager().getGames();
        if (games.isEmpty()) {
            sender.sendMessage(I.prefixed(LangText.NO_GAMES));
            return;
        }
        sender.sendMessage(i18n(LangText.GAME_LIST_TITLE, games.size()));
        for (Game game : games) {
            ServerStatus status = ServerStatus.of(game);
            sender.sendMessage(i18n(LangText.GAME_LIST_ENTRY, game.getArena().getName(), SignManager.stateText(status),
                    status.players(), status.maxPlayers(), i18n(game.getType().getDisplayName())));
        }
    }

    // ---------------------------------------------------------------- information

    @Command(ROOT + " info [arena]")
    @CommandDescription("command_info")
    public void infoCmd(CommandSender sender, @Argument("arena") @Nullable Arena arena) {
        Game game = arena != null ? plugin().getGameManager().getGame(arena)
                : sender instanceof Player player ? plugin().getUser(player).getGame() : null;
        if (game == null) {
            if (arena == null) {
                sender.sendMessage(I.prefixed(LangText.NOT_IN_GAME));
            } else {
                sender.sendMessage(I.prefixed(LangText.NO_GAME_IN_ARENA, arena.getName()));
            }
            return;
        }
        sender.sendMessage(i18n(LangText.INFO_TITLE, game.getArena().getDisplayName(), i18n(game.getType().getDisplayName())));
        sender.sendMessage(i18n(LangText.INFO_STATE, i18n(game.getState().getDisplayName())));
        boolean teams = game.getArena().getTeamSize() > 1;
        if (game.getState() == GameState.LOBBY) {
            sender.sendMessage(i18n(LangText.INFO_LOBBY, game.getParticipants().size(), game.getMaxPlayers(),
                    game.getArena().getMinPlayers()));
        } else {
            sender.sendMessage(i18n(teams ? LangText.INFO_RUNNING_TEAMS : LangText.INFO_RUNNING, game.getAlive().size(),
                    game.getParticipants().size(), game.getAliveTeams().size(), game.getTeams().getTeams().size()));
        }
        if (game instanceof VaroGame varo) {
            if (varo.getState() == GameState.RUNNING) sender.sendMessage(i18n(LangText.INFO_DAY, varo.getDay()));
            sender.sendMessage(i18n(LangText.INFO_RULES_VARO, Game.formatTime(varo.getPlayTime()),
                    game.getArena().getTeamSize()));
        } else {
            sender.sendMessage(i18n(LangText.INFO_RULES_SG, game.getArena().getTeamSize(),
                    game.getArena().settings().getInt("grace-period")));
        }
        if (sender instanceof Player player && game instanceof VaroGame && game.getParticipant(player.getUniqueId()) != null
                && game.getState() == GameState.RUNNING) {
            timeCmd(player);
        }
    }

    @Command(ROOT + " time")
    @CommandDescription("command_time")
    public void timeCmd(Player player) {
        // the Varo the player is in or takes part in
        VaroGame varo = plugin().getGameManager().findGame(player.getUniqueId()) instanceof VaroGame v ? v : null;
        Participant participant = varo == null ? null : varo.getParticipant(player.getUniqueId());
        if (participant == null || varo.getState() != GameState.RUNNING) {
            player.sendMessage(I.prefixed(LangText.NOT_PLAYING));
            return;
        }
        player.sendMessage(I.prefixed(LangText.TIME_LEFT, Game.formatTime(varo.getRemaining(participant)),
                participant.getKills()));
    }

    @Command(ROOT + " ping [player]")
    @CommandDescription("command_ping")
    public void pingCmd(CommandSender sender, @Argument("player") @Nullable Player target) {
        Player player = target != null ? target : sender instanceof Player self ? self : null;
        if (player == null) {
            sender.sendMessage(I.prefixed(LangText.ONLY_PLAYERS));
            return;
        }
        sender.sendMessage(I.prefixed(LangText.PING, player.getName(), player.getPing()));
    }

    @Command(ROOT + " lastseen <player>")
    @CommandDescription("command_lastseen")
    public void lastSeenCmd(CommandSender sender, @Argument("player") String name) {
        if (Bukkit.getPlayerExact(name) != null) {
            sender.sendMessage(I.prefixed(LangText.LAST_SEEN_ONLINE, name));
            return;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(name);
        if (player == null || player.getLastSeen() <= 0) {
            sender.sendMessage(I.prefixed(LangText.PLAYER_NOT_FOUND));
            return;
        }
        String date;
        try {
            date = DateTimeFormatter.ofPattern(String.valueOf(plugin().getFileManager().getConfigFile().getString("date-format")))
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(player.getLastSeen()));
        } catch (IllegalArgumentException ex) {
            date = Instant.ofEpochMilli(player.getLastSeen()).toString();
        }
        sender.sendMessage(I.prefixed(LangText.LAST_SEEN, player.getName(), date));
    }

    // ---------------------------------------------------------------- admins

    @Command(ROOT + " start")
    @CommandDescription("command_start")
    @Permission(ADMIN)
    public void startCmd(Player player, @Flag("force") boolean force) {
        Game game = plugin().getUser(player).getGame();
        if (game == null) {
            player.sendMessage(I.prefixed(LangText.NOT_IN_GAME));
            return;
        }
        CommandGame.start(player, game, force);
    }

    @Command(ROOT + " build")
    @CommandDescription("command_build")
    @Permission(ADMIN)
    public void buildCmd(Player player) {
        boolean building = plugin().getBuildMode().toggle(player);
        player.sendMessage(I.prefixed(building ? LangText.BUILD_ON : LangText.BUILD_OFF));
    }

    @Command(ROOT + " reload")
    @CommandDescription("command_reload")
    @Permission(ADMIN)
    public void reloadCmd(CommandSender sender) {
        plugin().reload();
        sender.sendMessage(I.prefixed(LangText.RELOADED));
    }

    @Command(ROOT + " debug")
    @CommandDescription("command_debug")
    @Permission(ADMIN)
    public void debugCmd(CommandSender sender) {
        sender.sendMessage(DEBUG_HEADER);
        sender.sendMessage(debugLine("Version", plugin().getPluginMeta().getVersion()));
        sender.sendMessage(debugLine("Network", network().getMode().name()
                + (network().isGameServer() ? " (arena " + (network().getArena() == null ? "-" : network().getArena().getName()) + ")" : "")));
        sender.sendMessage(debugLine("Arenas", plugin().getArenaManager().getArenas().size() + ""));
        for (Game game : plugin().getGameManager().getGames()) {
            String state = game.getType().getKey() + ", " + game.getState().name() + ", world "
                    + game.getGameWorld().getWorldName() + ", players " + game.getPlayers().size() + ", spectators "
                    + game.getSpectators().size() + ", participants " + game.getParticipants().size()
                    + (game.getCountdown() >= 0 ? ", countdown " + game.getCountdown() : "");
            if (game instanceof VaroGame varo) state += ", day " + varo.getDay() + " (" + varo.today() + ")";
            if (game instanceof SurvivalGame sg && sg.getState() == GameState.RUNNING) {
                state += ", " + Game.formatTime(sg.getSeconds()) + (sg.isDeathmatch() ? ", deathmatch" : "");
            }
            sender.sendMessage(debugLine("Game " + game.getArena().getName(), state));
            for (GameTeam team : game.getTeams().getTeams()) {
                sender.sendMessage(Component.text("  Team ", NamedTextColor.GRAY).append(team.getDisplayName()));
                for (var member : team.getMembers()) {
                    Participant participant = game.getParticipant(member);
                    String info = participant == null ? "-" : (participant.isAlive() ? "alive" : "out")
                            + ", kills " + participant.getKills() + ", spawn " + (participant.getSpawn() + 1)
                            + (participant.isStarted() ? "" : " (not started)")
                            + (game instanceof VaroGame varo ? ", left today " + Game.formatTime(varo.getRemaining(participant)) : "");
                    sender.sendMessage(debugLine("    " + game.getName(member), info));
                }
            }
        }
    }

    private static Component debugLine(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

}
