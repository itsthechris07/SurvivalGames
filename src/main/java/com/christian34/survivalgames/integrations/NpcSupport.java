package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.network.ServerStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * Join NPCs of Citizens or FancyNpcs: an admin links an NPC with an arena (on a lobby server: a game server) by
 * /sg npc link <target> and clicking the NPC. Clicking it then works like a join sign, and the NPC shows the state
 * of the game (npcs.show-status, see {@link NpcStatusDisplay}). Saved in npcs.yml.
 *
 * @author Christian34
 */
public class NpcSupport {
    /**
     * time to click the NPC after /sg npc link or unlink
     */
    static final long CLICK_MILLIS = 30_000;
    private final SurvivalGames plugin;
    private final File file;
    private final Map<String, Link> links = new LinkedHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final List<String> plugins = new ArrayList<>();
    /**
     * the status displays of the NPC plugins by the prefix of the keys ("citizens")
     */
    private final Map<String, NpcStatusDisplay> displays = new HashMap<>();
    /**
     * what the NPC plugins show on the NPCs (see {@link NpcStatusDisplay#showStatus}), saved in npcs.yml
     */
    private final Map<String, String> shown = new HashMap<>();
    /**
     * the state last shown on the NPCs (null: none), so the NPCs only change when the state does
     */
    private final Map<String, Component> statuses = new HashMap<>();
    private boolean failed;

    /**
     * @param npc    the name of the NPC when it was linked
     * @param target the arena, on a lobby server the game server
     */
    public record Link(String key, String npc, String target) {
    }

    /**
     * @param target null: unlink
     */
    private record Pending(@Nullable String target, long until) {
    }

    public NpcSupport(SurvivalGames plugin) {
        this.plugin = plugin;
        this.file = new File(FileManager.getPluginFolder(), "npcs.yml");
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npcs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String target = section.getString(key + ".target");
                if (target != null) links.put(key, new Link(key, section.getString(key + ".name", key), target));
                String status = section.getString(key + ".shown");
                if (status != null) shown.put(key, status);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled(CitizensNpcs.PLUGIN)) {
            CitizensNpcs citizens = new CitizensNpcs(this);
            register(CitizensNpcs.PLUGIN, CitizensNpcs.PREFIX, citizens, citizens);
        }
        if (Bukkit.getPluginManager().isPluginEnabled(FancyNpcsNpcs.PLUGIN)) {
            FancyNpcsNpcs fancyNpcs = new FancyNpcsNpcs(this);
            register(FancyNpcsNpcs.PLUGIN, FancyNpcsNpcs.PREFIX, fancyNpcs, fancyNpcs);
        }
        // the arenas of NPCs get a game on start, like those of signs
        if (!plugin.getNetworkManager().isLobby()) {
            for (Link link : links.values()) {
                Arena arena = plugin.getArenaManager().get(link.target());
                if (arena != null) plugin.getSignManager().ensureGame(arena);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateStatus, 20L, 20L);
    }

    private void register(String name, String prefix, Listener listener, NpcStatusDisplay display) {
        try {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            plugins.add(name);
            displays.put(prefix, display);
            Debug.info("Hooked into " + name + " (join NPCs)");
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't hook into " + name + ", join NPCs don't work with it: " + ex);
        }
    }

    /**
     * for tests: pretends an NPC plugin is installed
     */
    public void addPlugin(String name) {
        plugins.add(name);
    }

    /**
     * for tests: pretends an NPC plugin is installed that shows the state of the game on NPCs
     *
     * @param prefix the prefix of the keys of its NPCs ("citizens")
     */
    public void addPlugin(String name, String prefix, NpcStatusDisplay display) {
        plugins.add(name);
        displays.put(prefix, display);
    }

    /**
     * @return the installed NPC plugins
     */
    public List<String> getPlugins() {
        return plugins;
    }

    public List<Link> getLinks() {
        return new ArrayList<>(links.values());
    }

    /**
     * the next NPC the admin clicks gets linked with the target
     *
     * @param target the arena or game server (checked already), null: removes the link of the NPC
     */
    public void startLinking(@NotNull Player admin, @Nullable String target) {
        pending.put(admin.getUniqueId(), new Pending(target, System.currentTimeMillis() + CLICK_MILLIS));
    }

    /**
     * a player clicked an NPC
     *
     * @param key  the plugin and the id of the NPC ("citizens:<uuid>")
     * @param name the name of the NPC
     * @return true if SurvivalGames handled the click (the NPC plugin shouldn't do anything else)
     */
    public boolean click(@NotNull Player player, @NotNull String key, @NotNull String name) {
        Pending action = pending.remove(player.getUniqueId());
        if (action != null && action.until() >= System.currentTimeMillis()) {
            if (action.target() == null) {
                if (!links.containsKey(key)) {
                    player.sendMessage(I.prefixed(LangText.NPC_NOT_LINKED, name));
                } else {
                    showStatus(key, null);
                    links.remove(key);
                    save();
                    player.sendMessage(I.prefixed(LangText.NPC_UNLINKED, name));
                }
            } else {
                links.put(key, new Link(key, name, action.target()));
                // the new target is shown with the next update
                statuses.remove(key);
                save();
                player.sendMessage(I.prefixed(LangText.NPC_LINKED, name, action.target()));
            }
            return true;
        }
        Link link = links.get(key);
        if (link == null) return false;
        plugin.getSignManager().join(player, link.target());
        return true;
    }

    /**
     * shows the state of the games on the linked NPCs (every second, only changes are sent to the NPC plugins)
     */
    public void updateStatus() {
        boolean enabled = plugin.getFileManager().getConfigFile().getBoolean("npcs.show-status");
        boolean changed = false;
        for (Link link : links.values()) {
            Component status = enabled ? getStatus(link.target()) : null;
            if (statuses.containsKey(link.key()) && Objects.equals(statuses.get(link.key()), status)) continue;
            changed |= showStatus(link.key(), status);
        }
        if (changed) save();
    }

    /**
     * removes the state from all NPCs (SurvivalGames is disabled - it may be removed)
     */
    public void removeStatus() {
        boolean changed = false;
        for (Link link : links.values()) {
            changed |= showStatus(link.key(), null);
        }
        if (changed) save();
    }

    /**
     * @return the state of the game shown on the NPCs ("Waiting • 2/8")
     */
    public Component getStatus(String target) {
        ServerStatus status = plugin.getSignManager().getStatus(target);
        Component state = SignManager.stateText(status);
        if (status.state() == ServerStatus.State.OFFLINE) return state;
        return i18n(LangText.NPC_STATUS, state, status.players(), status.maxPlayers());
    }

    /**
     * @param status null: removes it
     * @return true if what the NPC shows has changed (npcs.yml needs to be saved)
     */
    private boolean showStatus(String key, @Nullable Component status) {
        int separator = key.indexOf(':');
        NpcStatusDisplay display = separator < 0 ? null : displays.get(key.substring(0, separator));
        if (display == null) return false;
        String id = key.substring(separator + 1);
        String before = shown.get(key);
        String now;
        try {
            // tried again with the next update (NPCs are loaded after the start)
            if (!display.exists(id)) return false;
            statuses.put(key, status);
            now = display.showStatus(id, before, status);
        } catch (Exception | LinkageError ex) {
            // an incompatible version of the NPC plugin: clicks may still work
            if (!failed) Debug.warn("Couldn't show the state of the game on the NPC " + key + ": " + ex);
            failed = true;
            return false;
        }
        if (Objects.equals(before, now)) return false;
        if (now == null) {
            shown.remove(key);
        } else {
            shown.put(key, now);
        }
        return true;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Link link : links.values()) {
            data.set("npcs." + link.key() + ".name", link.npc());
            data.set("npcs." + link.key() + ".target", link.target());
            data.set("npcs." + link.key() + ".shown", shown.get(link.key()));
        }
        try {
            data.save(file);
        } catch (IOException ex) {
            Debug.warn("Couldn't save npcs.yml: " + ex.getMessage());
        }
    }

}
