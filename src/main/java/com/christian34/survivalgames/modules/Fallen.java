package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The fallen (modules.fallen): a cannon shot everybody in the game hears for every player who is out, and every few
 * minutes (Varo: when a new day begins) a list of the players who have fallen since the last one.
 *
 * @author Christian34
 */
public class Fallen {
    public static final String MODULE = "fallen";
    private final SurvivalGames plugin;

    public Fallen(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public boolean isUsed(Game game) {
        return plugin.isModuleEnabled(MODULE, game);
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection("modules." + MODULE);
        return section != null ? section : plugin.getFileManager().getConfigFile().getData()
                .createSection("modules." + MODULE);
    }

    /**
     * the participant is out: the cannon fires, he is on the next list of the fallen
     */
    public void onEliminated(Game game, Participant participant) {
        if (!isUsed(game)) return;
        game.getFallen().add(participant.getUuid());
        if (game instanceof VaroGame varo && !varo.isStopped()) varo.save();
        if (!config().getBoolean("cannon", true)) return;
        for (GamePlayer member : game.getMembers()) {
            Player player = member.getPlayer();
            // far away: deep and quiet
            player.playSound(player.getLocation().add(0, 30, 0), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
        }
    }

    /**
     * every second of a running game: the list of the fallen every minutes minutes (not in a Varo, see
     * {@link #showFallen(Game)})
     */
    public void tick(Game game) {
        int minutes = config().getInt("minutes", 5);
        if (!isUsed(game) || game instanceof VaroGame || minutes <= 0 || game.getState() != GameState.RUNNING) return;
        int seconds = game.getRunningSeconds();
        if (seconds > 0 && seconds % (minutes * 60) == 0) showFallen(game);
    }

    /**
     * the players who have fallen since the last list (also: nobody)
     */
    public void showFallen(Game game) {
        if (!isUsed(game)) return;
        List<UUID> fallen = new ArrayList<>(game.getFallen());
        game.getFallen().clear();
        if (fallen.isEmpty()) {
            game.announce(LangText.FALLEN_NONE);
            return;
        }
        List<Component> names = new ArrayList<>();
        for (UUID uuid : fallen) {
            GameTeam team = game.getTeam(uuid);
            names.add(game.getArena().getTeamSize() > 1 && team != null
                    ? I.i18n(LangText.FALLEN_ENTRY_TEAM, game.getName(uuid), team.getDisplayName())
                    : I.i18n(LangText.FALLEN_ENTRY, game.getName(uuid)));
        }
        game.announce(LangText.FALLEN, fallen.size(), Component.join(JoinConfiguration.commas(true), names));
        Title title = Title.title(I.i18n(LangText.FALLEN_TITLE, fallen.size()), Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500)));
        for (GamePlayer member : game.getMembers()) {
            member.getPlayer().showTitle(title);
        }
    }

}
