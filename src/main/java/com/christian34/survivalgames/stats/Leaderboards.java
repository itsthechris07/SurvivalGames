package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The best players in the world, without other plugins: holograms (text displays) with the top 10 of a statistic
 * (/sg leaderboard create) and signs with one rank ([sgtop] in the first line, the statistic, the rank and the game
 * type in the others) - a player head above the sign (or above the block a wall sign hangs on) shows the player.
 * Updated every minute, saved in leaderboards.yml.
 *
 * @author Christian34
 */
public class Leaderboards implements Listener {
    public static final String SIGN_KEY = "[sgtop]";
    /**
     * /sg leaderboard remove takes the nearest hologram within this distance
     */
    private static final double REMOVE_DISTANCE = 5;
    private static final String MARKER = "leaderboard";
    private final SurvivalGames plugin;
    private final File file;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();
    private final List<TopSign> signs = new ArrayList<>();

    /**
     * the statistic of a leaderboard
     *
     * @param type null: all game types
     */
    public record Board(Stat stat, @Nullable GameType type) {

        public Component getDisplayName() {
            return i18n(LangText.LEADERBOARD_NAME, i18n(stat.getDisplayName()),
                    i18n(type == null ? LangText.TOP_ALL_TYPES : type.getDisplayName()));
        }
    }

    /**
     * a hologram with the top 10
     *
     * @param world  the name of the world (it may be loaded later, e.g. by Multiverse)
     * @param entity the text display, null: not spawned yet
     */
    public record Hologram(String id, String world, double x, double y, double z, Board board, @Nullable UUID entity) {

        /**
         * @return null if the world isn't loaded
         */
        @Nullable
        public Location location() {
            World loaded = Bukkit.getWorld(world);
            return loaded == null ? null : new Location(loaded, x, y, z);
        }

        Hologram withEntity(@Nullable UUID entity) {
            return new Hologram(id, world, x, y, z, board, entity);
        }
    }

    /**
     * a sign with one rank (1: the best player)
     */
    public record TopSign(String world, int x, int y, int z, Board board, int rank) {

        boolean is(Block block) {
            return block.getWorld().getName().equals(world) && block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }

    public Leaderboards(SurvivalGames plugin) {
        this.plugin = plugin;
        this.file = new File(FileManager.getPluginFolder(), "leaderboards.yml");
        load();
        // after the worlds and the statistics are there
        Bukkit.getScheduler().runTaskTimer(plugin, () -> update(), 20L * 5, 20L * 60);
    }

    private void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("holograms");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection hologram = section.getConfigurationSection(id);
                Board board = board(hologram);
                if (hologram == null || board == null) continue;
                String entity = hologram.getString("entity");
                holograms.put(id, new Hologram(id, hologram.getString("world", ""), hologram.getDouble("x"),
                        hologram.getDouble("y"), hologram.getDouble("z"), board, entity == null ? null : UUID.fromString(entity)));
            }
        }
        for (Map<?, ?> sign : data.getMapList("signs")) {
            YamlConfiguration entry = new YamlConfiguration();
            sign.forEach((key, value) -> entry.set(String.valueOf(key), value));
            Board board = board(entry);
            if (board == null) continue;
            signs.add(new TopSign(entry.getString("world", ""), entry.getInt("x"), entry.getInt("y"), entry.getInt("z"),
                    board, Math.max(1, entry.getInt("rank", 1))));
        }
    }

    @Nullable
    private static Board board(@Nullable ConfigurationSection section) {
        if (section == null) return null;
        Stat stat = Stat.parse(section.getString("stat"));
        String type = section.getString("type");
        return stat == null ? null : new Board(stat, type == null ? null : GameType.parse(type));
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Hologram hologram : holograms.values()) {
            String path = "holograms." + hologram.id();
            data.set(path + ".world", hologram.world());
            data.set(path + ".x", hologram.x());
            data.set(path + ".y", hologram.y());
            data.set(path + ".z", hologram.z());
            data.set(path + ".stat", hologram.board().stat().getKey());
            data.set(path + ".type", hologram.board().type() == null ? null : hologram.board().type().getKey());
            data.set(path + ".entity", hologram.entity() == null ? null : hologram.entity().toString());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (TopSign sign : signs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", sign.world());
            entry.put("x", sign.x());
            entry.put("y", sign.y());
            entry.put("z", sign.z());
            entry.put("stat", sign.board().stat().getKey());
            if (sign.board().type() != null) entry.put("type", sign.board().type().getKey());
            entry.put("rank", sign.rank());
            list.add(entry);
        }
        data.set("signs", list);
        try {
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save " + file, ex);
        }
    }

    // ---------------------------------------------------------------- holograms

    /**
     * a hologram with the top 10 above the location
     */
    public Hologram create(Location location, Board board) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Hologram hologram = new Hologram(id, location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), board, null);
        holograms.put(id, hologram);
        save();
        update(board);
        return hologram;
    }

    /**
     * @return the removed hologram, null if there is none near the location
     */
    @Nullable
    public Hologram removeNearest(Location location) {
        Hologram nearest = holograms.values().stream()
                .filter(hologram -> hologram.world().equals(location.getWorld().getName()))
                .filter(hologram -> hologram.location().distance(location) <= REMOVE_DISTANCE)
                .min(Comparator.comparingDouble(hologram -> hologram.location().distance(location))).orElse(null);
        if (nearest == null) return null;
        holograms.remove(nearest.id());
        Entity entity = nearest.entity() == null ? null : Bukkit.getEntity(nearest.entity());
        if (entity != null) entity.remove();
        save();
        return nearest;
    }

    public List<Hologram> getHolograms() {
        return new ArrayList<>(holograms.values());
    }

    public List<TopSign> getSigns() {
        return new ArrayList<>(signs);
    }

    // ---------------------------------------------------------------- update

    /**
     * loads the best players of all leaderboards (every minute)
     */
    public void update() {
        Set<Board> boards = new HashSet<>();
        holograms.values().forEach(hologram -> boards.add(hologram.board()));
        signs.forEach(sign -> boards.add(sign.board()));
        boards.forEach(this::update);
    }

    private void update(Board board) {
        StatsManager stats = plugin.getStats();
        if (!stats.isEnabled()) return;
        stats.then(stats.top(board.stat(), board.type()), top -> show(board, top), () -> {
        });
    }

    private void show(Board board, List<PlayerStats> top) {
        boolean changed = false;
        for (Hologram hologram : List.copyOf(holograms.values())) {
            if (hologram.board().equals(board)) changed |= show(hologram, top);
        }
        for (TopSign sign : signs) {
            if (sign.board().equals(board)) show(sign, top);
        }
        if (changed) save();
    }

    /**
     * @return true if the text display was spawned again (leaderboards.yml needs to be saved)
     */
    private boolean show(Hologram hologram, List<PlayerStats> top) {
        Location location = hologram.location();
        if (location == null) return false;
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if ( !world.isChunkLoaded(chunkX, chunkZ) || !world.getChunkAt(chunkX, chunkZ).isEntitiesLoaded()) {
            return false;
        }
        Stat stat = hologram.board().stat();
        List<Component> lines = new ArrayList<>();
        lines.add(i18n(LangText.LEADERBOARD_TITLE, hologram.board().getDisplayName()));
        for (int rank = 1; rank <= StatsManager.TOP_SIZE; rank++) {
            PlayerStats entry = rank <= top.size() ? top.get(rank - 1) : null;
            lines.add(entry == null ? i18n(LangText.LEADERBOARD_EMPTY_ENTRY, rank)
                    : i18n(LangText.LEADERBOARD_ENTRY, rank, entry.name(), entry.get(stat)));
        }
        Component text = Component.join(JoinConfiguration.newlines(), lines);
        if (hologram.entity() != null && Bukkit.getEntity(hologram.entity()) instanceof TextDisplay display) {
            if (!text.equals(display.text())) display.text(text);
            return false;
        }
        // removed (e.g. /kill) or not spawned yet
        TextDisplay display = world.spawn(location, TextDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setPersistent(true);
            spawned.setLineWidth(250);
            spawned.text(text);
            spawned.getPersistentDataContainer().set(Items.key(MARKER), PersistentDataType.STRING, hologram.id());
        });
        holograms.put(hologram.id(), hologram.withEntity(display.getUniqueId()));
        return true;
    }

    private void show(TopSign topSign, List<PlayerStats> top) {
        World world = Bukkit.getWorld(topSign.world());
        if (world == null || !world.isChunkLoaded(topSign.x() >> 4, topSign.z() >> 4)) return;
        Block block = world.getBlockAt(topSign.x(), topSign.y(), topSign.z());
        if (!(block.getState(false) instanceof org.bukkit.block.Sign sign)) return;
        PlayerStats entry = topSign.rank() <= top.size() ? top.get(topSign.rank() - 1) : null;
        List<Component> lines = getLines(topSign, entry);
        for (int i = 0; i < lines.size(); i++) {
            sign.getSide(Side.FRONT).line(i, lines.get(i));
        }
        sign.update();
        showHead(block, entry);
    }

    private static List<Component> getLines(TopSign sign, @Nullable PlayerStats entry) {
        Board board = sign.board();
        return List.of(i18n(LangText.TOP_SIGN_RANK, sign.rank(), i18n(board.stat().getDisplayName())),
                i18n(LangText.TOP_SIGN_NAME, entry == null ? "---" : entry.name()),
                i18n(LangText.TOP_SIGN_VALUE, entry == null ? "" : String.valueOf(entry.get(board.stat()))),
                i18n(LangText.TOP_SIGN_TYPE, i18n(board.type() == null ? LangText.TOP_ALL_TYPES : board.type().getDisplayName())));
    }

    /**
     * a player head above the sign or above the block a wall sign hangs on shows the player
     */
    private static void showHead(Block signBlock, @Nullable PlayerStats entry) {
        List<Block> candidates = new ArrayList<>();
        candidates.add(signBlock.getRelative(BlockFace.UP));
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            candidates.add(signBlock.getRelative(wallSign.getFacing().getOppositeFace()).getRelative(BlockFace.UP));
        }
        for (Block block : candidates) {
            if (!(block.getState(false) instanceof Skull skull)) continue;
            ResolvableProfile profile = skull.getProfile();
            UUID owner = profile == null ? null : profile.uuid();
            // nobody on the rank yet: the head stays as it is
            if (entry == null || entry.uuid().equals(owner)) return;
            skull.setProfile(ResolvableProfile.resolvableProfile().uuid(entry.uuid()).build());
            skull.update();
            return;
        }
    }

    // ---------------------------------------------------------------- signs

    @Nullable
    private TopSign getSign(Block block) {
        return signs.stream().filter(sign -> sign.is(block)).findFirst().orElse(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        Component first = e.line(0);
        Player player = e.getPlayer();
        if (first == null || !I.plain(first).trim().equalsIgnoreCase(SIGN_KEY)
                || !player.hasPermission(SurvivalGames.ADMIN_PERMISSION)) {
            return;
        }
        String statName = line(e, 1);
        Stat stat = statName.isEmpty() ? Stat.WINS : Stat.parse(statName);
        if (stat == null) {
            player.sendMessage(I.prefixed(player, LangText.UNKNOWN_STAT, statName, String.join(", ", Stat.keys())));
            return;
        }
        int rank;
        try {
            rank = line(e, 2).isEmpty() ? 1 : Integer.parseInt(line(e, 2));
        } catch (NumberFormatException ex) {
            rank = 0;
        }
        if (rank < 1 || rank > StatsManager.TOP_SIZE) {
            player.sendMessage(I.prefixed(player, LangText.TOP_SIGN_INVALID_RANK, line(e, 2), StatsManager.TOP_SIZE));
            return;
        }
        String typeName = line(e, 3);
        GameType type = typeName.isEmpty() ? null : GameType.parse(typeName);
        if (!typeName.isEmpty() && type == null) {
            player.sendMessage(I.prefixed(player, LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys())));
            return;
        }
        Block block = e.getBlock();
        TopSign old = getSign(block);
        if (old != null) signs.remove(old);
        TopSign sign = new TopSign(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                new Board(stat, type), rank);
        signs.add(sign);
        save();
        List<Component> lines = getLines(sign, null);
        for (int i = 0; i < lines.size(); i++) {
            e.line(i, lines.get(i));
        }
        player.sendMessage(I.prefixed(player, LangText.TOP_SIGN_CREATED, rank, sign.board().getDisplayName()));
        // after the sign has been changed
        Bukkit.getScheduler().runTask(plugin, () -> update(sign.board()));
    }

    private static String line(SignChangeEvent e, int index) {
        Component line = e.line(index);
        return line == null ? "" : I.plain(line).trim();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        TopSign sign = getSign(e.getBlock());
        if (sign == null) return;
        // sneaking, so the sign isn't removed by accident
        if (e.getPlayer().hasPermission(SurvivalGames.ADMIN_PERMISSION) && e.getPlayer().isSneaking()) {
            signs.remove(sign);
            save();
            e.getPlayer().sendMessage(I.prefixed(e.getPlayer(), LangText.SIGN_REMOVED));
        } else {
            e.setCancelled(true);
        }
    }

}
