package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parties of party plugins (Parties, Party and Friends): the leader takes the members of their party into the game
 * (parties.join-together) and parties play in the same team (parties.same-team).
 *
 * @author Christian34
 */
public class PartySupport {
    private final SurvivalGames plugin;
    private final List<PartyProvider> providers = new ArrayList<>();
    private boolean failed;

    public PartySupport(SurvivalGames plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("Parties")) addProvider(new PartiesProvider());
        if (Bukkit.getPluginManager().isPluginEnabled(PartyAndFriendsProvider.PLUGIN)) addProvider(new PartyAndFriendsProvider());
    }

    public void addProvider(@NotNull PartyProvider provider) {
        providers.add(provider);
        Debug.info("Hooked into " + provider.getName() + " (parties)");
    }

    public List<PartyProvider> getProviders() {
        return providers;
    }

    private boolean isEnabled() {
        return !providers.isEmpty() && plugin.getFileManager().getConfigFile().getBoolean("parties.enabled");
    }

    /**
     * @return the party of the player, null if he isn't in one (or parties are disabled)
     */
    @Nullable
    public PartyProvider.Party getParty(@NotNull Player player) {
        if (!isEnabled()) return null;
        for (PartyProvider provider : providers) {
            try {
                PartyProvider.Party party = provider.getParty(player);
                if (party != null) return party;
            } catch (Exception | LinkageError ex) {
                // an incompatible version of the party plugin mustn't break joining games
                if (!failed) Debug.warn("Couldn't get the party of " + player.getName() + " from " + provider.getName() + ": " + ex);
                failed = true;
            }
        }
        return null;
    }

    /**
     * @return the number of players in the party of the player (1 if he isn't in one)
     */
    public int getPartySize(@NotNull Player player) {
        PartyProvider.Party party = getParty(player);
        return party == null ? 1 : party.members().size();
    }

    /**
     * @return the online members of this server the party leader takes into the game (parties.join-together), empty
     * if the player isn't the leader of a party
     */
    public List<Player> getFollowers(@NotNull Player leader) {
        List<Player> followers = new ArrayList<>();
        if (!plugin.getFileManager().getConfigFile().getBoolean("parties.join-together")) return followers;
        PartyProvider.Party party = getParty(leader);
        if (party == null || !party.isLeader(leader)) return followers;
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && !player.equals(leader)) followers.add(player);
        }
        return followers;
    }

    /**
     * @return the id of the party the team assignment keeps together (parties.same-team), null if none
     */
    @Nullable
    public UUID getTeamParty(@NotNull Player player) {
        if (!plugin.getFileManager().getConfigFile().getBoolean("parties.same-team")) return null;
        PartyProvider.Party party = getParty(player);
        return party == null ? null : party.leader();
    }

}
