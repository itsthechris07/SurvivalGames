package com.christian34.survivalgames;

import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the releases on GitHub for a new version (at the start and every 12 hours) and tells the console and the
 * admins when they join - every time, so the update isn't forgotten. The longer the server stays on an old version,
 * the louder it gets (see {@link Update#isOutdated()}).
 *
 * @author Christian34
 */
public class UpdateChecker implements Listener {
    static final URI RELEASES_URI = URI.create("https://api.github.com/repos/itsthechris07/SurvivalGames/releases?per_page=30");
    static final String RELEASES_PAGE = "https://github.com/itsthechris07/SurvivalGames/releases";
    /**
     * this many releases behind or this long since the first newer release: the update is overdue
     */
    static final int OUTDATED_RELEASES = 3;
    static final Duration OUTDATED_AFTER = Duration.ofDays(30);
    private static final long CHECK_INTERVAL_TICKS = 12 * 60 * 60 * 20L;
    private static final long JOIN_DELAY_TICKS = 3 * 20L;
    private static final Pattern NUMBERS = Pattern.compile("\\d+");
    private final SurvivalGames plugin;
    private final URI releasesUri;
    private final String currentVersion;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Nullable
    private volatile Update update;

    public UpdateChecker(SurvivalGames plugin) {
        this(plugin, RELEASES_URI, plugin.getPluginMeta().getVersion());
    }

    UpdateChecker(SurvivalGames plugin, URI releasesUri, String currentVersion) {
        this.plugin = plugin;
        this.releasesUri = releasesUri;
        this.currentVersion = currentVersion;
    }

    /**
     * -Dsg.offline=true: GitHub isn't asked (tests)
     */
    public static boolean isOffline() {
        return Boolean.getBoolean("sg.offline");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (isOffline() || !plugin.getFileManager().getConfigFile().getBoolean("update-checker.enabled")) return;
        // the first check right away (async), then every 12 hours
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> check(), 0L, CHECK_INTERVAL_TICKS);
    }

    /**
     * asks GitHub for the releases; logs and announces a newer one
     *
     * @return the newer release, null if this is the latest version (or the check failed)
     */
    CompletableFuture<@Nullable Update> check() {
        HttpRequest request = HttpRequest.newBuilder(releasesUri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SurvivalGames/" + currentVersion)
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, error) -> {
            if (error != null || response.statusCode() != 200) {
                Debug.warn("Couldn't check for updates of SurvivalGames: "
                        + (error != null ? error.getMessage() : "HTTP " + response.statusCode()));
                return update;
            }
            Update found;
            try {
                found = parse(response.body(), currentVersion);
            } catch (RuntimeException ex) {
                Debug.warn("Couldn't check for updates of SurvivalGames: " + ex);
                return update;
            }
            Update previous = update;
            this.update = found;
            if (found == null) return null;
            boolean newRelease = previous == null || !previous.version().equals(found.version());
            // the console hears about it at the start and then every 12 hours once the update is overdue
            if (newRelease || found.isOutdated()) Debug.warn(found.consoleText(currentVersion));
            if (newRelease && previous != null) {
                // a release came out while the server is running: the admins online learn it now
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::notify));
            }
            return found;
        });
    }

    /**
     * @param json the releases from the GitHub api
     * @return the latest release newer than the current version, null if there is none
     */
    @Nullable
    static Update parse(String json, String currentVersion) {
        List<Release> newer = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject release = element.getAsJsonObject();
            // drafts aren't visible anyway, pre-releases aren't offered to servers
            if (bool(release, "draft") || bool(release, "prerelease") || !release.has("tag_name")) continue;
            String version = stripV(release.get("tag_name").getAsString());
            if (!isNewer(version, currentVersion)) continue;
            newer.add(new Release(version, string(release, "html_url", RELEASES_PAGE), published(release)));
        }
        if (newer.isEmpty()) return null;
        Release latest = newer.getFirst();
        Instant firstPublished = null;
        for (Release release : newer) {
            if (isNewer(release.version(), latest.version())) latest = release;
            if (release.published() != null && (firstPublished == null || release.published().isBefore(firstPublished))) {
                firstPublished = release.published();
            }
        }
        return new Update(latest.version(), latest.url(), newer.size(), firstPublished);
    }

    /**
     * compares the numbers of both versions (e.g. 2.0.1 &gt; 2.0.0 &gt; 1.8.13), a release is newer than its betas
     */
    static boolean isNewer(@NotNull String latest, @NotNull String current) {
        int[] a = numbers(latest.split("-")[0]), b = numbers(current.split("-")[0]);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return !latest.contains("-") && current.contains("-");
    }

    private static int[] numbers(String version) {
        Matcher matcher = NUMBERS.matcher(version);
        return matcher.results().mapToInt(result -> {
            try {
                return Integer.parseInt(result.group());
            } catch (NumberFormatException ex) {
                return 0;
            }
        }).toArray();
    }

    private static String stripV(String tag) {
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    @Nullable
    private static Instant published(JsonObject release) {
        try {
            return release.has("published_at") && !release.get("published_at").isJsonNull()
                    ? Instant.parse(release.get("published_at").getAsString()) : null;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * @return the newer release, null if this is the latest version or it wasn't checked
     */
    @Nullable
    public Update getUpdate() {
        return update;
    }

    /**
     * tells an admin about the update
     */
    void notify(Player player) {
        Update found = update;
        if (found == null || !player.isOnline() || !player.hasPermission(SurvivalGames.ADMIN_PERMISSION)) return;
        Component download = I.button(LangText.UPDATE_DOWNLOAD, ClickEvent.openUrl(found.url()))
                .hoverEvent(HoverEvent.showText(I.i18n(LangText.UPDATE_DOWNLOAD_HOVER, found.url())));
        player.sendMessage(I.prefixed(LangText.UPDATE_AVAILABLE, currentVersion, found.version())
                .appendSpace().append(download));
        if (found.isOutdated()) {
            player.sendMessage(I.prefixed(LangText.UPDATE_OUTDATED, found.behind(), found.daysSinceFirst()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (update == null || !player.hasPermission(SurvivalGames.ADMIN_PERMISSION)) return;
        // after the join messages of the server and other plugins, so it doesn't get lost
        Bukkit.getScheduler().runTaskLater(plugin, () -> notify(player), JOIN_DELAY_TICKS);
    }

    private record Release(String version, String url, @Nullable Instant published) {
    }

    /**
     * a newer release
     *
     * @param version        the latest version
     * @param url            its page on GitHub
     * @param behind         releases newer than the installed version
     * @param firstPublished when the first of them was published (null: unknown)
     */
    public record Update(String version, String url, int behind, @Nullable Instant firstPublished) {

        /**
         * @return true if the server missed several releases or the update came out a while ago
         */
        public boolean isOutdated() {
            return behind >= OUTDATED_RELEASES || daysSinceFirst() >= OUTDATED_AFTER.toDays();
        }

        public long daysSinceFirst() {
            return firstPublished == null ? 0 : Duration.between(firstPublished, Instant.now()).toDays();
        }

        String consoleText(String currentVersion) {
            String text = "A new version of SurvivalGames is available: " + currentVersion + " -> " + version
                    + " (" + behind + (behind == 1 ? " release" : " releases") + " behind";
            if (firstPublished != null) text += ", the first one came out " + daysSinceFirst() + " days ago";
            return text + "). Download: " + url;
        }

    }

}
