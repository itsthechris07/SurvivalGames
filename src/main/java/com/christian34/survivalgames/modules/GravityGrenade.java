package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The gravity grenade: a crafted snowball (snowball + TNT + ender pearl). Where it hits, the blocks around are torn
 * out of the ground, pulled together with the players nearby and thrown away again. Containers, signs and
 * unbreakable blocks stay where they are.
 *
 * @author Christian34
 */
public class GravityGrenade implements Listener {
    public static final String MODULE = "gravity-grenade";
    /**
     * ticks the blocks and entities are pulled to the center
     */
    private static final int PULL_TICKS = 30;
    private final SurvivalGames plugin;
    private final NamespacedKey recipeKey;

    public GravityGrenade(SurvivalGames plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "gravity_grenade");
        if (config().getBoolean("modules.gravity-grenade.enabled")) {
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createItem());
            recipe.addIngredient(Material.SNOWBALL);
            recipe.addIngredient(Material.TNT);
            recipe.addIngredient(Material.ENDER_PEARL);
            Bukkit.addRecipe(recipe);
        }
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    public void removeRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public static ItemStack createItem() {
        return Items.create(Material.SNOWBALL, i18n(LangText.ITEM_GRAVITY_GRENADE),
                I.itemLines(LangText.ITEM_GRAVITY_GRENADE_LORE), Items.GRENADE);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball snowball) || !Items.is(snowball.getItem(), Items.GRENADE)) return;
        Game game = plugin.getGameManager().getGame(snowball.getWorld());
        if (!plugin.isModuleEnabled(MODULE, game) || game.getState() != GameState.RUNNING) return;
        Location center = e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : snowball.getLocation();
        explode(center, snowball.getShooter() instanceof Player player ? player : null);
    }

    /**
     * @param thrower gets the kill if somebody dies (may be null)
     */
    public void explode(Location center, @Nullable Player thrower) {
        World world = center.getWorld();
        int radius = Math.clamp(config().getInt("modules.gravity-grenade.radius"), 1, 8);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);

        List<FallingBlock> blocks = new ArrayList<>();
        for (Block block : getBlocks(center, radius)) {
            BlockData data = block.getBlockData();
            plugin.getReplays().block(block);
            block.setType(Material.AIR);
            FallingBlock fallingBlock = world.spawn(block.getLocation().add(0.5, 0, 0.5), FallingBlock.class,
                    falling -> {
                        falling.setBlockData(data);
                        falling.setDropItem(true);
                        falling.setHurtEntities(false);
                    });
            blocks.add(fallingBlock);
        }
        List<LivingEntity> entities = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius * 2, radius * 2, radius * 2)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player player && player.isInvulnerable())) {
                entities.add(living);
                if (thrower != null) {
                    living.damage(4, thrower);
                } else {
                    living.damage(4);
                }
            }
        }

        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (ticks++ < PULL_TICKS) {
                    blocks.forEach(block -> pull(block, center, 0.4));
                    entities.forEach(entity -> pull(entity, center, 0.25));
                    return;
                }
                // thrown away again
                for (FallingBlock block : blocks) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    block.setVelocity(new Vector(random.nextDouble(-1, 1), random.nextDouble(0.4, 1),
                            random.nextDouble(-1, 1)));
                }
                world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void pull(Entity entity, Location center, double speed) {
        if (!entity.isValid()) return;
        Vector direction = center.toVector().subtract(entity.getLocation().toVector());
        if (direction.lengthSquared() < 0.25) {
            entity.setVelocity(new Vector());
            return;
        }
        entity.setVelocity(direction.normalize().multiply(speed));
    }

    /**
     * @return the blocks in the sphere that are torn out (no air, liquids, unbreakable blocks or blocks with data)
     */
    private static List<Block> getBlocks(Location center, int radius) {
        List<Block> blocks = new ArrayList<>();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    int dx = x - cx, dy = y - cy, dz = z - cz;
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir() || block.isLiquid() || type.getHardness() < 0 || type == Material.OBSIDIAN
                            || block.getState(false) instanceof TileState) {
                        continue;
                    }
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

}
