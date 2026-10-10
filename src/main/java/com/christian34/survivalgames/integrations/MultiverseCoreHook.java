package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.event.world.MVWorldImportedEvent;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;

import java.util.ArrayList;

/**
 * Multiverse-Core 5 (https://github.com/Multiverse/Multiverse-Core), see {@link MultiverseSupport}
 */
class MultiverseCoreHook implements Listener {
    private final SurvivalGames plugin;

    MultiverseCoreHook(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    /**
     * on start: worlds of games Multiverse knows from before (it may have loaded them again) - temporary ones are
     * unloaded (the plugin deletes them afterwards), the world of a Varo stays loaded for its game
     */
    void removeGameWorlds() {
        WorldManager worlds = MultiverseCoreApi.get().getWorldManager();
        for (MultiverseWorld world : new ArrayList<>(worlds.getWorlds())) {
            if (MultiverseSupport.isGameWorld(world.getName())) {
                remove(world, MultiverseSupport.isTemporary(world.getName()));
            }
        }
    }

    /**
     * e.g. /mv reload imports the worlds of running games: they stay loaded, but aren't managed by Multiverse
     */
    @EventHandler
    public void onImport(MVWorldImportedEvent e) {
        String name = e.getWorld().getName();
        if (!MultiverseSupport.isGameWorld(name)) return;
        Bukkit.getScheduler().runTask(plugin, () -> MultiverseCoreApi.get().getWorldManager().getWorld(name)
                .peek(world -> remove(world, false)));
    }

    private static void remove(MultiverseWorld world, boolean unload) {
        MultiverseCoreApi.get().getWorldManager()
                .removeWorld(RemoveWorldOptions.world(world).saveBukkitWorld(false).unloadBukkitWorld(unload))
                .onSuccess(name -> Debug.info("Removed the game world " + name + " from Multiverse"))
                .onFailure(failure -> Debug.warn("Couldn't remove the game world " + world.getName()
                        + " from Multiverse: " + failure.getFailureMessage()));
    }

}
