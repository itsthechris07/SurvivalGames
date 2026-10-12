package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Locks containers for a team: a sign with [Lock] in the first line on a chest (barrel, furnace, ...). Only the
 * team can open or break the container and the sign; explosions and hoppers can't take anything out. The team is
 * stored in the sign (persistent data), the sign is waxed so nobody can edit it.
 *
 * @author Christian34
 */
public class ChestLock implements Listener {
    public static final String MODULE = "chest-lock";
    private static final BlockFace[] SIGN_FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
            BlockFace.UP};
    private final SurvivalGames plugin;
    private final NamespacedKey lockKey;

    public ChestLock(SurvivalGames plugin) {
        this.plugin = plugin;
        this.lockKey = Items.key("lock");
    }

    /**
     * @return the game in the world if it uses the module
     */
    @Nullable
    private Game getGame(Block block) {
        Game game = plugin.getGameManager().getGame(block.getWorld());
        return plugin.isModuleEnabled(MODULE, game) ? game : null;
    }

    /**
     * @return the block the sign hangs on (wall sign) or stands on
     */
    @Nullable
    private static Block getAttachedBlock(Block sign) {
        BlockData data = sign.getBlockData();
        if (data instanceof WallSign wallSign) return sign.getRelative(wallSign.getFacing().getOppositeFace());
        if (data instanceof org.bukkit.block.data.type.Sign) return sign.getRelative(BlockFace.DOWN);
        return null;
    }

    /**
     * @return the team key of the lock sign, null if the block isn't one
     */
    @Nullable
    private String getLockTeam(Block block) {
        if (!(block.getState(false) instanceof Sign sign)) return null;
        return sign.getPersistentDataContainer().get(lockKey, PersistentDataType.STRING);
    }

    /**
     * @return the blocks of the container (both halves of a double chest)
     */
    private static List<Block> getParts(Block container) {
        List<Block> parts = new ArrayList<>();
        parts.add(container);
        if (container.getState(false) instanceof Chest chest
                && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            for (InventoryHolder side : new InventoryHolder[]{doubleChest.getLeftSide(), doubleChest.getRightSide()}) {
                if (side instanceof Chest half && !half.getBlock().equals(container)) parts.add(half.getBlock());
            }
        }
        return parts;
    }

    /**
     * @return the team key of the lock on the container, null if it isn't locked
     */
    @Nullable
    public String getLock(Block container) {
        if (!(container.getState(false) instanceof Container)) return null;
        for (Block part : getParts(container)) {
            for (BlockFace face : SIGN_FACES) {
                Block sign = part.getRelative(face);
                String team = getLockTeam(sign);
                if (team != null && part.equals(getAttachedBlock(sign))) return team;
            }
        }
        return null;
    }

    /**
     * @return true if the player may open or break the block (not locked or locked by the own team)
     */
    private boolean hasAccess(Player player, @Nullable String lock) {
        if (lock == null || plugin.getBuildMode().isBuilding(player)) return true;
        Game game = plugin.getUser(player).getGame();
        GameTeam team = game == null ? null : game.getTeam(player.getUniqueId());
        return team != null && team.getKey().equals(lock);
    }

    /**
     * @return the lock of the block: of the container or of the lock sign itself
     */
    @Nullable
    private String getLockOfBlock(Block block) {
        String lock = getLockTeam(block);
        return lock != null ? lock : getLock(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        Component firstLine = e.line(0);
        if (firstLine == null || !I.plain(firstLine).trim().equalsIgnoreCase("[lock]")) return;
        Game game = getGame(e.getBlock());
        if (game == null) return;
        Player player = e.getPlayer();
        GameTeam team = game.getTeam(player.getUniqueId());
        if (game.getState() != GameState.RUNNING || team == null || plugin.getUser(player).getGame() != game) {
            player.sendMessage(I.prefixed(LangText.LOCK_ONLY_INGAME));
            return;
        }
        Block container = getAttachedBlock(e.getBlock());
        if (container == null || !(container.getState(false) instanceof Container)) {
            player.sendMessage(I.prefixed(LangText.LOCK_NO_CONTAINER));
            return;
        }
        if (getLock(container) != null) {
            player.sendMessage(I.prefixed(LangText.LOCK_ALREADY_LOCKED));
            e.setCancelled(true);
            return;
        }
        e.line(0, Component.text("[Locked]", NamedTextColor.DARK_RED));
        e.line(1, team.getDisplayName());
        Block block = e.getBlock();
        // the sign gets its text after the event
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(block.getState() instanceof Sign sign)) return;
            sign.getPersistentDataContainer().set(lockKey, PersistentDataType.STRING, team.getKey());
            sign.setWaxed(true);
            sign.update();
        });
        player.sendMessage(I.prefixed(LangText.LOCK_CREATED, team.getDisplayName()));
        plugin.getTelemetry().used(Telemetry.Feature.CHEST_LOCK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        Block block = getBlock(e.getInventory().getHolder());
        if (block == null || hasAccess(player, getLock(block))) return;
        e.setCancelled(true);
        player.sendMessage(I.prefixed(LangText.LOCK_NO_ACCESS));
    }

    @Nullable
    private static Block getBlock(@Nullable InventoryHolder holder) {
        if (holder instanceof DoubleChest doubleChest && doubleChest.getLeftSide() instanceof Chest left) {
            return left.getBlock();
        }
        if (holder instanceof BlockState state) return state.getBlock();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (hasAccess(e.getPlayer(), getLockOfBlock(e.getBlock()))) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(I.prefixed(LangText.LOCK_NO_ACCESS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> getLockOfBlock(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> getLockOfBlock(block) != null);
    }

    /**
     * hoppers can't empty a locked container
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        Block block = getBlock(e.getSource().getHolder(false));
        if (block != null && getLock(block) != null) e.setCancelled(true);
    }

}
