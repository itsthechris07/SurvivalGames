package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The placeholders for MiniPlaceholders (https://github.com/MiniPlaceholders/MiniPlaceholders), the same as those of
 * PlaceholderAPI ({@link Placeholders}) with arguments instead of underscores: &lt;sg_arena&gt;, &lt;sg_kills&gt;, ...
 * (of the viewer), &lt;sg_stats:wins&gt;, &lt;sg_stats:varo:kills&gt;, &lt;sg_top:wins:1:name&gt;, &lt;sg_games&gt;,
 * &lt;sg_game_players:castle&gt;, &lt;sg_game_max_players:castle&gt; and &lt;sg_game_state:castle&gt;.
 *
 * @author Christian34
 */
public class SgMiniPlaceholders {
    private final SurvivalGames plugin;

    public SgMiniPlaceholders(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public Expansion register() {
        Expansion.Builder builder = Expansion.builder("sg")
                .author("Christian34")
                .version(plugin.getPluginMeta().getVersion());
        for (String key : Placeholders.PLAYER_GAME) {
            builder.audiencePlaceholder(Player.class, key, (player, queue, context) ->
                    tag(Placeholders.value(plugin, player, key)));
        }
        builder.audiencePlaceholder(Player.class, "stats", (player, queue, context) ->
                tag(Placeholders.value(plugin, player, "stats_" + String.join("_", arguments(queue, 2)))));
        builder.globalPlaceholder("top", (queue, context) ->
                tag(Placeholders.value(plugin, null, "top_" + String.join("_", arguments(queue, 3)))));
        builder.globalPlaceholder("games", (queue, context) -> tag(Placeholders.value(plugin, null, "games")));
        for (String kind : Placeholders.ARENA_GAME) {
            builder.globalPlaceholder("game_" + kind, (queue, context) ->
                    queue.hasNext() ? tag(Placeholders.arenaGame(plugin, kind, queue.pop().value())) : null);
        }
        Expansion expansion = builder.build();
        expansion.register();
        return expansion;
    }

    private static List<String> arguments(ArgumentQueue queue, int count) {
        List<String> arguments = new ArrayList<>();
        while (queue.hasNext() && arguments.size() < count) {
            arguments.add(queue.pop().value());
        }
        return arguments;
    }

    /**
     * @param value with legacy colors (the team, the state, ...)
     */
    @Nullable
    private static Tag tag(@Nullable String value) {
        return value == null ? null : Tag.selfClosingInserting(LegacyComponentSerializer.legacySection().deserialize(value));
    }

}
