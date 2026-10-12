package com.christian34.survivalgames.network;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.integrations.VanishSupport;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network mode (network.* in config.yml) for servers behind a Velocity (or BungeeCord) proxy:
 * <ul>
 *     <li>standalone: the games run on this server, players join them with /sg join or join signs</li>
 *     <li>game: this server hosts the game of one arena. Players come from the lobby server and join it right away
 *     (or watch it), afterwards they are sent back to the lobby server. Players who can't play (full lobby, out of
 *     the Varo, no play time left) are turned away before they connect. The state of the game is the MOTD
 *     ({@link ServerStatus}).</li>
 *     <li>lobby: no games - join signs and /sg join send players to the game servers, whose MOTD is read every 2
 *     seconds.</li>
 * </ul>
 * Players are sent with the "Connect" message of the BungeeCord channel, which Velocity understands as well.
 *
 * @author Christian34
 */
public class NetworkManager implements Listener {
    public static final String CHANNEL = "BungeeCord";
    /**
     * players with this permission aren't put into the game when they join a game server (e.g. to set up the arena)
     */
    public static final String BYPASS_PERMISSION = "sg.network.bypass";
    private static final int PING_TIMEOUT_MILLIS = 1000;
    private final SurvivalGames plugin;
    private final Map<String, ServerStatus> statuses = new ConcurrentHashMap<>();

    public enum Mode {
        STANDALONE, GAME, LOBBY
    }

    /**
     * a game server of the network
     *
     * @param name the name in the proxy config
     * @param host the address the lobby server pings
     * @param port the port of the server
     */
    public record Server(String name, String host, int port) {
    }

    public NetworkManager(SurvivalGames plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pingAll, 20L, 40L);
    }

    @NotNull
    public Mode getMode() {
        String mode = plugin.getFileManager().getConfigFile().getString("network.mode");
        try {
            return mode == null ? Mode.STANDALONE : Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Mode.STANDALONE;
        }
    }

    public boolean isGameServer() {
        return getMode() == Mode.GAME;
    }

    public boolean isLobby() {
        return getMode() == Mode.LOBBY;
    }

    @Nullable
    private String getLobbyServer() {
        String lobby = plugin.getFileManager().getConfigFile().getString("network.lobby-server");
        return lobby == null || lobby.isBlank() ? null : lobby.trim();
    }

    /**
     * sends the player to another server of the network
     */
    public void connect(@NotNull Player player, @NotNull String server) {
        // plugin messages can't be sent while the plugin is disabled (shutdown: the proxy moves the players itself)
        if (!plugin.isEnabled()) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendMessage(I.prefixed(LangText.NETWORK_CONNECTING, server));
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /**
     * on a game server: sends the player to the lobby server (after the game or when he leaves it)
     */
    public void sendToLobby(@NotNull Player player) {
        String lobby = getLobbyServer();
        if (!isGameServer() || !player.isOnline() || lobby == null) return;
        connect(player, lobby);
    }

    /**
     * the player has to leave the game: he gets the message, on a game server he is sent to the lobby server (kicked
     * with the message if there is none)
     */
    public void sendAway(@NotNull Player player, @NotNull Component message) {
        if (!player.isOnline()) return;
        if (isGameServer() && getLobbyServer() == null) {
            player.kick(message);
            return;
        }
        player.sendMessage(message);
        sendToLobby(player);
    }

    // ---------------------------------------------------------------- game server

    /**
     * @return the arena of this game server: network.arena, otherwise the first playable arena
     */
    @Nullable
    public Arena getArena() {
        String name = plugin.getFileManager().getConfigFile().getString("network.arena");
        if (name != null && !name.isBlank()) return plugin.getArenaManager().get(name.trim());
        return plugin.getArenaManager().getArenas().stream().filter(Arena::isPlayable).findFirst().orElse(null);
    }

    /**
     * @return the game of this game server, null if there is none (or this isn't a game server)
     */
    @Nullable
    public Game getGame() {
        Arena arena = isGameServer() ? getArena() : null;
        return arena == null ? null : plugin.getGameManager().getGame(arena);
    }

    /**
     * on a game server: creates the game of the arena if there is none
     */
    public void ensureGame() {
        if (!isGameServer()) return;
        Arena arena = getArena();
        if (arena == null || !arena.isPlayable()) {
            Debug.warn("network.mode is 'game', but there is no playable arena (network.arena)!");
            return;
        }
        if (plugin.getGameManager().getGame(arena) != null) return;
        try {
            plugin.getGameManager().createGame(arena);
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't create the game of arena '" + arena.getName() + "'", ex);
        }
    }

    /**
     * players who can neither play nor watch don't get onto a game server (the proxy keeps them on the lobby server
     * with the reason)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        Game game = getGame();
        if (game == null || game.maySpectate(e.getUniqueId())) return;
        Component denial = game.getJoinDenial(e.getUniqueId());
        if (denial == null) return;
        OfflinePlayer player = Bukkit.getOfflinePlayer(e.getUniqueId());
        // admins can always get onto the server to look after it
        if (player.isOp()) return;
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, I.prefixed(denial));
    }

    /**
     * players joining a game server join the game (or watch it)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (!isGameServer() || player.hasPermission(BYPASS_PERMISSION)) return;
        // after the join is done, otherwise the teleport doesn't work
        Bukkit.getScheduler().runTask(plugin, () -> joinGame(player));
    }

    private void joinGame(Player player) {
        // vanished players (admins checking the server) stay outside of the game
        if (!player.isOnline() || VanishSupport.isVanished(player)) return;
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = getGame();
        // without a game (arena not set up yet) the player stays
        if (gamePlayer.getGame() != null || game == null) return;
        Component denial = game.getJoinDenial(player.getUniqueId());
        if (denial == null) {
            plugin.getTelemetry().joined(Telemetry.Join.NETWORK);
            game.join(gamePlayer);
        } else if (game.maySpectate(player.getUniqueId())) {
            game.spectate(gamePlayer);
        } else {
            sendAway(player, I.prefixed(denial));
        }
    }

    /**
     * @return the status of the game on this server
     */
    public ServerStatus getStatus() {
        return ServerStatus.of(getGame());
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!isGameServer()) return;
        ServerStatus status = getStatus();
        e.motd(Component.text(status.toMotd()));
        if (status.maxPlayers() > 0) e.setMaxPlayers(status.maxPlayers());
    }

    // ---------------------------------------------------------------- lobby server

    /**
     * @return the game servers of network.servers
     */
    public List<Server> getServers() {
        List<Server> servers = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getFileManager().getConfigFile().getData().getMapList("network.servers")) {
            Object name = entry.get("name");
            Object address = entry.get("address");
            if (name == null || address == null) continue;
            String[] parts = address.toString().trim().split(":");
            try {
                servers.add(new Server(name.toString(), parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 25565));
            } catch (NumberFormatException ex) {
                Debug.warn("Invalid address of server '" + name + "' in network.servers: " + address);
            }
        }
        return servers;
    }

    @Nullable
    public Server getServer(@NotNull String name) {
        for (Server server : getServers()) {
            if (server.name().equalsIgnoreCase(name)) return server;
        }
        return null;
    }

    /**
     * on the lobby server: reads the status of all game servers (not in the main thread, it waits for the answers)
     */
    public void pingAll() {
        if (!isLobby()) return;
        for (Server server : getServers()) {
            statuses.put(server.name().toLowerCase(Locale.ROOT),
                    ServerStatus.parse(ServerPinger.ping(server.host(), server.port(), PING_TIMEOUT_MILLIS)));
        }
    }

    /**
     * @return the last known status of the game server
     */
    @NotNull
    public ServerStatus getStatus(@NotNull String server) {
        return statuses.getOrDefault(server.toLowerCase(Locale.ROOT), ServerStatus.OFFLINE);
    }

    /**
     * only for the tests: the status the lobby server knows of a game server
     */
    public void setStatus(@NotNull String server, @NotNull ServerStatus status) {
        statuses.put(server.toLowerCase(Locale.ROOT), status);
    }

    /**
     * on the lobby server: sends the player to the game server with the most players waiting (preferably with room
     * for his party, the proxy plugin of the party takes the members along), otherwise to a running game
     *
     * @param spectate only running games
     */
    public void joinAnyServer(@NotNull Player player, boolean spectate) {
        Comparator<Server> mostPlayers = Comparator.comparingInt(server -> getStatus(server.name()).players());
        int party = spectate ? 1 : plugin.getPartySupport().getPartySize(player);
        Server server = spectate ? null : getServers().stream()
                .filter(s -> getStatus(s.name()).isJoinable())
                .filter(s -> getStatus(s.name()).players() + party <= getStatus(s.name()).maxPlayers())
                .max(mostPlayers)
                .orElse(null);
        if (server == null && !spectate) {
            server = getServers().stream()
                    .filter(s -> getStatus(s.name()).isJoinable())
                    .max(mostPlayers)
                    .orElse(null);
        }
        if (server == null) {
            server = getServers().stream()
                    .filter(s -> getStatus(s.name()).isRunning())
                    .max(mostPlayers)
                    .orElse(null);
        }
        if (server == null) {
            player.sendMessage(I.prefixed(spectate ? LangText.NO_GAME_TO_SPECTATE : LangText.NO_GAME_TO_JOIN));
            return;
        }
        connect(player, server.name());
    }

    /**
     * on the lobby server: sends the player to the game server if its game is waiting or running (whether he may
     * play there is checked by the game server)
     */
    public void joinServer(@NotNull Player player, @NotNull String name) {
        Server server = getServer(name);
        ServerStatus status = getStatus(name);
        if (server == null || !(status.isJoinable() || status.isRunning())) {
            player.sendMessage(I.prefixed(LangText.SERVER_NOT_AVAILABLE, name));
            return;
        }
        connect(player, server.name());
    }

}
