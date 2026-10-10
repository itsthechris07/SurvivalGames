package com.christian34.survivalgames.integrations;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the state of the game on a join NPC of an NPC plugin (npcs.show-status).
 *
 * @author Christian34
 */
public interface NpcStatusDisplay {

    /**
     * @param id the id of the NPC in the NPC plugin (the key without "citizens:")
     * @return false if the NPC doesn't exist (any more) or isn't loaded yet (e.g. on start)
     */
    boolean exists(@NotNull String id);

    /**
     * @param id     the id of the NPC in the NPC plugin (the key without "citizens:")
     * @param shown  what this method returned last time for the NPC (saved in npcs.yml), null if nothing is shown
     * @param status the state of the game, null: removes it from the NPC
     * @return what is shown now, given back next time
     */
    @Nullable
    String showStatus(@NotNull String id, @Nullable String shown, @Nullable Component status);

}
