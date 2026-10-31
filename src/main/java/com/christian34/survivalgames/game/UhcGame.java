package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * A round of UHC (Ultra Hardcore) in a newly generated world: no loot in the chests, everything is mined and crafted.
 * Health doesn't come back by itself (only golden apples, golden heads, potions and the final heal). The teams start
 * at random places in the world, PvP begins after the grace period and the shrinking world border pushes everybody
 * into the center. The rest (lobby, grace period, border, deathmatch, end) works like a round of Survival Games.
 *
 * @author Christian34
 */
public class UhcGame extends SurvivalGame {
    public static final String CUTCLEAN = "cutclean";
    public static final String TIMBER = "timber";
    /**
     * chunks generated at the same time while the players wait in the lobby
     */
    private static final int PREGENERATION_CHUNKS = 4;
    /**
     * random places tried per team until one is on dry land
     */
    private static final int SCATTER_ATTEMPTS = 200;
    /**
     * seconds until everybody alive is healed, -1: no final heal (anymore)
     */
    private int finalHealIn = -1;
    @Nullable
    private BukkitTask pregeneration;
    private int pregenerated;
    private int toPregenerate;

    public UhcGame(SurvivalGames plugin, Arena arena) {
        super(plugin, arena);
        startPregeneration();
    }

    @Override
    public GameType getType() {
        return GameType.UHC;
    }

    @Override
    protected void setupWorld(World world) {
        super.setupWorld(world);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, settings().getBoolean("natural-regeneration"));
    }

    /**
     * @return the scenarios of the game (uhc.scenarios, lower case)
     */
    public List<String> getScenarios() {
        return settings().getStringList("scenarios").stream().map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty()).toList();
    }

    public boolean hasScenario(String scenario) {
        return getScenarios().contains(scenario);
    }

    public boolean hasGoldenHeads() {
        return settings().getBoolean("golden-heads");
    }

    // ---------------------------------------------------------------- pregeneration

    /**
     * the chunks inside the world border are generated while the players wait in the lobby, the ones at the center
     * first - the rest is generated when somebody gets there
     */
    private void startPregeneration() {
        int size = arena.getBorderSize();
        if (!settings().getBoolean("pregenerate") || size <= 0) return;
        World world = getWorld();
        Location center = arena.getCenter(world);
        int centerX = center.getBlockX() >> 4;
        int centerZ = center.getBlockZ() >> 4;
        int radius = (size / 2 >> 4) + 1;
        List<int[]> chunks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                chunks.add(new int[]{centerX + x, centerZ + z});
            }
        }
        chunks.sort(Comparator.comparingInt(chunk -> Math.max(Math.abs(chunk[0] - centerX), Math.abs(chunk[1] - centerZ))));
        Deque<int[]> queue = new ArrayDeque<>(chunks);
        this.toPregenerate = chunks.size();
        AtomicInteger loading = new AtomicInteger();
        long started = System.currentTimeMillis();
        this.pregeneration = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isStopped() || getState() != GameState.LOBBY) {
                stopPregeneration();
                return;
            }
            while (loading.get() < PREGENERATION_CHUNKS && !queue.isEmpty()) {
                int[] chunk = queue.poll();
                loading.incrementAndGet();
                try {
                    world.getChunkAtAsync(chunk[0], chunk[1], true).whenComplete((loaded, ex) -> {
                        loading.decrementAndGet();
                        pregenerated++;
                    });
                } catch (RuntimeException ex) {
                    // not implemented by MockBukkit: one chunk per tick
                    world.getChunkAt(chunk[0], chunk[1]).load();
                    loading.decrementAndGet();
                    pregenerated++;
                    break;
                }
            }
            if (queue.isEmpty() && loading.get() == 0) {
                Debug.info("Generated " + toPregenerate + " chunks of the world of arena '" + arena.getName() + "' in "
                        + (System.currentTimeMillis() - started) / 1000 + " seconds");
                stopPregeneration();
            }
        }, 1L, 1L);
    }

    private void stopPregeneration() {
        if (pregeneration != null) pregeneration.cancel();
        this.pregeneration = null;
    }

    /**
     * @return the progress of the pregeneration in percent (100: done or none)
     */
    public int getPregenerationProgress() {
        return toPregenerate == 0 ? 100 : Math.min(100, pregenerated * 100 / toPregenerate);
    }

    // ---------------------------------------------------------------- scatter

    @Override
    public void start() {
        broadcast(LangText.UHC_SCATTERING);
        super.start();
    }

    /**
     * every team starts at a random place inside the world border, at least uhc.scatter-distance blocks apart from the
     * other teams (less if there is no room), its members next to each other
     */
    @Override
    protected List<Location> resolveSpawns(int count) {
        World world = getWorld();
        Location center = arena.getCenter(world);
        int size = getStartBorderSize();
        double radius = Math.max(8, (size > 0 ? size / 2.0 : 500) - 16);
        double distance = Math.max(0, settings().getDouble("scatter-distance", 100));
        List<Location> places = new ArrayList<>();
        List<Location> spawns = new ArrayList<>();
        for (GameTeam team : teams.getTeams()) {
            Location place = null;
            int index = 0;
            for (UUID member : team.getMembers()) {
                if (participants.get(member) == null) continue;
                if (place == null) {
                    place = scatter(world, center, radius, distance, places);
                    places.add(place);
                }
                spawns.add(beside(place, index++));
            }
        }
        while (spawns.size() < count) {
            Location place = scatter(world, center, radius, distance, places);
            places.add(place);
            spawns.add(place);
        }
        return spawns;
    }

    /**
     * @return a random place on the ground (not in water or lava) away from the others - if there is no dry land (an
     * ocean), the center on a small platform
     */
    private static Location scatter(World world, Location center, double radius, double distance, List<Location> others) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location place = null;
        for (int attempt = 0; attempt < SCATTER_ATTEMPTS && place == null; attempt++) {
            // the distance is halved every 10 attempts: a small world doesn't have room for everybody
            double minDistance = attempt < 60 ? distance / (1 << attempt / 10) : 0;
            Location candidate = center.clone().add(random.nextDouble(-radius, radius), 0, random.nextDouble(-radius, radius));
            if (others.stream().anyMatch(other -> other.distanceSquared(candidate) < minDistance * minDistance)) continue;
            if (isSafeGround(world, candidate)) place = candidate;
        }
        if (place == null) {
            place = center.clone();
            buildPlatform(world, place);
        }
        Location spawn = ground(place);
        Vector direction = center.toVector().subtract(spawn.toVector()).setY(0);
        if (direction.lengthSquared() > 0.25) spawn.setDirection(direction);
        return spawn;
    }

    /**
     * @return the place of a member of a team: the first one on the place, the others in a circle around him (on the
     * ground, looking the same way), so nobody stands inside somebody else
     */
    private static Location beside(Location place, int index) {
        if (index == 0) return place.clone();
        double angle = 2 * Math.PI * (index - 1) / 6 + (index - 1) / 6 * 0.5;
        double radius = 2 + (index - 1) / 6 * 1.5;
        Location location = place.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        Location spawn = ground(location);
        spawn.setYaw(place.getYaw());
        spawn.setPitch(place.getPitch());
        return spawn;
    }

    /**
     * open ground: no water, lava or cactus and nothing above it (not on or under a tree)
     */
    private static boolean isSafeGround(World world, Location location) {
        try {
            int x = location.getBlockX();
            int z = location.getBlockZ();
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material type = ground.getType();
            if (ground.getY() != world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)) return false;
            return !ground.isLiquid() && type != Material.MAGMA_BLOCK && type != Material.CACTUS
                    && type != Material.POWDER_SNOW && !type.isAir() && !Tag.LOGS.isTagged(type)
                    && !Tag.LEAVES.isTagged(type);
        } catch (RuntimeException ex) {
            // MockBukkit
            return true;
        }
    }

    /**
     * a 3x3 platform on water or lava (nobody spawns in the ocean)
     */
    private static void buildPlatform(World world, Location location) {
        try {
            Block ground = world.getHighestBlockAt(location.getBlockX(), location.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!ground.isLiquid()) return;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = ground.getRelative(x, 0, z);
                    if (block.isLiquid() || block.getType().isAir()) block.setType(Material.STONE);
                }
            }
        } catch (RuntimeException ex) {
            // MockBukkit
        }
    }

    /**
     * @return the location on the highest block (leaves don't count: never on top of a tree)
     */
    private static Location ground(Location location) {
        try {
            World world = location.getWorld();
            int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            return new Location(world, location.getBlockX() + 0.5, y + 1, location.getBlockZ() + 0.5,
                    location.getYaw(), location.getPitch());
        } catch (RuntimeException ex) {
            // MockBukkit
            return safe(location);
        }
    }

    /**
     * the deathmatch: everybody alive stands in a circle around the center
     */
    @Override
    protected Location getDeathmatchSpawn(Participant participant) {
        World world = getWorld();
        Location center = arena.getCenter(world);
        double radius = Math.max(3, settings().getInt("deathmatch.border-size") / 2.0 - 5);
        double angle = 2 * Math.PI * Math.max(0, participant.getSpawn()) / Math.max(1, participants.size());
        Location location = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        location.setY(world.getMinHeight());
        Location spawn = ground(location);
        spawn.setDirection(center.toVector().subtract(spawn.toVector()).setY(0));
        return spawn;
    }

    // ---------------------------------------------------------------- running

    @Override
    protected void onBegin() {
        super.onBegin();
        stopPregeneration();
        int minutes = settings().getInt("final-heal");
        this.finalHealIn = minutes > 0 ? minutes * 60 : -1;
        // a new world starts in the morning, but the lobby may have taken a while
        if (settings().getLong("time", -1) < 0) getWorld().setTime(0);
        List<String> scenarios = getScenarios();
        if (!scenarios.isEmpty()) broadcast(LangText.UHC_SCENARIOS, describeScenarios(scenarios));
    }

    @Override
    protected void onTick() {
        super.onTick();
        if (getState() != GameState.RUNNING || finalHealIn < 0) return;
        finalHealIn--;
        if (finalHealIn == 0) {
            this.finalHealIn = -1;
            finalHeal();
        } else if ((finalHealIn == 60 || finalHealIn == 10) && finalHealIn != getGraceLeft()) {
            // at the end of the grace period its reminders are enough
            broadcast(LangText.UHC_FINAL_HEAL_SOON, finalHealIn);
        }
    }

    /**
     * @return the scenarios, explained when hovering over them
     */
    private static Component describeScenarios(List<String> scenarios) {
        List<Component> names = new ArrayList<>();
        for (String scenario : scenarios) {
            LangText description = switch (scenario) {
                case CUTCLEAN -> LangText.UHC_SCENARIO_CUTCLEAN;
                case TIMBER -> LangText.UHC_SCENARIO_TIMBER;
                default -> null;
            };
            Component name = Component.text(scenario);
            names.add(description != null ? name.hoverEvent(HoverEvent.showText(i18n(description))) : name);
        }
        return Component.join(JoinConfiguration.commas(true), names);
    }

    /**
     * @return the seconds until the final heal, -1 if there is none (anymore)
     */
    public int getFinalHealIn() {
        return finalHealIn;
    }

    /**
     * everybody alive gets full health and food
     */
    public void finalHeal() {
        for (GamePlayer member : getPlayers()) {
            Player player = member.getPlayer();
            Participant participant = getParticipant(player.getUniqueId());
            if (participant == null || !participant.isAlive()) continue;
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(maxHealth != null ? maxHealth.getValue() : 20);
            player.setFoodLevel(20);
            player.setSaturation(20);
        }
        broadcast(LangText.UHC_FINAL_HEAL);
    }

    /**
     * the player drops his head (golden heads)
     */
    @Override
    public void onDeath(GamePlayer gamePlayer, PlayerDeathEvent e) {
        Player player = gamePlayer.getPlayer();
        Participant participant = getParticipant(player.getUniqueId());
        if (hasGoldenHeads() && getState() == GameState.RUNNING && participant != null && participant.isAlive()) {
            ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
            e.getDrops().add(head);
        }
        super.onDeath(gamePlayer, e);
    }

    /**
     * the chests of the world keep their own loot
     */
    @Override
    public void fillContainer(Inventory inventory) {
    }

    @Override
    protected void onStop() {
        stopPregeneration();
        super.onStop();
    }

}
