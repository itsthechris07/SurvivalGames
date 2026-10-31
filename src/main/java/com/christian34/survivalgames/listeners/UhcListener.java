package com.christian34.survivalgames.listeners;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.UhcGame;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The rules of UHC that aren't part of the game itself: golden heads (crafted from the head of a dead player), no
 * portals and the scenarios cutclean (ores and meat drop smelted) and timber (a whole tree falls).
 *
 * @author Christian34
 */
public class UhcListener implements Listener {
    /**
     * cutclean: the drops are smelted
     */
    private static final Map<Material, Material> SMELTED = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.BEEF, Material.COOKED_BEEF),
            Map.entry(Material.PORKCHOP, Material.COOKED_PORKCHOP),
            Map.entry(Material.CHICKEN, Material.COOKED_CHICKEN),
            Map.entry(Material.MUTTON, Material.COOKED_MUTTON),
            Map.entry(Material.RABBIT, Material.COOKED_RABBIT),
            Map.entry(Material.COD, Material.COOKED_COD),
            Map.entry(Material.SALMON, Material.COOKED_SALMON));
    /**
     * timber: the most logs that fall at once
     */
    private static final int MAX_LOGS = 256;
    private final SurvivalGames plugin;
    private final NamespacedKey recipeKey;

    public UhcListener(SurvivalGames plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "golden_head");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createGoldenHead());
        recipe.shape("GGG", "GHG", "GGG");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('H', Material.PLAYER_HEAD);
        Bukkit.addRecipe(recipe);
    }

    public void removeRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public static ItemStack createGoldenHead() {
        return Items.create(Material.GOLDEN_APPLE, i18n(LangText.ITEM_GOLDEN_HEAD),
                I.itemLines(LangText.ITEM_GOLDEN_HEAD_LORE), Items.GOLDEN_HEAD);
    }

    @Nullable
    private UhcGame getGame(Player player) {
        return plugin.getUser(player).getGame() instanceof UhcGame game ? game : null;
    }

    /**
     * @return the running UHC in the world, null if there is none
     */
    @Nullable
    private UhcGame getRunningGame(World world) {
        Game game = plugin.getGameManager().getGame(world);
        return game instanceof UhcGame uhc && game.getState() == GameState.RUNNING ? uhc : null;
    }

    // ---------------------------------------------------------------- golden heads

    /**
     * golden heads can only be crafted in a UHC that uses them
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (!Items.is(e.getInventory().getResult(), Items.GOLDEN_HEAD)) return;
        UhcGame game = e.getView().getPlayer() instanceof Player player ? getGame(player) : null;
        if (game == null || !game.hasGoldenHeads()) e.getInventory().setResult(null);
    }

    /**
     * a golden head heals more than a golden apple: regeneration for longer (after the effects of the apple)
     */
    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!Items.is(e.getItem(), Items.GOLDEN_HEAD)) return;
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> player.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, 20 * 9, 1)));
    }

    // ---------------------------------------------------------------- portals

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (getGame(e.getPlayer()) == null) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(i18n(LangText.UHC_NO_PORTALS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (plugin.getGameManager().getGame(e.getEntity().getWorld()) instanceof UhcGame) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- scenarios

    /**
     * cutclean: ores drop ingots
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent e) {
        UhcGame game = getRunningGame(e.getBlock().getWorld());
        if (game == null || !game.hasScenario(UhcGame.CUTCLEAN)) return;
        for (Item item : e.getItems()) {
            ItemStack stack = item.getItemStack();
            Material smelted = SMELTED.get(stack.getType());
            if (smelted != null) item.setItemStack(stack.withType(smelted));
        }
    }

    /**
     * cutclean: animals drop cooked meat
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        UhcGame game = getRunningGame(e.getEntity().getWorld());
        if (game == null || !game.hasScenario(UhcGame.CUTCLEAN)) return;
        List<ItemStack> drops = e.getDrops();
        drops.replaceAll(stack -> {
            Material smelted = stack == null ? null : SMELTED.get(stack.getType());
            return smelted != null ? stack.withType(smelted) : stack;
        });
    }

    /**
     * timber: the logs connected to a broken log fall too
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!Tag.LOGS.isTagged(block.getType())) return;
        UhcGame game = getRunningGame(block.getWorld());
        if (game == null || !game.hasScenario(UhcGame.TIMBER) || getGame(e.getPlayer()) != game) return;
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        for (Block log : findTree(block)) {
            log.breakNaturally(tool);
        }
    }

    /**
     * @return the logs connected to the block (also diagonally), without the block itself
     */
    static Set<Block> findTree(Block start) {
        Set<Block> logs = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        Set<Block> seen = new HashSet<>(Set.of(start));
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            Block block = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block next = block.getRelative(x, y, z);
                        if (!seen.add(next) || !Tag.LOGS.isTagged(next.getType())) continue;
                        logs.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return logs;
    }

}
