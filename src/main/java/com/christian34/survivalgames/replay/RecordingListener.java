package com.christian34.survivalgames.replay;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Records what happens in the games that are recorded (the movements are polled by the {@link Recorder} itself).
 *
 * @author Christian34
 */
final class RecordingListener implements Listener {
    private static final BlockFace[] NEIGHBORS = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST,
            BlockFace.SOUTH, BlockFace.WEST};
    private final Replays replays;

    RecordingListener(Replays replays) {
        this.replays = replays;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent e) {
        Recorder recorder = replays.recorder(e.getPlayer());
        if (recorder != null) recorder.swing(e.getPlayer(), e.getHand() == EquipmentSlot.OFF_HAND);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        Recorder recorder = replays.recorder(player);
        if (recorder != null) recorder.hurt(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Recorder recorder = replays.recorder(e.getPlayer());
        if (recorder != null) recorder.death(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player player)) return;
        Recorder recorder = replays.recorder(player);
        if (recorder != null) recorder.launch(player, e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent e) {
        Recorder recorder = replays.recorder(e.getWorld());
        if (recorder != null) recorder.lightning(e.getLightning().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Recorder recorder = replays.recorder(e.getBlock().getWorld());
        if (recorder == null) return;
        // the block is placed already
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState replaced : multi.getReplacedBlockStates()) {
                recorder.block(replaced.getBlock(), replaced.getBlockData());
            }
        } else {
            recorder.block(e.getBlock(), e.getBlockReplacedState().getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        withNeighbors(e.getBlock());
    }

    /**
     * the block and what hangs on it (torches, doors, ...)
     */
    private void withNeighbors(Block block) {
        Recorder recorder = replays.recorder(block.getWorld());
        if (recorder == null) return;
        recorder.block(block, block.getBlockData());
        for (BlockFace face : NEIGHBORS) {
            Block neighbor = block.getRelative(face);
            recorder.block(neighbor, neighbor.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        withNeighbors(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        replays.block(e.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        replays.blocks(e.getBlock().getWorld(), e.blockList());
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        replays.blocks(e.getLocation().getWorld(), e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        withNeighbors(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) {
        replays.block(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        replays.block(e.getBlock());
    }

    /**
     * doors, levers, buttons, cake, ...
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null || e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        withNeighbors(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        piston(e.getBlock(), e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        piston(e.getBlock(), e.getBlocks(), e.getDirection());
    }

    private void piston(Block piston, List<Block> moved, BlockFace direction) {
        Recorder recorder = replays.recorder(piston.getWorld());
        if (recorder == null) return;
        withNeighbors(piston);
        for (Block block : moved) {
            recorder.block(block, block.getBlockData());
            Block target = block.getRelative(direction);
            recorder.block(target, target.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        states(e.getWorld(), e.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        states(e.getBlock().getWorld(), e.getBlocks());
    }

    /**
     * @param states what the blocks are going to be
     */
    private void states(World world, List<BlockState> states) {
        Recorder recorder = replays.recorder(world);
        if (recorder == null) return;
        for (BlockState state : states) {
            Block block = state.getBlock();
            recorder.block(block, block.getBlockData());
        }
    }
}
