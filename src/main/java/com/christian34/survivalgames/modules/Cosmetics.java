package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Menus;
import com.christian34.survivalgames.game.Rewards;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.Achievement;
import com.christian34.survivalgames.stats.StatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The cosmetics of the players (modules.cosmetics, /sg cosmetics), see {@link Cosmetic}. They are unlocked for free,
 * bought with the money of an economy plugin, unlocked with an achievement or given with the permission
 * sg.cosmetic.&lt;name&gt; (sg.cosmetic.* for all). The choice and what a player has bought are kept with his
 * statistics, so the servers of a network share them (MySQL).
 *
 * @author Christian34
 */
public class Cosmetics extends GameModule implements Listener {
    public static final String MODULE = "cosmetics";
    public static final String PERMISSION = "sg.cosmetic.";
    private static final String BOUGHT = "bought-";
    /**
     * ticks a trail follows a projectile at most
     */
    private static final int TRAIL_TICKS = 20 * 5;
    /**
     * seconds of the win effect
     */
    private static final int WIN_SECONDS = 5;

    public Cosmetics(SurvivalGames plugin) {
        super(plugin, MODULE);
    }

    private StatsManager stats() {
        return plugin.getStats();
    }

    /**
     * @return true if the players can choose cosmetics (the module is on and the statistics keep the choice)
     */
    public boolean isEnabled() {
        return stats().isEnabled()
                && config().getBoolean("enabled", true);
    }

    /**
     * @return free, a price, achievement:&lt;key&gt; or permission (modules.cosmetics.unlock.&lt;name&gt;)
     */
    public String getUnlock(Cosmetic cosmetic) {
        String unlock = plugin.getFileManager().getConfigFile().getString("modules." + MODULE + ".unlock." + cosmetic.getKey());
        return unlock == null || unlock.isBlank() ? cosmetic.getDefaultUnlock() : unlock.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * @return the price, 0 if it can't be bought
     */
    public double getPrice(Cosmetic cosmetic) {
        try {
            return Math.max(0, Double.parseDouble(getUnlock(cosmetic)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * @return the achievement that unlocks the cosmetic, null if it is unlocked otherwise
     */
    @Nullable
    public Achievement getAchievement(Cosmetic cosmetic) {
        String unlock = getUnlock(cosmetic);
        if (!unlock.startsWith("achievement:")) return null;
        String key = unlock.substring("achievement:".length()).trim();
        for (Achievement achievement : Achievement.values()) {
            if (achievement.getKey().equals(key)) return achievement;
        }
        return null;
    }

    /**
     * @return true if the player may use the cosmetic
     */
    public boolean owns(Player player, Cosmetic cosmetic) {
        if (player.hasPermission(PERMISSION + cosmetic.getKey()) || player.hasPermission(PERMISSION + "*")) return true;
        String unlock = getUnlock(cosmetic);
        if (unlock.equals("free")) return true;
        Achievement achievement = getAchievement(cosmetic);
        if (achievement != null) {
            Map<String, Long> unlocked = stats().getCachedAchievements(player.getUniqueId());
            return unlocked != null && unlocked.containsKey(achievement.getKey());
        }
        return getPrice(cosmetic) > 0 && stats().getSetting(player.getUniqueId(), BOUGHT + cosmetic.getKey()) != null;
    }

    /**
     * @return the cosmetic of the category the player has chosen, null if none (or he may not use it anymore)
     */
    @Nullable
    public Cosmetic getSelected(Player player, Cosmetic.Category category) {
        Cosmetic cosmetic = Cosmetic.parse(stats().getSetting(player.getUniqueId(), category.getSetting()));
        return cosmetic != null && cosmetic.getCategory() == category && owns(player, cosmetic) ? cosmetic : null;
    }

    /**
     * the player chooses the cosmetic - he buys it if it has a price and he hasn't got it yet
     */
    public void select(Player player, Cosmetic cosmetic) {
        if (!isEnabled()) {
            player.sendMessage(I.prefixed(player, LangText.COSMETICS_DISABLED));
            return;
        }
        if (!owns(player, cosmetic)) {
            double price = getPrice(cosmetic);
            if (price <= 0) {
                player.sendMessage(I.prefixed(player, LangText.COSMETIC_LOCKED, i18n(cosmetic.getDisplayName())));
                return;
            }
            String formatted = plugin.getEconomySupport().format(price);
            if (!plugin.getEconomySupport().withdraw(player, price)) {
                player.sendMessage(I.prefixed(player, LangText.COSMETIC_TOO_EXPENSIVE, i18n(cosmetic.getDisplayName()), formatted));
                return;
            }
            stats().setSetting(player.getUniqueId(), player.getName(), BOUGHT + cosmetic.getKey(),
                    String.valueOf(System.currentTimeMillis()));
            player.sendMessage(I.prefixed(player, LangText.COSMETIC_BOUGHT, i18n(cosmetic.getDisplayName()), formatted));
        }
        stats().setSetting(player.getUniqueId(), player.getName(), cosmetic.getCategory().getSetting(), cosmetic.getKey());
        player.sendMessage(I.prefixed(player, LangText.COSMETIC_SELECTED, i18n(cosmetic.getDisplayName())));
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    /**
     * the player doesn't use a cosmetic of the category anymore
     */
    public void deselect(Player player, Cosmetic.Category category) {
        stats().setSetting(player.getUniqueId(), player.getName(), category.getSetting(), null);
        player.sendMessage(I.prefixed(player, LangText.COSMETIC_REMOVED, i18n(category.getDisplayName())));
    }

    // ---------------------------------------------------------------- menus

    /**
     * the categories with the chosen cosmetics
     */
    public void openMenu(Player player) {
        if (!isEnabled()) {
            player.sendMessage(I.prefixed(player, LangText.COSMETICS_DISABLED));
            return;
        }
        Menu menu = new Menu(i18n(LangText.GUI_COSMETICS));
        for (Cosmetic.Category category : Cosmetic.Category.values()) {
            Cosmetic selected = getSelected(player, category);
            List<Component> lore = new ArrayList<>(I.itemLines(category.getLore()));
            lore.add(i18n(LangText.GUI_COSMETICS_SELECTED, selected == null ? i18n(LangText.GUI_COSMETIC_NONE)
                    : i18n(selected.getDisplayName())));
            menu.button(ItemStack.of(selected == null ? category.getIcon() : selected.getIcon()),
                    i18n(category.getDisplayName()), lore, clicker -> openCategory(clicker, category));
        }
        menu.back(Menus::openMain);
        menu.show(player);
    }

    /**
     * the cosmetics of the category - click: choose it (buy it)
     */
    public void openCategory(Player player, Cosmetic.Category category) {
        Menu menu = new Menu(i18n(category.getDisplayName()));
        Cosmetic selected = getSelected(player, category);
        for (Cosmetic cosmetic : Cosmetic.values()) {
            if (cosmetic.getCategory() != category) continue;
            boolean owned = owns(player, cosmetic);
            List<Component> lore = new ArrayList<>();
            if (cosmetic == selected) {
                lore.add(i18n(LangText.GUI_COSMETIC_ACTIVE));
            } else if (owned) {
                lore.add(i18n(LangText.GUI_COSMETIC_SELECT));
            } else if (getPrice(cosmetic) > 0) {
                lore.add(i18n(LangText.GUI_COSMETIC_BUY, plugin.getEconomySupport().format(getPrice(cosmetic))));
            } else {
                Achievement achievement = getAchievement(cosmetic);
                lore.add(achievement != null ? i18n(LangText.GUI_COSMETIC_ACHIEVEMENT, i18n(achievement.getDisplayName()))
                        : i18n(LangText.GUI_COSMETIC_LOCKED));
            }
            menu.button(ItemStack.of(owned ? cosmetic.getIcon() : Material.GRAY_DYE),
                    i18n(cosmetic.getDisplayName()).color(cosmetic == selected ? NamedTextColor.GREEN
                            : owned ? NamedTextColor.YELLOW : NamedTextColor.GRAY), lore,
                    clicker -> {
                        if (cosmetic != getSelected(clicker, category)) select(clicker, cosmetic);
                        openCategory(clicker, category);
                    });
        }
        menu.corner(ItemStack.of(Material.BARRIER), i18n(LangText.GUI_COSMETIC_NONE), List.of(), clicker -> {
            deselect(clicker, category);
            openMenu(clicker);
        });
        menu.back(this::openMenu);
        menu.show(player);
    }

    // ---------------------------------------------------------------- effects

    /**
     * the killer's effect where the victim died
     */
    public void onKill(Game game, UUID killer, UUID victim) {
        if (!isEnabled(game) || !stats().isEnabled()) return;
        Player player = Bukkit.getPlayer(killer);
        Player dead = Bukkit.getPlayer(victim);
        if (player == null || dead == null) return;
        Cosmetic cosmetic = getSelected(player, Cosmetic.Category.KILL);
        if (cosmetic == null) return;
        Location location = dead.getLocation().add(0, 1, 0);
        World world = location.getWorld();
        switch (cosmetic) {
            case BLOOD -> {
                world.spawnParticle(Particle.BLOCK, location, 60, 0.3, 0.5, 0.3, Material.REDSTONE_BLOCK.createBlockData());
                world.playSound(location, Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
            }
            case HEARTS -> world.spawnParticle(Particle.HEART, location, 12, 0.6, 0.6, 0.6);
            case FIREWORK -> Rewards.burst(location, FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.RED, Color.ORANGE).withFade(Color.YELLOW).build());
            case LIGHTNING -> world.strikeLightningEffect(location);
            case SOULS -> {
                world.spawnParticle(Particle.SOUL, location, 30, 0.4, 0.8, 0.4, 0.02);
                world.playSound(location, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1f);
            }
            case EXPLOSION -> {
                world.spawnParticle(Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5);
                world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            }
            default -> {
            }
        }
    }

    /**
     * the trail of the shooter behind his projectile
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        Projectile projectile = e.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        Game game = plugin.getUser(shooter).getGame();
        if (game == null || game.getState() != GameState.RUNNING || !isEnabled(game)
                || !stats().isEnabled()) {
            return;
        }
        Cosmetic cosmetic = getSelected(shooter, Cosmetic.Category.TRAIL);
        if (cosmetic == null) return;
        Particle particle = switch (cosmetic) {
            case NOTES -> Particle.NOTE;
            case MAGIC -> Particle.WITCH;
            case SOUL_FLAMES -> Particle.SOUL_FIRE_FLAME;
            case STARS -> Particle.END_ROD;
            default -> Particle.FLAME;
        };
        BukkitTask[] task = new BukkitTask[1];
        int[] ticks = {0};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (++ticks[0] > TRAIL_TICKS || !projectile.isValid() || projectile.isOnGround()) {
                task[0].cancel();
                return;
            }
            projectile.getWorld().spawnParticle(particle, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0);
        }, 1L, 1L);
    }

    /**
     * the effects of the winners who are in the game
     */
    public void onWin(Game game, Collection<Player> winners) {
        if (!isEnabled(game) || !stats().isEnabled()) return;
        for (Player winner : winners) {
            Cosmetic cosmetic = getSelected(winner, Cosmetic.Category.WIN);
            if (cosmetic != null) playWin(winner, cosmetic);
        }
    }

    private void playWin(Player winner, Cosmetic cosmetic) {
        UUID world = winner.getWorld().getUID();
        BukkitTask[] task = new BukkitTask[1];
        int[] ticks = {0};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // the world is unloaded with the next round
            if ((ticks[0] += 5) > WIN_SECONDS * 20 || !winner.isOnline() || !winner.getWorld().getUID().equals(world)) {
                task[0].cancel();
                return;
            }
            Location location = winner.getLocation();
            World w = location.getWorld();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            switch (cosmetic) {
                case FLAME_RING -> {
                    for (int i = 0; i < 24; i++) {
                        double angle = Math.PI * 2 * i / 24 + ticks[0] / 10.0;
                        w.spawnParticle(Particle.FLAME, location.clone().add(Math.cos(angle) * 1.5, 0.2,
                                Math.sin(angle) * 1.5), 1, 0, 0, 0, 0);
                    }
                }
                case MUSIC -> {
                    w.spawnParticle(Particle.NOTE, location.clone().add(0, 2.2, 0), 4, 0.6, 0.3, 0.6, 1);
                    w.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.5f + random.nextFloat() * 1.5f);
                }
                case TOTEM -> {
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.3);
                    if (ticks[0] == 5) w.playSound(location, Sound.ITEM_TOTEM_USE, 0.6f, 1f);
                }
                case STARFALL -> w.spawnParticle(Particle.END_ROD, location.clone().add(random.nextDouble(-2, 2), 4,
                        random.nextDouble(-2, 2)), 6, 0.2, 0.2, 0.2, 0.05);
                default -> {
                }
            }
        }, 0L, 5L);
    }

}
