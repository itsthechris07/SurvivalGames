package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.UpdateChecker;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.AdminMenus;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.Menus;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.PlayHours;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.network.ServerStatus;
import com.christian34.survivalgames.stats.PlayerStats;
import com.christian34.survivalgames.stats.Stat;
import com.christian34.survivalgames.stats.StatsManager;
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
 * seen, statistics, strikes and the admin commands start, build, reload and debug.
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
        if (sender instanceof Player player) {
            Menus.openMain(player);
        } else {
            sender.sendMessage(I.prefixed(LangText.PLUGIN_INFO, plugin().getPluginMeta().getVersion()));
        }
    }

    @Command(ROOT + " games")
    @CommandDescription("command_games")
    public void gamesCmd(Player player) {
        Menus.openGames(player);
    }

    @Command(ROOT + " admin")
    @CommandDescription("command_admin")
    @Permission(ADMIN)
    public void adminCmd(Player player) {
        AdminMenus.open(player);
    }

    @Command(ROOT + " help [query]")
    @CommandDescription("command_help")
    public void helpCmd(CommandSender sender, @Argument("query") @Greedy @Nullable String query) {
        // cloud looks for whole commands ("sg arena"): /sg help arena means the same
        if (query != null && !query.isBlank() && !List.of(ROOT.split("\\|")).contains(query.split(" ")[0])) {
            query = "sg " + query;
        }
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
        plugin().getTelemetry().joined(Telemetry.Join.COMMAND);
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
        if (gamePlayer.getGame() != null && gamePlayer.isSpectator()) {
            // spectators choose whom to watch (Bedrock players can't do that in the spectator mode)
            Menus.openSpectate(player, gamePlayer.getGame());
            return;
        }
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
        if (plugin().getArenaManager().isEditing(player)) {
            plugin().getArenaManager().stopEditing(gamePlayer);
            player.sendMessage(I.prefixed(LangText.EDIT_STOPPED));
            return;
        }
        if (plugin().getReplays().leave(player)) {
            player.sendMessage(I.prefixed(LangText.REPLAY_LEFT));
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
        PlayHours hours = varo.getPlayHours();
        if (hours.isRestricted()) {
            player.sendMessage(I.prefixed(LangText.VARO_PLAY_HOURS, String.join(", ", hours.getEntries())));
        }
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
        sender.sendMessage(I.prefixed(LangText.LAST_SEEN, player.getName(), formatDate(player.getLastSeen())));
    }

    /**
     * @param time milliseconds since 1970
     * @return the date in the date-format of config.yml
     */
    public static String formatDate(long time) {
        try {
            return DateTimeFormatter.ofPattern(String.valueOf(plugin().getFileManager().getConfigFile().getString("date-format")))
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time));
        } catch (IllegalArgumentException ex) {
            return Instant.ofEpochMilli(time).toString();
        }
    }

    // ---------------------------------------------------------------- statistics

    private static StatsManager stats() {
        return plugin().getStats();
    }

    /**
     * @return false if the statistics are turned off (the sender got a message)
     */
    private static boolean checkStats(CommandSender sender) {
        if (stats().isEnabled()) return true;
        sender.sendMessage(I.prefixed(LangText.STATS_DISABLED));
        return false;
    }

    @Suggestions("online-players")
    public List<String> onlinePlayers(CommandContext<CommandSender> context, String input) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /**
     * the players alive in the game of the sender (bounties, sponsors)
     */
    @Suggestions("alive-players")
    public List<String> alivePlayers(CommandContext<CommandSender> context, String input) {
        if (!(context.sender() instanceof Player player)) return List.of();
        Game game = plugin().getGameManager().findGame(player.getUniqueId());
        if (game == null) return List.of();
        return game.getAlive().stream().map(Participant::getName).toList();
    }

    @Command(ROOT + " stats [player]")
    @CommandDescription("command_stats")
    public void statsCmd(CommandSender sender, @Argument(value = "player", suggestions = "online-players") @Nullable String name) {
        if (name == null && !(sender instanceof Player)) {
            sender.sendMessage(I.prefixed(LangText.ONLY_PLAYERS));
            return;
        }
        if (!checkStats(sender)) return;
        String player = name != null ? name : sender.getName();
        stats().then(stats().load(player), stats -> {
            if (stats.isEmpty()) {
                sender.sendMessage(I.prefixed(LangText.STATS_NONE, player));
                return;
            }
            PlayerStats any = stats.values().iterator().next();
            sender.sendMessage(i18n(LangText.STATS_TITLE, any.name()));
            for (GameType type : GameType.values()) {
                PlayerStats typeStats = stats.get(type.getKey());
                if (typeStats == null) continue;
                sender.sendMessage(statsLine(i18n(type.getDisplayName()), typeStats));
                if (stats().isRated(type)) sender.sendMessage(i18n(LangText.STATS_RATING, typeStats.get(Stat.RATING)));
            }
            if (stats.size() > 1) {
                sender.sendMessage(statsLine(i18n(LangText.STATS_TOTAL),
                        PlayerStats.total(any.uuid(), any.name(), stats.values())));
            }
        }, () -> sender.sendMessage(I.prefixed(LangText.STATS_ERROR)));
    }

    @Command(ROOT + " achievements [player]")
    @CommandDescription("command_achievements")
    public void achievementsCmd(CommandSender sender, @Argument(value = "player", suggestions = "online-players") @Nullable String name) {
        if (name == null && !(sender instanceof Player)) {
            sender.sendMessage(I.prefixed(LangText.ONLY_PLAYERS));
            return;
        }
        if (!checkStats(sender)) return;
        if (!plugin().getAchievements().isEnabled()) {
            sender.sendMessage(I.prefixed(LangText.ACHIEVEMENTS_DISABLED));
            return;
        }
        String player = name != null ? name : sender.getName();
        stats().then(stats().loadAchievements(player), unlocked -> {
            if (name == null && sender instanceof Player viewer) {
                if (viewer.isOnline()) plugin().getAchievements().showMenu(viewer, unlocked);
            } else {
                plugin().getAchievements().sendList(sender, player, unlocked);
            }
        }, () -> sender.sendMessage(I.prefixed(LangText.STATS_ERROR)));
    }

    private static Component statsLine(Component type, PlayerStats stats) {
        return i18n(LangText.STATS_ENTRY, type, stats.games(), stats.wins(), stats.kills(), stats.deaths(),
                stats.formatKillDeathRatio());
    }

    @Suggestions("stats")
    public List<String> statNames(CommandContext<CommandSender> context, String input) {
        return Stat.keys();
    }

    /**
     * also used by CommandArena
     */
    @Suggestions("game-types")
    public List<String> gameTypes(CommandContext<CommandSender> context, String input) {
        return GameType.keys();
    }

    @Command(ROOT + " top [stat] [type]")
    @CommandDescription("command_top")
    public void topCmd(CommandSender sender, @Argument(value = "stat", suggestions = "stats") @Nullable String statName,
                       @Argument(value = "type", suggestions = "game-types") @Nullable String typeName) {
        Stat stat = statName == null ? Stat.WINS : Stat.parse(statName);
        if (stat == null) {
            sender.sendMessage(I.prefixed(LangText.UNKNOWN_STAT, statName, String.join(", ", Stat.keys())));
            return;
        }
        GameType type = typeName == null ? null : GameType.parse(typeName);
        if (typeName != null && type == null) {
            sender.sendMessage(I.prefixed(LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys())));
            return;
        }
        if (!checkStats(sender)) return;
        Component typeText = i18n(type == null ? LangText.TOP_ALL_TYPES : type.getDisplayName());
        stats().then(stats().top(stat, type), top -> {
            if (top.isEmpty()) {
                sender.sendMessage(I.prefixed(LangText.TOP_EMPTY, i18n(stat.getDisplayName())));
                return;
            }
            sender.sendMessage(i18n(LangText.TOP_TITLE, i18n(stat.getDisplayName()), typeText));
            for (int i = 0; i < top.size(); i++) {
                sender.sendMessage(i18n(LangText.TOP_ENTRY, i + 1, top.get(i).name(), top.get(i).get(stat)));
            }
        }, () -> sender.sendMessage(I.prefixed(LangText.STATS_ERROR)));
    }

    @Command(ROOT + " inventory <id>")
    @CommandDescription("command_inventory")
    public void inventoryCmd(Player player, @Argument("id") String id) {
        if (!plugin().getInventoryView().open(player, id)) {
            player.sendMessage(I.prefixed(LangText.INVENTORY_VIEW_EXPIRED));
        }
    }

    @Command(ROOT + " vote [arena]")
    @CommandDescription("command_vote")
    public void voteCmd(Player player, @Argument("arena") @Nullable Arena arena) {
        if (!(plugin().getUser(player).getGame() instanceof SurvivalGame game) || game.getMapVoting() == null) {
            player.sendMessage(I.prefixed(LangText.MAP_VOTE_NONE));
        } else if (arena == null) {
            Menus.openVoting(player, game);
        } else {
            game.voteMap(player, arena);
        }
    }

    @Command(ROOT + " sponsor [player]")
    @CommandDescription("command_sponsor")
    public void sponsorCmd(Player player, @Argument(value = "player", suggestions = "alive-players") @Nullable String name) {
        Game game = plugin().getUser(player).getGame();
        LangText denial = plugin().getSponsor().getDenial(player, game);
        if (denial != null) {
            player.sendMessage(I.prefixed(denial));
            return;
        }
        if (name == null) {
            Menus.openSponsor(player, game);
            return;
        }
        Participant target = game.findParticipant(name);
        if (target == null || !target.isAlive()) {
            player.sendMessage(I.prefixed(LangText.SPONSOR_INVALID_TARGET, name));
        } else {
            Menus.openSponsorItems(player, game, target);
        }
    }

    @Command(ROOT + " strikes")
    @CommandDescription("command_strikes")
    public void strikesCmd(Player player) {
        Game game = plugin().getGameManager().findGame(player.getUniqueId());
        Participant participant = game instanceof VaroGame ? game.getParticipant(player.getUniqueId()) : null;
        if (participant == null) {
            player.sendMessage(I.prefixed(LangText.NOT_PLAYING));
            return;
        }
        showStrikes(player, participant);
    }

    /**
     * the strikes of the participant with reason, admin and date
     */
    static void showStrikes(CommandSender sender, Participant participant) {
        List<Participant.Strike> strikes = participant.getStrikes();
        if (strikes.isEmpty()) {
            sender.sendMessage(I.prefixed(LangText.STRIKES_NONE, participant.getName()));
            return;
        }
        sender.sendMessage(i18n(LangText.STRIKES_TITLE, participant.getName(), strikes.size()));
        for (int i = 0; i < strikes.size(); i++) {
            Participant.Strike strike = strikes.get(i);
            sender.sendMessage(i18n(LangText.STRIKES_ENTRY, i + 1, strike.reason(), strike.by(),
                    formatDate(strike.time())));
        }
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
        UpdateChecker.Update update = plugin().getUpdateChecker().getUpdate();
        sender.sendMessage(debugLine("Latest version",
                update == null ? "installed (or not checked)" : update.version() + " (" + update.behind() + " behind)"));
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
