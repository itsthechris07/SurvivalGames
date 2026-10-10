package com.christian34.survivalgames.integrations;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A party plugin (players form groups that play together)
 *
 * @author Christian34
 */
public interface PartyProvider {

    /**
     * @return the name of the plugin
     */
    String getName();

    /**
     * @return the party of the player, null if he isn't in one
     */
    @Nullable
    Party getParty(@NotNull Player player);

    /**
     * @param leader  the party leader
     * @param members all members, including the leader (also those on other servers of a network)
     */
    record Party(UUID leader, Set<UUID> members) {

        public boolean isLeader(Player player) {
            return leader.equals(player.getUniqueId());
        }

    }

}
