package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.UsageStats;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quells (modules.quell): every n-th round of an arena (or by chance) is a special round with one of the rules - a
 * rule changes settings of the game type for this round (e.g. no grace period). The players learn the rule in the
 * lobby and when the game begins. Admins can choose the rule of the next round (/sg game &lt;arena&gt; quell &lt;rule&gt;).
 *
 * @author Christian34
 */
public class Quell extends GameModule {
    public static final String MODULE = "quell";
    /**
     * the rule an admin has chosen for the next round of an arena (lower case name)
     */
    private final Map<String, String> chosen = new ConcurrentHashMap<>();

    public Quell(SurvivalGames plugin) {
        super(plugin, MODULE);
    }

    private boolean isEnabled(Arena arena) {
        return config().getBoolean("enabled", true)
                && config().getStringList("games").stream().anyMatch(type -> type.equalsIgnoreCase(arena.getType().getKey()));
    }

    /**
     * @return the names of the rules (config: rules)
     */
    public List<String> getRules() {
        ConfigurationSection rules = config().getConfigurationSection("rules");
        return rules == null ? List.of() : new ArrayList<>(rules.getKeys(false));
    }

    /**
     * @return the name of the rule as in the config (any case), null if there is no such rule
     */
    @Nullable
    public String find(String rule) {
        return getRules().stream().filter(other -> other.equalsIgnoreCase(rule)).findFirst().orElse(null);
    }

    /**
     * the next round of the arena is a quell with the rule
     *
     * @return false if there is no such rule
     */
    public boolean choose(Arena arena, String rule) {
        String name = find(rule);
        if (name == null) return false;
        chosen.put(arena.getName().toLowerCase(Locale.ROOT), name);
        return true;
    }

    /**
     * a new round of the arena: is it a quell?
     *
     * @return the rule of the round, null: a normal round
     */
    @Nullable
    public String nextRound(Arena arena) {
        String rule = chosen.remove(arena.getName().toLowerCase(Locale.ROOT));
        if (!isEnabled(arena) && rule == null) return null;
        int round = arena.addRound();
        List<String> rules = getRules();
        if (rule == null && !rules.isEmpty()) {
            int every = config().getInt("every", 25);
            boolean quell = every > 0 && round % every == 0
                    || ThreadLocalRandom.current().nextInt(100) < config().getInt("chance");
            if (quell) rule = rules.get(ThreadLocalRandom.current().nextInt(rules.size()));
        }
        if (rule != null) plugin.getUsageStats().used(UsageStats.Feature.QUELL);
        return rule;
    }

    /**
     * @return the settings of the game type with the changes of the rule
     */
    public ConfigurationSection apply(ConfigurationSection settings, String rule) {
        MemoryConfiguration copy = new MemoryConfiguration();
        settings.getValues(true).forEach((key, value) -> {
            if (!(value instanceof ConfigurationSection)) copy.set(key, value);
        });
        ConfigurationSection changes = config().getConfigurationSection("rules." + rule);
        if (changes != null) {
            changes.getValues(true).forEach((key, value) -> {
                if (!(value instanceof ConfigurationSection)) copy.set(key, value);
            });
        }
        return copy;
    }

    /**
     * @return the description of the rule: the message quell_rule_&lt;rule&gt; (dashes as underscores), otherwise the
     * name of the rule
     */
    public static Component describe(String rule) {
        String key = "quell_rule_" + rule.toLowerCase(Locale.ROOT).replace('-', '_');
        for (LangText text : LangText.values()) {
            if (text.getKey().equals(key)) return I.i18n(text);
        }
        return Component.text(rule);
    }

}
