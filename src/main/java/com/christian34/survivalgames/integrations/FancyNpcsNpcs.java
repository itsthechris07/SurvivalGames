package com.christian34.survivalgames.integrations;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * FancyNpcs (https://github.com/FancyInnovations/FancyPlugins): clicks on NPCs, the state of the game behind the
 * display name (MiniMessage)
 */
class FancyNpcsNpcs implements Listener, NpcStatusDisplay {
    static final String PLUGIN = "FancyNpcs";
    static final String PREFIX = "fancynpcs";
    /**
     * the display name of FancyNpcs for "no name"
     */
    private static final String NO_NAME = "<empty>";
    private final NpcSupport support;

    FancyNpcsNpcs(NpcSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(NpcInteractEvent e) {
        if (support.click(e.getPlayer(), PREFIX + ":" + e.getNpc().getData().getId(), e.getNpc().getData().getName())) {
            // no actions of FancyNpcs (e.g. commands) on linked NPCs
            e.setCancelled(true);
        }
    }

    @Override
    public boolean exists(@NotNull String id) {
        return FancyNpcsPlugin.get().getNpcManager().getNpcById(id) != null;
    }

    /**
     * @param shown the display name of the NPC without the state
     */
    @Override
    @Nullable
    public String showStatus(@NotNull String id, @Nullable String shown, @Nullable Component status) {
        Npc npc = Objects.requireNonNull(FancyNpcsPlugin.get().getNpcManager().getNpcById(id));
        String current = npc.getData().getDisplayName();
        // renamed by an admin in the meantime: that is the name now
        String name = shown != null && (current.startsWith(shown) || NO_NAME.equals(shown)) ? shown : current;
        String displayName = name;
        if (status != null) {
            String text = MiniMessage.miniMessage().serialize(status);
            displayName = NO_NAME.equals(name) ? text : name + "<reset> " + text;
        }
        if (!displayName.equals(current)) {
            npc.getData().setDisplayName(displayName);
            npc.updateForAll();
        }
        return status == null ? null : name;
    }

}
