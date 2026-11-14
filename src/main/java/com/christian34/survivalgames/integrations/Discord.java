package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.lang.I;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Posts what happens in the games to a Discord channel (discord in config.yml): an embed per event through a webhook.
 * The messages are the texts of the game (messages.yml) without colors. They are sent one after another by an own
 * thread, a slow or missing Discord doesn't hold up the server.
 *
 * @author Christian34
 */
public class Discord {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final SurvivalGames plugin;
    @Nullable
    private ExecutorService service;
    @Nullable
    private HttpClient client;
    /**
     * the tests get the JSON instead of Discord
     */
    @Nullable
    private Consumer<String> testSender;
    private long lastWarning;

    public enum Event {
        /**
         * a game has begun
         */
        START("start", 0x55FF55),
        /**
         * a team has won
         */
        WIN("win", 0xFFAA00),
        /**
         * a player is out of the game
         */
        ELIMINATION("elimination", 0xFF5555),
        /**
         * a new day of a Varo
         */
        VARO_DAY("varo-day", 0x00AAAA),
        /**
         * a strike of a Varo
         */
        STRIKE("strike", 0xAA0000),
        /**
         * a player has unlocked an achievement
         */
        ACHIEVEMENT("achievement", 0xFF55FF),
        /**
         * a season of the statistics is over
         */
        SEASON("season", 0x5555FF);

        private final String key;
        private final int color;

        Event(String key, int color) {
            this.key = key;
            this.color = color;
        }

        /**
         * the name in discord.events
         */
        public String getKey() {
            return key;
        }
    }

    public Discord(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    private ConfigFile config() {
        return plugin.getFileManager().getConfigFile();
    }

    /**
     * only for the tests: the JSON of the messages goes here instead of Discord
     */
    public void setSender(@Nullable Consumer<String> sender) {
        this.testSender = sender;
    }

    /**
     * @param game null: an event that doesn't belong to a game (achievements, seasons)
     * @return true if the event is posted (discord.enabled, a webhook, the event in discord.events and the game type
     * in discord.games)
     */
    public boolean isPosted(Event event, @Nullable Game game) {
        if (!config().getBoolean("discord.enabled")) return false;
        String url = config().getString("discord.webhook-url");
        if (url == null || url.isBlank()) return false;
        if (config().getData().getStringList("discord.events").stream().noneMatch(event.key::equalsIgnoreCase)) {
            return false;
        }
        return game == null || config().getData().getStringList("discord.games").stream()
                .anyMatch(type -> type.equalsIgnoreCase(game.getType().getKey()));
    }

    /**
     * posts the message if the event is posted (see {@link #isPosted})
     *
     * @param game the title of the embed is its game type and arena
     */
    public void post(Event event, @Nullable Game game, String message) {
        if (!isPosted(event, game)) return;
        String title = game == null ? null
                : I.plain(I.i18n(game.getType().getDisplayName())) + " - " + I.plain(game.getArena().getDisplayName());
        String json = json(title, message, event.color);
        if (testSender != null) {
            testSender.accept(json);
            return;
        }
        String url = String.valueOf(config().getString("discord.webhook-url")).trim();
        if (!url.startsWith("https://")) {
            warn("discord.webhook-url has to start with https://");
            return;
        }
        try {
            service().execute(() -> send(url, json));
        } catch (RuntimeException ex) {
            warn("Couldn't post to Discord: " + ex);
        }
    }

    private synchronized ExecutorService service() {
        if (service == null) {
            this.service = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SurvivalGames Discord");
                thread.setDaemon(true);
                return thread;
            });
        }
        return service;
    }

    /**
     * the JSON of the webhook: one embed, with discord.username and discord.avatar-url
     */
    String json(@Nullable String title, String description, int color) {
        StringBuilder json = new StringBuilder("{");
        String username = config().getString("discord.username");
        if (username != null && !username.isBlank()) json.append("\"username\":").append(quote(username)).append(',');
        String avatar = config().getString("discord.avatar-url");
        if (avatar != null && !avatar.isBlank()) json.append("\"avatar_url\":").append(quote(avatar)).append(',');
        // nobody is pinged by a name like @everyone
        json.append("\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        if (title != null) json.append("\"title\":").append(quote(title)).append(',');
        json.append("\"description\":").append(quote(description)).append(",\"color\":").append(color).append("}]}");
        return json.toString();
    }

    static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    /**
     * on the thread of the webhook: waits once if Discord asks for it (too many messages)
     */
    private void send(String url, String json) {
        try {
            if (client == null) this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("User-Agent", "SurvivalGames")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt == 0) {
                    double seconds = response.headers().firstValue("Retry-After").map(Discord::parse).orElse(2.0);
                    Thread.sleep((long) (Math.min(seconds, 30) * 1000));
                    continue;
                }
                if (response.statusCode() >= 300) {
                    warn("Discord answered " + response.statusCode() + ": " + response.body());
                }
                return;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            warn("Couldn't post to Discord: " + ex);
        }
    }

    private static double parse(String number) {
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException ex) {
            return 2;
        }
    }

    /**
     * at most once a minute, a wrong webhook would fill the console
     */
    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 60_000) return;
        this.lastWarning = now;
        Debug.warn(message);
    }

    /**
     * the messages that are still waiting are sent (a few seconds at most) - the plugin is disabled
     */
    public void close() {
        ExecutorService service;
        synchronized (this) {
            service = this.service;
            this.service = null;
        }
        if (service == null) return;
        service.shutdown();
        try {
            if (!service.awaitTermination(5, TimeUnit.SECONDS)) service.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
