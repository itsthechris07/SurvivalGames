package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.network.ServerStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * Join signs: an admin writes "[sg]" and the name of an arena on a sign. It shows the state of the game and players
 * join it (or watch it while it is running) by clicking the sign. Arenas with signs get a game on start. On the lobby
 * server of a network, signs show a game server instead (see {@link NetworkManager}).
 *
 * @author Christian34
 */
public class SignManager implements Listener {
    /**
     * first line of a new sign
     */
    public static final String SIGN_KEY = "[sg]";
    private final SurvivalGames plugin;
    private final File file;
    private final List<JoinSign> signs = new ArrayList<>();

    /**
     * @param target the arena, on a lobby server of a network the game server
     */
    public record JoinSign(String world, int x, int y, int z, String target) {

        boolean is(Block block) {
            return block.getWorld().getName().equals(world) && block.getX() == x && block.getY() == y && block.getZ() == z;
        }

        String serialize() {
            return world + ";" + x + ";" + y + ";" + z + ";" + target;
        }

        @Nullable
        static JoinSign parse(String text) {
            String[] parts = text.split(";");
            if (parts.length != 5) return null;
            try {
                return new JoinSign(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), parts[4]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    public SignManager(SurvivalGames plugin) {
        this.plugin = plugin;
        this.file = new File(FileManager.getPluginFolder(), "signs.yml");
        for (String line : YamlConfiguration.loadConfiguration(file).getStringList("signs")) {
            JoinSign sign = JoinSign.parse(line);
            if (sign != null) signs.add(sign);
        }
        if (!plugin.getNetworkManager().isLobby()) {
            for (JoinSign sign : signs) {
                Arena arena = plugin.getArenaManager().get(sign.target());
                if (arena != null) ensureGame(arena);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    /**
     * creates the game of the arena if it has none
     */
    public void ensureGame(Arena arena) {
        if (!arena.isPlayable() || plugin.getGameManager().getGame(arena) != null) return;
        try {
            plugin.getGameManager().createGame(arena);
        } catch (RuntimeException ex) {
            Debug.warn("Couldn't create a game for the join sign of arena '" + arena.getName() + "'", ex);
        }
    }

    public List<JoinSign> getSigns() {
        return List.copyOf(signs);
    }

    @Nullable
    public JoinSign getSign(Block block) {
        for (JoinSign sign : signs) {
            if (sign.is(block)) return sign;
        }
        return null;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("signs", signs.stream().map(JoinSign::serialize).toList());
        try {
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save signs.yml: " + ex.getMessage());
        }
    }

    private void updateAll() {
        for (JoinSign sign : signs) {
            World world = Bukkit.getWorld(sign.world());
            if (world == null || !world.isChunkLoaded(sign.x() >> 4, sign.z() >> 4)) continue;
            if (!(world.getBlockAt(sign.x(), sign.y(), sign.z()).getState() instanceof Sign state)) continue;
            List<Component> lines = getLines(sign);
            SignSide side = state.getSide(Side.FRONT);
            boolean changed = false;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).equals(side.line(i))) {
                    side.line(i, lines.get(i));
                    changed = true;
                }
            }
            // updating sends the sign to the players, so only on changes
            if (changed) state.update();
        }
    }

    /**
     * @return the four lines: game type, arena (or server), state, players
     */
    List<Component> getLines(JoinSign sign) {
        String target = sign.target();
        ServerStatus status = getStatus(target);
        Component name = Component.text(target);
        if (!plugin.getNetworkManager().isLobby()) {
            Arena arena = plugin.getArenaManager().get(target);
            if (arena != null) name = arena.getDisplayName();
        }
        GameType type = GameType.parse(status.type());
        return List.of(
                type == null ? i18n(LangText.SIGN_TITLE) : i18n(LangText.SIGN_TYPE, i18n(type.getDisplayName())),
                i18n(LangText.SIGN_NAME, name),
                stateText(status),
                status.state() == ServerStatus.State.OFFLINE ? Component.empty()
                        : i18n(LangText.SIGN_PLAYERS, status.players(), status.maxPlayers()));
    }

    /**
     * @param target the arena, on a lobby server the game server
     * @return the state of its game (for signs and placeholders)
     */
    public ServerStatus getStatus(String target) {
        if (plugin.getNetworkManager().isLobby()) return plugin.getNetworkManager().getStatus(target);
        Arena arena = plugin.getArenaManager().get(target);
        return ServerStatus.of(arena == null ? null : plugin.getGameManager().getGame(arena));
    }

    /**
     * @return the state as on the signs ("Waiting", "Running", "Opens Sat 18:00" for a Varo outside of its play
     * hours - in the lobby players can join it, so it stays "Lobby")
     */
    public static Component stateText(ServerStatus status) {
        if (status.opens() != null && status.isRunning()) {
            return i18n(LangText.SIGN_STATUS_OPENS, VaroGame.formatOpening(status.opens()));
        }
        return i18n(switch (status.state()) {
            case LOBBY -> LangText.SIGN_STATUS_LOBBY;
            case INGAME -> LangText.SIGN_STATUS_INGAME;
            case ENDING -> LangText.SIGN_STATUS_ENDING;
            case OFFLINE -> LangText.SIGN_STATUS_OFFLINE;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (!I.plain(e.line(0) == null ? Component.empty() : e.line(0)).trim().equalsIgnoreCase(SIGN_KEY)
                || !e.getPlayer().hasPermission(SurvivalGames.ADMIN_PERMISSION)) {
            return;
        }
        Component line = e.line(1);
        String target = resolveTarget(e.getPlayer(), line == null ? "" : I.plain(line).trim());
        if (target == null) return;
        Block block = e.getBlock();
        JoinSign old = getSign(block);
        if (old != null) signs.remove(old);
        JoinSign sign = new JoinSign(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), target);
        signs.add(sign);
        save();
        List<Component> lines = getLines(sign);
        for (int i = 0; i < lines.size(); i++) {
            e.line(i, lines.get(i));
        }
        e.getPlayer().sendMessage(I.prefixed(plugin.getNetworkManager().isLobby() ? LangText.SIGN_CREATED_SERVER
                : LangText.SIGN_CREATED, target));
    }

    /**
     * checks the target of a sign (and creates a game for the arena if it has none)
     *
     * @param name the arena, on a lobby server the game server
     * @return the name of the arena or server, null if it doesn't exist (the sender got a message)
     */
    @Nullable
    public String resolveTarget(CommandSender sender, String name) {
        if (plugin.getNetworkManager().isLobby()) {
            NetworkManager.Server server = plugin.getNetworkManager().getServer(name);
            if (server == null) {
                sender.sendMessage(I.prefixed(LangText.SERVER_NOT_FOUND, name));
                return null;
            }
            return server.name();
        }
        Arena arena = plugin.getArenaManager().get(name);
        if (arena == null) {
            sender.sendMessage(I.prefixed(LangText.ARENA_NOT_FOUND, name));
            return null;
        }
        ensureGame(arena);
        return arena.getName();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        JoinSign sign = getSign(e.getClickedBlock());
        if (sign == null) return;
        // no sign editor
        e.setCancelled(true);
        plugin.getTelemetry().joined(Telemetry.Join.SIGN);
        join(e.getPlayer(), sign.target());
    }

    /**
     * the player clicked a join sign or NPC: he joins the game of the arena (or watches it), on a lobby server he is
     * sent to the game server
     *
     * @param target the arena, on a lobby server the game server
     */
    public void join(Player player, String target) {
        if (plugin.getNetworkManager().isLobby()) {
            plugin.getNetworkManager().joinServer(player, target);
            return;
        }
        Arena arena = plugin.getArenaManager().get(target);
        if (arena == null) {
            player.sendMessage(I.prefixed(LangText.ARENA_NOT_FOUND, target));
            return;
        }
        plugin.getGameManager().join(plugin.getUser(player), arena);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        JoinSign sign = getSign(e.getBlock());
        if (sign == null) return;
        // sneaking, so the sign isn't removed by accident
        if (e.getPlayer().hasPermission(SurvivalGames.ADMIN_PERMISSION) && e.getPlayer().isSneaking()) {
            signs.remove(sign);
            save();
            e.getPlayer().sendMessage(I.prefixed(LangText.SIGN_REMOVED));
        } else {
            e.setCancelled(true);
        }
    }

}
