package com.christian34.survivalgames.integrations;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Citizens (https://github.com/CitizensDev/Citizens2): clicks on NPCs, the state of the game as a hologram line
 * (Citizens understands MiniMessage)
 */
class CitizensNpcs implements Listener, NpcStatusDisplay {
    static final String PLUGIN = "Citizens";
    static final String PREFIX = "citizens";
    private final NpcSupport support;

    CitizensNpcs(NpcSupport support) {
        this.support = support;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(NPCRightClickEvent e) {
        if (support.click(e.getClicker(), PREFIX + ":" + e.getNPC().getUniqueId(), e.getNPC().getName())) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean exists(@NotNull String id) {
        return getNpc(id) != null;
    }

    @Nullable
    private static NPC getNpc(String id) {
        return CitizensAPI.getNPCRegistry().getByUniqueIdGlobal(UUID.fromString(id));
    }

    @Override
    @Nullable
    public String showStatus(@NotNull String id, @Nullable String shown, @Nullable Component status) {
        NPC npc = Objects.requireNonNull(getNpc(id));
        if (status == null && !npc.hasTrait(HologramTrait.class)) return null;
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        // the other lines of the hologram stay as they are
        List<String> lines = hologram.getLines();
        int index = shown == null ? -1 : lines.indexOf(shown);
        if (status == null) {
            if (index >= 0) hologram.removeLine(index);
            return null;
        }
        String text = MiniMessage.miniMessage().serialize(status);
        if (index >= 0) {
            if (!text.equals(shown)) hologram.setLine(index, text);
        } else {
            hologram.addLine(text);
        }
        return text;
    }

}
