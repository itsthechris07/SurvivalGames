package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kits: admins save the content of their inventory as a kit (/sg kit create), the players choose one in the lobby and
 * get it when the game begins. Kits with a permission can only be chosen with sg.kit.&lt;kit&gt;. Saved in kits.yml.
 *
 * @author Christian34
 */
public class Kits {
    public static final String MODULE = "kits";
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    /**
     * the inventory (36), the armor (4) and the second hand
     */
    private static final int SLOTS = 41;
    private final SurvivalGames plugin;
    private final File file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    /**
     * @param items the inventory (36 slots, the hotbar first), the armor (boots first) and the second hand
     */
    public record Kit(String name, ItemStack icon, ItemStack[] items, boolean permission) {

        public String getPermission() {
            return "sg.kit." + name.toLowerCase(Locale.ROOT);
        }

        public boolean mayUse(Player player) {
            return !permission || player.hasPermission(getPermission()) || player.hasPermission(SurvivalGames.ADMIN_PERMISSION);
        }
    }

    public Kits(SurvivalGames plugin) {
        this.plugin = plugin;
        this.file = new File(FileManager.getPluginFolder(), "kits.yml");
        load();
    }

    private void load() {
        kits.clear();
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("kits");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                ItemStack[] items = Arrays.copyOf(decode(section.getString(key + ".items")), SLOTS);
                ItemStack[] icon = decode(section.getString(key + ".icon"));
                kits.put(key.toLowerCase(Locale.ROOT), new Kit(key, icon.length == 0 || icon[0] == null
                        ? ItemStack.of(Material.CHEST) : icon[0], items, section.getBoolean(key + ".permission")));
            } catch (RuntimeException ex) {
                Debug.warn("Couldn't load the kit '" + key + "'", ex);
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String path = "kits." + kit.name();
            data.set(path + ".icon", encode(new ItemStack[]{kit.icon()}));
            data.set(path + ".permission", kit.permission());
            data.set(path + ".items", encode(kit.items()));
        }
        try {
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save " + file, ex);
        }
    }

    private static String encode(ItemStack[] items) {
        ItemStack[] contents = Arrays.stream(items).map(item -> item == null ? ItemStack.empty() : item)
                .toArray(ItemStack[]::new);
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
    }

    private static ItemStack[] decode(@Nullable String saved) {
        if (saved == null) return new ItemStack[0];
        return Arrays.stream(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(saved)))
                .map(item -> item == null || item.isEmpty() ? null : item).toArray(ItemStack[]::new);
    }

    /**
     * saves the inventory of the player as kit (replaces a kit with the same name), the item in his hand is its icon
     */
    public Kit create(String name, Player player, boolean permission) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] items = new ItemStack[SLOTS];
        System.arraycopy(inventory.getStorageContents(), 0, items, 0, 36);
        System.arraycopy(inventory.getArmorContents(), 0, items, 36, 4);
        items[40] = inventory.getItemInOffHand();
        for (int i = 0; i < items.length; i++) {
            items[i] = items[i] == null || items[i].isEmpty() ? null : items[i].clone();
        }
        ItemStack hand = inventory.getItemInMainHand();
        ItemStack icon = hand.isEmpty() ? ItemStack.of(Material.CHEST) : hand.asOne();
        Kit kit = new Kit(name, icon, items, permission);
        kits.put(name.toLowerCase(Locale.ROOT), kit);
        save();
        return kit;
    }

    /**
     * @return false if there is no such kit
     */
    public boolean delete(String name) {
        if (kits.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        save();
        return true;
    }

    @Nullable
    public Kit get(@Nullable String name) {
        return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Kit> getKits() {
        return new ArrayList<>(kits.values());
    }

    /**
     * @return true if the players of the game choose kits (the module is on and there are kits)
     */
    public boolean isUsed(@Nullable Game game) {
        return game != null && !kits.isEmpty() && plugin.isModuleEnabled(MODULE, game);
    }

    /**
     * the player takes the kit in the game (null: none) - only before the game begins
     */
    public void choose(Player player, Game game, @Nullable Kit kit) {
        Participant participant = game.getParticipant(player.getUniqueId());
        if (participant == null || !isUsed(game)
                || game.getState() != GameState.LOBBY && game.getState() != GameState.STARTING) {
            player.sendMessage(I.prefixed(LangText.KIT_NOT_NOW));
            return;
        }
        if (kit != null && !kit.mayUse(player)) {
            player.sendMessage(I.prefixed(LangText.KIT_NO_PERMISSION, kit.name()));
            return;
        }
        participant.setKit(kit == null ? null : kit.name());
        if (kit != null) plugin.getTelemetry().used(Telemetry.Feature.KIT);
        player.sendMessage(kit == null ? I.prefixed(LangText.KIT_NONE_CHOSEN) : I.prefixed(LangText.KIT_CHOSEN, kit.name()));
    }

    /**
     * @return the kit the participant gets: the one he chose or the default one (modules.kits.default)
     */
    @Nullable
    public Kit getKit(Participant participant) {
        Kit chosen = get(participant.getKit());
        return chosen != null ? chosen : get(plugin.getFileManager().getConfigFile().getString("modules.kits.default"));
    }

    /**
     * the game begins: every player alive in it gets his kit
     */
    public void give(Game game) {
        if (!isUsed(game)) return;
        for (GamePlayer member : game.getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = game.getParticipant(player.getUniqueId());
            if (participant == null || !participant.isAlive()) continue;
            Kit kit = getKit(participant);
            if (kit != null && kit.mayUse(player)) apply(player, kit);
        }
    }

    /**
     * puts the items of the kit into their slots (the items in the inventory are kept, they move to free slots)
     */
    public static void apply(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> moved = new ArrayList<>();
        ItemStack[] items = kit.items();
        // the slots of the kit are those of the player inventory: 36-39 the armor, 40 the second hand
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] == null) continue;
            ItemStack before = inventory.getItem(slot);
            if (before != null && !before.isEmpty()) moved.add(before);
            inventory.setItem(slot, items[slot].clone());
        }
        inventory.addItem(moved.toArray(ItemStack[]::new)).values()
                .forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
    }

}
