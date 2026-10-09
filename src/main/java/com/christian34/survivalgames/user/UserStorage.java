package com.christian34.survivalgames.user;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.network.NetworkManager;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The state of a player outside of the plugin (inventory, health, experience, effects, location, attributes, ...):
 * saved before a game (or editing a map) and restored afterwards, so the game has no effect on the real game.
 * <p>
 * The backup is also written to backups/&lt;uuid&gt;.yml (except on a game server of a network, where players only
 * play) and restored when the player joins after a crash of the server.
 */
public class UserStorage {
    private static final String BACKUP_FOLDER = "backups";
    private final Player player;
    /**
     * the saved state, null if nothing is saved
     */
    private YamlConfiguration backup;

    protected UserStorage(Player player) {
        this.player = player;
    }

    /**
     * saves the state of the player (again while saved: the first state is kept, it is the real one)
     */
    public void store() {
        if (backup != null) return;
        // items on the cursor or in the crafting grid go back into the inventory
        player.closeInventory();
        this.backup = snapshot(player);
        writeBackupFile();
    }

    /**
     * @return true if a state is saved (the player is in a game or edits a map)
     */
    public boolean isStored() {
        return backup != null;
    }

    public void restore() {
        if (backup == null) return;
        try {
            apply(player, backup, true);
        } catch (RuntimeException ex) {
            // keep the file, so the items can still be recovered
            Debug.warn("Couldn't restore the state of " + player.getName(), ex);
            this.backup = null;
            return;
        }
        this.backup = null;
        deleteBackupFile(player);
    }

    public void cleanPlayer() {
        PlayerInventory inv = this.player.getInventory();
        inv.clear();

        this.player.setAllowFlight(false);
        this.player.setFlying(false);
        this.player.setExp(0.0F);
        this.player.setLevel(0);
        this.player.setSneaking(false);
        this.player.setSprinting(false);
        this.player.setFoodLevel(20);
        this.player.setSaturation(10);
        this.player.setExhaustion(0);
        setMaxHealth(this.player, 20.0D);
        this.player.setHealth(20.0D);
        this.player.setAbsorptionAmount(0);
        this.player.setFireTicks(0);
        this.player.setFreezeTicks(0);
        this.player.setFallDistance(0);
        this.player.setRemainingAir(this.player.getMaximumAir());

        if (this.player.isInsideVehicle()) {
            this.player.leaveVehicle();
        }

        for (PotionEffect e : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(e.getType());
        }

        this.player.updateInventory();
    }

    // --- backup file (server crash)

    private static File backupFile(Player player) {
        return new File(new File(FileManager.getPluginFolder(), BACKUP_FOLDER), player.getUniqueId() + ".yml");
    }

    /**
     * on a game server of a network players only play, there is nothing to restore
     */
    private static boolean usesBackupFiles() {
        NetworkManager network = SurvivalGames.getInstance().getNetworkManager();
        return network == null || !network.isGameServer();
    }

    private void writeBackupFile() {
        if (!usesBackupFiles()) return;
        File file = backupFile(player);
        if (file.isFile()) {
            // an old backup that couldn't be restored: never overwrite real items
            File old = new File(file.getParentFile(), player.getUniqueId() + "-" + System.currentTimeMillis() + ".yml");
            Debug.warn("Keeping the old inventory backup of " + player.getName() + " as " + old.getName());
            if (!file.renameTo(old)) Debug.warn("Couldn't rename " + file.getName());
        }
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);
            backup.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save the inventory backup of " + player.getName() + " (lost if the server crashes): " + ex.getMessage());
        }
    }

    private static void deleteBackupFile(Player player) {
        File file = backupFile(player);
        if (file.isFile() && !file.delete()) Debug.warn("Couldn't delete the inventory backup " + file);
    }

    /**
     * restores the state saved before a crash of the server (the player was in a game or edited a map)
     *
     * @return true if there was a backup and it has been restored
     */
    public static boolean restoreBackupFile(@NotNull Player player) {
        File file = backupFile(player);
        if (!file.isFile()) return false;
        YamlConfiguration backup = new YamlConfiguration();
        try {
            backup.load(file);
            // the names are set by other plugins again, the saved ones may be outdated
            apply(player, backup, false);
        } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
            Debug.warn("Couldn't restore the inventory backup of " + player.getName() + " (" + file + "): " + ex);
            return false;
        }
        deleteBackupFile(player);
        Debug.info("Restored the inventory of " + player.getName() + " from before the server crash");
        return true;
    }

    // --- snapshot

    /**
     * @return the whole state of the player (also used for the state of a Varo player while he is offline)
     */
    public static YamlConfiguration snapshot(Player player) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("name", player.getName());
        data.set("inventory", items(player.getInventory().getContents()));
        data.set("ender-chest", items(player.getEnderChest().getContents()));
        setLocation(data, "location", player.getLocation());
        setLocation(data, "respawn-location", player.getRespawnLocation());
        setLocation(data, "last-death-location", player.getLastDeathLocation());
        setLocation(data, "compass-target", player.getCompassTarget());
        data.set("game-mode", player.getGameMode().name());
        data.set("allow-flight", player.getAllowFlight());
        data.set("flying", player.isFlying());
        data.set("walk-speed", player.getWalkSpeed());
        data.set("fly-speed", player.getFlySpeed());
        ConfigurationSection attributes = data.createSection("attributes");
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) attributes.set(attribute.getKey().toString(), instance.getBaseValue());
        }
        data.set("health", player.getHealth());
        data.set("absorption", player.getAbsorptionAmount());
        data.set("food", player.getFoodLevel());
        data.set("saturation", player.getSaturation());
        data.set("exhaustion", player.getExhaustion());
        data.set("fire-ticks", player.getFireTicks());
        data.set("freeze-ticks", player.getFreezeTicks());
        data.set("air", player.getRemainingAir());
        data.set("fall-distance", player.getFallDistance());
        data.set("level", player.getLevel());
        data.set("exp", player.getExp());
        data.set("total-experience", player.getTotalExperience());
        data.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        data.set("display-name", GsonComponentSerializer.gson().serialize(player.displayName()));
        data.set("list-name", GsonComponentSerializer.gson().serialize(player.playerListName()));
        return data;
    }

    /**
     * gives the player the saved state
     *
     * @param names true: also the display and tab list name
     */
    public static void apply(Player player, ConfigurationSection data, boolean names) {
        player.closeInventory();
        GameMode gameMode = GameMode.valueOf(data.getString("game-mode", GameMode.SURVIVAL.name()));
        player.setGameMode(gameMode);
        player.getInventory().setContents(items(data.getString("inventory"), player.getInventory().getSize()));
        player.getEnderChest().setContents(items(data.getString("ender-chest"), player.getEnderChest().getSize()));

        ConfigurationSection attributes = data.getConfigurationSection("attributes");
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            String key = attribute.getKey().toString();
            // attributes the player didn't have before get their default value
            instance.setBaseValue(attributes != null && attributes.contains(key) ? attributes.getDouble(key)
                    : attribute.getDefaultValue());
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = data.getDouble("health", 20);
        player.setHealth(Math.max(0.5, Math.min(health, maxHealth == null ? 20 : maxHealth.getValue())));
        player.setAbsorptionAmount(data.getDouble("absorption"));
        player.setFoodLevel(data.getInt("food", 20));
        player.setSaturation((float) data.getDouble("saturation", 5));
        player.setExhaustion((float) data.getDouble("exhaustion"));
        player.setFireTicks(data.getInt("fire-ticks"));
        player.setFreezeTicks(data.getInt("freeze-ticks"));
        player.setRemainingAir(data.getInt("air", player.getMaximumAir()));
        player.setFallDistance((float) data.getDouble("fall-distance"));
        player.setTotalExperience(data.getInt("total-experience"));
        player.setLevel(data.getInt("level"));
        player.setExp((float) data.getDouble("exp"));

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        List<?> effects = data.getList("effects", List.of());
        for (Object effect : effects) {
            if (effect instanceof PotionEffect potionEffect) player.addPotionEffect(potionEffect);
        }

        player.setAllowFlight(data.getBoolean("allow-flight") || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        player.setWalkSpeed((float) data.getDouble("walk-speed", 0.2));
        player.setFlySpeed((float) data.getDouble("fly-speed", 0.1));
        if (names) {
            player.displayName(GsonComponentSerializer.gson().deserializeOrNull(data.getString("display-name")));
            player.playerListName(GsonComponentSerializer.gson().deserializeOrNull(data.getString("list-name")));
        }
        player.setRespawnLocation(getLocation(data, "respawn-location"), true);
        player.setLastDeathLocation(getLocation(data, "last-death-location"));
        Location compassTarget = getLocation(data, "compass-target");
        if (compassTarget != null) player.setCompassTarget(compassTarget);
        player.updateInventory();
        Location location = getLocation(data, "location");
        if (location != null) player.teleport(location);
        // after the teleport: changing the world may reset it
        if (player.getAllowFlight()) player.setFlying(data.getBoolean("flying"));
    }

    private static void setMaxHealth(Player player, double health) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(health);
        if (player.getHealth() > health) player.setHealth(health);
    }

    private static String items(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? ItemStack.empty() : items[i];
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(copy));
    }

    private static ItemStack[] items(@Nullable String data, int size) {
        ItemStack[] contents = new ItemStack[size];
        if (data == null || data.isEmpty()) return contents;
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
        for (int i = 0; i < Math.min(size, items.length); i++) {
            // empty slots are null (an air stack counts as an occupied slot for some implementations)
            contents[i] = items[i] == null || items[i].isEmpty() ? null : items[i];
        }
        return contents;
    }

    private static void setLocation(ConfigurationSection data, String path, @Nullable Location location) {
        // e.g. a respawn location in an unloaded world
        if (location == null || !location.isWorldLoaded()) return;
        ConfigurationSection section = data.createSection(path);
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    /**
     * @return the location, null if there is none or its world doesn't exist (any more)
     */
    @Nullable
    private static Location getLocation(ConfigurationSection data, String path) {
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

}
