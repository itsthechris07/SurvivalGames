package com.christian34.survivalgames.integrations;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parties by AlessioDP (https://github.com/AlessioDP/Parties)
 */
class PartiesProvider implements PartyProvider {

    @Override
    public String getName() {
        return "Parties";
    }

    @Override
    @Nullable
    public Party getParty(@NotNull Player player) {
        PartyPlayer partyPlayer = Parties.getApi().getPartyPlayer(player.getUniqueId());
        if (partyPlayer == null || !partyPlayer.isInParty() || partyPlayer.getPartyId() == null) return null;
        com.alessiodp.parties.api.interfaces.Party party = Parties.getApi().getParty(partyPlayer.getPartyId());
        if (party == null || party.getLeader() == null) return null;
        Set<UUID> members = new HashSet<>(party.getMembers());
        members.add(party.getLeader());
        return new Party(party.getLeader(), members);
    }

}
