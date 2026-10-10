package com.christian34.survivalgames.integrations;

import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayer;
import de.simonsator.partyandfriends.spigot.api.pafplayers.PAFPlayerManager;
import de.simonsator.partyandfriends.spigot.api.party.PartyManager;
import de.simonsator.partyandfriends.spigot.api.party.PlayerParty;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Party and Friends by Simonsator (https://github.com/simonsator/BungeecordPartyAndFriends): the parties live on the
 * proxy, game servers read them with the "Spigot Party API for Party and Friends" (plugin Spigot-Party-API-PAF)
 */
class PartyAndFriendsProvider implements PartyProvider {
    static final String PLUGIN = "Spigot-Party-API-PAF";

    @Override
    public String getName() {
        return "Party and Friends";
    }

    @Override
    @Nullable
    public Party getParty(@NotNull Player player) {
        PAFPlayer pafPlayer = PAFPlayerManager.getInstance().getPlayer(player.getUniqueId());
        if (pafPlayer == null) return null;
        PlayerParty party = PartyManager.getInstance().getParty(pafPlayer);
        if (party == null || party.getLeader() == null) return null;
        Set<UUID> members = new HashSet<>();
        for (PAFPlayer member : party.getAllPlayers()) {
            members.add(member.getUniqueId());
        }
        members.add(party.getLeader().getUniqueId());
        return new Party(party.getLeader().getUniqueId(), members);
    }

}
