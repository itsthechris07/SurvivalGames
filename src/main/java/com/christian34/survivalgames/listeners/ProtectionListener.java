package com.christian34.survivalgames.listeners;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Menus;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.modules.Backpack;
import com.christian34.survivalgames.modules.GravityGrenade;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Location;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Outside of the running game (lobby, countdown, end) and for spectators nothing in the world or the inventory can
 * be changed (admins in /sg build can). In a round of Survival Games only some blocks can be broken and placed, the
 * chests are filled when they are opened. The lobby items open the team menu and leave the game.
 *
 * @author Christian34
 */
public class ProtectionListener implements Listener {
    private final SurvivalGames plugin;

    public ProtectionListener(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    /**
     * @return true if the player can't change anything right now
     */
    private boolean isProtected(Player player) {
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game == null || plugin.getBuildMode().isBuilding(player)) return false;
        return gamePlayer.isSpectator() || game.getState() != GameState.RUNNING;
    }

    private void protect(Player player, Cancellable event) {
        if (isProtected(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        GamePlayer gamePlayer = plugin.getUser(player);
        Game game = gamePlayer.getGame();
        if (game != null && e.getHand() == EquipmentSlot.HAND
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (Items.is(e.getItem(), Items.TEAMS)) {
                e.setCancelled(true);
                Menus.openTeams(player, game);
                return;
            }
            if (Items.is(e.getItem(), Items.LEAVE)) {
                e.setCancelled(true);
                game.leave(gamePlayer, Game.LeaveReason.COMMAND);
                return;
            }
        }
        // trampling farmland, pressure plates, buttons, doors, ...
        if (e.getAction() == Action.PHYSICAL || e.getClickedBlock() != null && isProtected(player)
                && !(e.getClickedBlock().getState(false) instanceof InventoryHolder)) {
            protect(player, e);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (isProtected(player)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.getUser(player).getGame() instanceof SurvivalGame sg && !plugin.getBuildMode().isBuilding(player)
                && !sg.mayBreak(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if (isProtected(player)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.getUser(player).getGame() instanceof SurvivalGame sg && !plugin.getBuildMode().isBuilding(player)
                && !sg.mayPlace(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        protect(e.getPlayer(), e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player player) protect(player, e);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        protect(e.getPlayer(), e);
    }

    /**
     * the lobby items stay where they are
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player && e.getClickedInventory() == player.getInventory()) {
            protect(player, e);
        }
    }

    /**
     * no chests outside of the running game - in a round of Survival Games the chests get their loot
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        // only containers in the world (not the menus)
        InventoryHolder holder = e.getInventory().getHolder();
        Location location = e.getInventory().getLocation();
        if (!(e.getPlayer() instanceof Player player) || location == null
                || !(holder instanceof BlockInventoryHolder || holder instanceof DoubleChest)) return;
        if (isProtected(player)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.getUser(player).getGame() instanceof SurvivalGame sg
                && SurvivalGame.isLootContainer(location.getBlock().getType())) {
            sg.fillContainer(e.getInventory());
        }
    }

    /**
     * the items of the modules can only be crafted in games that use them
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        String module;
        if (Items.is(result, Items.BACKPACK)) {
            module = Backpack.MODULE;
        } else if (Items.is(result, Items.GRENADE)) {
            module = GravityGrenade.MODULE;
        } else {
            return;
        }
        if (!(e.getView().getPlayer() instanceof Player player)
                || !plugin.isModuleEnabled(module, plugin.getUser(player).getGame())) {
            e.getInventory().setResult(null);
        }
    }

}
