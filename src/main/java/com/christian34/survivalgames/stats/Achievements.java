package com.christian34.survivalgames.stats;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.commands.CommandSurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Rewards;
import com.christian34.survivalgames.integrations.Discord;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gives the players their achievements (stats.achievements in config.yml) - they are kept with the statistics, so
 * there are none without them.
 *
 * @author Christian34
 */
public class Achievements {
    private final SurvivalGames plugin;

    public Achievements(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getStats().isEnabled()
                && plugin.getFileManager().getConfigFile().getBoolean("stats.achievements.enabled");
    }

    /**
     * the player gets the achievement if he doesn't have it yet (he reads it in the chat if he is online)
     */
    public void unlock(UUID uuid, String name, Achievement achievement) {
        if (!isEnabled()) return;
        plugin.getStats().unlock(uuid, name, achievement.getKey(), () -> {
            reward(uuid, name, achievement);
            plugin.getDiscord().post(Discord.Event.ACHIEVEMENT, null, I.plain(LangText.DISCORD_ACHIEVEMENT, name,
                    I.i18n(achievement.getDisplayName()), I.i18n(achievement.getDescription())));
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            player.sendMessage(I.prefixed(player, LangText.ACHIEVEMENT_UNLOCKED, I.i18n(achievement.getDisplayName()),
                    I.i18n(achievement.getDescription())));
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
        });
    }

    /**
     * the reward for the achievement (stats.achievements.money and .commands) - also if the player is offline
     */
    private void reward(UUID uuid, String name, Achievement achievement) {
        ConfigFile config = plugin.getFileManager().getConfigFile();
        double money = config.getDouble("stats.achievements.money");
        if (money > 0 && plugin.getEconomySupport().pay(uuid, money)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(I.prefixed(player, LangText.ACHIEVEMENT_REWARD,
                        plugin.getEconomySupport().format(money), I.i18n(achievement.getDisplayName())));
            }
        }
        List<String> commands = new ArrayList<>(config.getData().getStringList("stats.achievements.commands.all"));
        commands.addAll(config.getData().getStringList("stats.achievements.commands." + achievement.getKey()));
        Rewards.dispatch(plugin, commands, Map.of("{player}", name, "{uuid}", uuid.toString(),
                "{achievement}", achievement.getKey()));
    }

    /**
     * the statistics of an online player have changed or have been loaded
     *
     * @param total his statistics of all game types
     */
    void onStats(PlayerStats total) {
        for (Achievement achievement : Achievement.values()) {
            Stat stat = achievement.getStat();
            if (stat != null && total.get(stat) >= achievement.getGoal()) {
                unlock(total.uuid(), total.name(), achievement);
            }
        }
    }

    /**
     * the participant has killed another one
     */
    public void onKill(Game game, Participant killer) {
        if (!isEnabled()) return;
        int kills = killer.getKills();
        if (game.getParticipants().stream().mapToInt(Participant::getKills).sum() == 1) {
            unlock(killer.getUuid(), killer.getName(), Achievement.FIRST_BLOOD);
        }
        if (kills >= Achievement.RAMPAGE_KILLS) unlock(killer.getUuid(), killer.getName(), Achievement.RAMPAGE);
        if (kills >= Achievement.UNSTOPPABLE_KILLS) unlock(killer.getUuid(), killer.getName(), Achievement.UNSTOPPABLE);
    }

    /**
     * the team has won the game (its players are still in the game)
     */
    public void onWin(Game game, GameTeam winner) {
        if (!isEnabled()) return;
        for (UUID member : winner.getMembers()) {
            Participant participant = game.getParticipant(member);
            if (participant == null || !participant.isAlive()) continue;
            String name = participant.getName();
            if (participant.getKills() == 0) unlock(member, name, Achievement.PACIFIST);
            if (game.getType() == GameType.VARO) {
                unlock(member, name, Achievement.VARO_SURVIVOR);
                if (participant.getStrikes().isEmpty()) unlock(member, name, Achievement.VARO_SPOTLESS);
            }
            Player player = Bukkit.getPlayer(member);
            if (player == null || plugin.getUser(player).getGame() != game) continue;
            boolean armor = false;
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item != null && !item.getType().isAir()) armor = true;
            }
            if (!armor) unlock(member, name, Achievement.NAKED);
            if (player.getHealth() <= 2) unlock(member, name, Achievement.LAST_BREATH);
        }
    }

    /**
     * @param unlocked the achievements of the player (key - when he got it)
     * @return how many of the achievements there are he has
     */
    public static int count(Map<String, Long> unlocked) {
        int count = 0;
        for (Achievement achievement : Achievement.values()) {
            if (unlocked.containsKey(achievement.getKey())) count++;
        }
        return count;
    }

    /**
     * @return the progress of the achievement ("37/50"), null if it isn't reached with a statistic or is unlocked
     */
    @Nullable
    private static String progress(Achievement achievement, StatsManager.Profile profile) {
        Stat stat = achievement.getStat();
        if (stat == null || profile.total() == null || profile.unlocked().containsKey(achievement.getKey())) return null;
        return Math.min(profile.total().get(stat), achievement.getGoal()) + "/" + achievement.getGoal();
    }

    /**
     * shows the player his achievements in a menu
     */
    public void showMenu(Player player, StatsManager.Profile profile) {
        Map<String, Long> unlocked = profile.unlocked();
        Menu menu = new Menu(I.i18n(LangText.GUI_ACHIEVEMENTS, count(unlocked), Achievement.values().length));
        for (Achievement achievement : Achievement.values()) {
            Long time = unlocked.get(achievement.getKey());
            List<Component> lore = new ArrayList<>();
            lore.add(I.i18n(achievement.getDescription()));
            lore.add(time == null ? I.i18n(LangText.GUI_ACHIEVEMENT_LOCKED)
                    : I.i18n(LangText.GUI_ACHIEVEMENT_UNLOCKED, CommandSurvivalGames.formatDate(time)));
            String progress = progress(achievement, profile);
            if (progress != null) lore.add(I.i18n(LangText.GUI_ACHIEVEMENT_PROGRESS, progress));
            menu.button(new ItemStack(time == null ? Material.GRAY_DYE : achievement.getIcon()),
                    I.i18n(achievement.getDisplayName()).color(time == null ? NamedTextColor.GRAY : NamedTextColor.GOLD),
                    lore, clicker -> {
                    });
        }
        menu.show(player);
    }

    /**
     * lists the achievements of a player in the chat
     */
    public void sendList(CommandSender sender, String name, StatsManager.Profile profile) {
        Map<String, Long> unlocked = profile.unlocked();
        sender.sendMessage(I.i18n(LangText.ACHIEVEMENTS_TITLE, name, count(unlocked), Achievement.values().length));
        for (Achievement achievement : Achievement.values()) {
            Long time = unlocked.get(achievement.getKey());
            Component displayName = I.i18n(achievement.getDisplayName());
            Component description = I.i18n(achievement.getDescription());
            String progress = progress(achievement, profile);
            sender.sendMessage(time != null ? I.i18n(LangText.ACHIEVEMENTS_ENTRY_UNLOCKED, displayName, description,
                    CommandSurvivalGames.formatDate(time))
                    : progress != null ? I.i18n(LangText.ACHIEVEMENTS_ENTRY_PROGRESS, displayName, description, progress)
                    : I.i18n(LangText.ACHIEVEMENTS_ENTRY_LOCKED, displayName, description));
        }
    }

}
