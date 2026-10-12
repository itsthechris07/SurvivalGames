package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The backpack: a crafted item (chest surrounded by leather, diamond below) that opens extra storage - shared by the
 * team or one per player (modules.backpack in config.yml). The contents belong to the game (a Varo saves them).
 *
 * @author Christian34
 */
public class Backpack implements Listener {
    public static final String MODULE = "backpack";
    private final SurvivalGames plugin;
    private final NamespacedKey recipeKey;
    /**
     * the open backpacks: members of a team see the same inventory
     */
    private final Map<Key, Holder> open = new HashMap<>();

    /**
     * a backpack of a game
     *
     * @param key team.&lt;team&gt; or player.&lt;uuid&gt;
     */
    private record Key(Game game, String key) {
    }

    public Backpack(SurvivalGames plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "backpack");
        if (config().getBoolean("modules.backpack.enabled")) {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem());
            recipe.shape("LLL", "LCL", "LDL");
            recipe.setIngredient('L', Material.LEATHER);
            recipe.setIngredient('C', Material.CHEST);
            recipe.setIngredient('D', Material.DIAMOND);
            Bukkit.addRecipe(recipe);
        }
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    public void removeRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public static ItemStack createItem() {
        return Items.create(Material.CHEST, i18n(LangText.ITEM_BACKPACK), I.itemLines(LangText.ITEM_BACKPACK_LORE),
                Items.BACKPACK);
    }

    /**
     * the inventory of a backpack (the holder identifies it)
     */
    private final class Holder implements InventoryHolder {
        private final Key key;
        private final Inventory inventory;

        private Holder(Key key) {
            this.key = key;
            int rows = Math.clamp(config().getInt("modules.backpack.rows"), 1, 6);
            this.inventory = Bukkit.createInventory(this, rows * 9, i18n(LangText.GUI_BACKPACK));
            String saved = key.game().getBackpacks().get(key.key());
            if (saved != null) {
                try {
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(saved));
                    inventory.setContents(Arrays.copyOf(items, inventory.getSize()));
                } catch (RuntimeException ex) {
                    Debug.warn("Couldn't load the backpack '" + key.key() + "'", ex);
                }
            }
        }

        private void save() {
            ItemStack[] contents = Arrays.stream(inventory.getContents())
                    .map(item -> item == null ? ItemStack.empty() : item).toArray(ItemStack[]::new);
            key.game().getBackpacks().put(key.key(),
                    Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * @return the key of the player's backpack in the game
     */
    private static String getKey(Game game, Player player, ConfigFile config) {
        GameTeam team = game.getTeam(player.getUniqueId());
        if (team != null && "team".equalsIgnoreCase(config.getString("modules.backpack.type"))) {
            return "team." + team.getKey();
        }
        return "player." + player.getUniqueId();
    }

    public void open(Game game, Player player) {
        Holder holder = open.computeIfAbsent(new Key(game, getKey(game, player, config())), Holder::new);
        player.openInventory(holder.getInventory());
        plugin.getTelemetry().used(Telemetry.Feature.BACKPACK);
        player.playSound(player, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1f);
    }

    /**
     * the backpack of the team is gone with the team
     */
    public void delete(Game game, GameTeam team) {
        Key key = new Key(game, "team." + team.getKey());
        Holder holder = open.remove(key);
        if (holder != null) close(holder);
        game.getBackpacks().remove(key.key());
    }

    /**
     * closes the backpacks of the game (their contents are kept in the game)
     */
    public void closeAll(Game game) {
        List<Holder> holders = new ArrayList<>(open.values().stream().filter(h -> h.key.game() == game).toList());
        for (Holder holder : holders) {
            holder.save();
            open.remove(holder.key);
            holder.getInventory().close();
        }
    }

    /**
     * closes the backpack for all viewers without keeping its contents
     */
    private static void close(Holder holder) {
        holder.getInventory().clear();
        holder.getInventory().close();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !Items.is(e.getItem(), Items.BACKPACK)) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Game game = plugin.getUser(e.getPlayer()).getGame();
        if (!plugin.isModuleEnabled(MODULE, game) || game.getState() != GameState.RUNNING
                || plugin.getUser(e.getPlayer()).isSpectator()) {
            e.getPlayer().sendMessage(I.prefixed(LangText.BACKPACK_ONLY_INGAME));
            return;
        }
        open(game, e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (Items.is(e.getItemInHand(), Items.BACKPACK)) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        holder.save();
        // the last viewer closes it (the closing player is still a viewer)
        if (e.getInventory().getViewers().size() <= 1) open.remove(holder.key);
    }

    /**
     * no backpack in a backpack
     */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        boolean intoBackpack = e.getClickedInventory() == e.getView().getTopInventory()
                ? Items.is(e.getCursor(), Items.BACKPACK) || hotbarItemIsBackpack(e)
                : e.isShiftClick() && Items.is(e.getCurrentItem(), Items.BACKPACK);
        if (intoBackpack) e.setCancelled(true);
    }

    private static boolean hotbarItemIsBackpack(InventoryClickEvent e) {
        if (e.getHotbarButton() < 0) return false;
        @Nullable ItemStack item = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
        return Items.is(item, Items.BACKPACK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder && Items.is(e.getOldCursor(), Items.BACKPACK)) {
            e.setCancelled(true);
        }
    }

}
