package com.christian34.survivalgames.integrations;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.mvplugins.multiverse.inventories.event.GameModeChangeShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.ReadOnlyShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.ShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.WorldChangeShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.WriteOnlyShareHandlingEvent;

/**
 * Multiverse-Inventories (https://github.com/Multiverse/Multiverse-Inventories), see {@link MultiverseSupport}.
 * Bukkit needs a handler for each event class (the base class has no handler list).
 */
class MultiverseInventoriesHook implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onWorldChange(WorldChangeShareHandlingEvent e) {
        if (MultiverseSupport.ignoresInventory(e.getPlayer(), e.getFromWorld(), e.getToWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(GameModeChangeShareHandlingEvent e) {
        cancel(e);
    }

    /**
     * loading on join
     */
    @EventHandler(ignoreCancelled = true)
    public void onRead(ReadOnlyShareHandlingEvent e) {
        cancel(e);
    }

    /**
     * saving on quit
     */
    @EventHandler(ignoreCancelled = true)
    public void onWrite(WriteOnlyShareHandlingEvent e) {
        cancel(e);
    }

    private static void cancel(ShareHandlingEvent e) {
        if (MultiverseSupport.ignoresInventory(e.getPlayer(), null, null)) e.setCancelled(true);
    }

}
