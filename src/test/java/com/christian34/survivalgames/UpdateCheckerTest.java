package com.christian34.survivalgames;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The update checker reads the releases on GitHub and reminds the admins.
 *
 * @author Christian34
 */
class UpdateCheckerTest extends PluginTestBase {
    private HttpServer github;

    private static String release(String tag, boolean prerelease, Instant published) {
        return """
                {"tag_name": "%s", "html_url": "https://github.com/itsthechris07/SurvivalGames/releases/tag/%s",
                 "draft": false, "prerelease": %s, "published_at": "%s"}""".formatted(tag, tag, prerelease, published);
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    @AfterEach
    void stopGithub() {
        if (github != null) github.stop(0);
    }

    /**
     * @return a checker that asks a fake GitHub with these releases
     */
    private UpdateChecker checker(String currentVersion, String... releases) throws IOException {
        byte[] body = ("[" + String.join(",", releases) + "]").getBytes(StandardCharsets.UTF_8);
        github = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        github.createContext("/releases", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        github.start();
        URI uri = URI.create("http://127.0.0.1:" + github.getAddress().getPort() + "/releases");
        UpdateChecker checker = new UpdateChecker(plugin, uri, currentVersion);
        server.getPluginManager().registerEvents(checker, plugin);
        return checker;
    }

    @Test
    void comparesVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.3"));
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.5"));
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.0-beta"), "a release is newer than its beta");
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0-beta", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    void ignoresPreReleasesAndOlderVersions() {
        String json = "[" + String.join(",",
                release("v2.0.0-beta", true, daysAgo(1)),
                release("v1.1.0", false, daysAgo(5)),
                release("v1.0.0", false, daysAgo(40)),
                release("v0.9.0", false, daysAgo(90))) + "]";
        UpdateChecker.Update update = UpdateChecker.parse(json, "1.0.0");
        assertNotNull(update);
        assertEquals("1.1.0", update.version(), "the leading v of the tag is removed");
        assertEquals(1, update.behind());
        assertEquals(5, update.daysSinceFirst());
        assertTrue(update.url().endsWith("/releases/tag/v1.1.0"), "the download leads to the release on GitHub");
        assertFalse(update.isOutdated());

        assertNull(UpdateChecker.parse(json, "1.1.0"), "the latest version is installed");
        assertNull(UpdateChecker.parse("[]", "1.0.0"), "no releases yet");
    }

    @Test
    void outdatedAfterSeveralReleasesOrAMonth() {
        String threeReleases = "[" + String.join(",",
                release("v1.3.0", false, daysAgo(1)),
                release("v1.2.0", false, daysAgo(2)),
                release("v1.1.0", false, daysAgo(3))) + "]";
        UpdateChecker.Update update = UpdateChecker.parse(threeReleases, "1.0.0");
        assertNotNull(update);
        assertEquals("1.3.0", update.version());
        assertEquals(3, update.behind());
        assertTrue(update.isOutdated());

        UpdateChecker.Update old = UpdateChecker.parse("[" + release("v1.1.0", false, daysAgo(31)) + "]", "1.0.0");
        assertNotNull(old);
        assertTrue(old.isOutdated(), "an update ignored for a month");
    }

    @Test
    void adminsAreRemindedOnEveryJoin() throws IOException {
        UpdateChecker checker = checker("1.0.0", release("v1.1.0", false, daysAgo(2)));
        assertNotNull(checker.check().join());

        PlayerMock admin = addAdmin("Admin");
        PlayerMock player = addPlayer("Player");
        // a few seconds after the join, so the message doesn't get lost
        assertTrue(messages(admin).stream().noneMatch(message -> message.contains("1.1.0")));
        server.getScheduler().performTicks(60);
        List<String> messages = messages(admin);
        assertContains(messages, "1.0.0 → 1.1.0");
        assertNotContains(messages, "Updates missed");
        assertTrue(messages(player).stream().noneMatch(message -> message.contains("1.1.0")), "only admins");

        // the next join (MockBukkit doesn't call the event again on reconnect)
        server.getPluginManager().callEvent(new PlayerJoinEvent(admin, Component.empty()));
        server.getScheduler().performTicks(60);
        assertContains(messages(admin), "1.0.0 → 1.1.0");
    }

    @Test
    void overdueUpdateIsLouder() throws IOException {
        UpdateChecker checker = checker("1.0.0", release("v1.1.0", false, daysAgo(45)));
        checker.check().join();
        PlayerMock admin = addAdmin("Admin");
        server.getScheduler().performTicks(60);
        assertContains(messages(admin), "Updates missed: 1, the first one was released 45 days ago");
    }

    @Test
    void latestVersionIsQuiet() throws IOException {
        UpdateChecker checker = checker("1.1.0", release("v1.1.0", false, daysAgo(2)));
        assertNull(checker.check().join());
        assertNull(checker.getUpdate());
        PlayerMock admin = addAdmin("Admin");
        server.getScheduler().performTicks(60);
        assertTrue(messages(admin).stream().noneMatch(message -> message.contains("new version")));
    }

    @Test
    void failedCheckIsHarmless() {
        UpdateChecker checker = new UpdateChecker(plugin, URI.create("http://127.0.0.1:1/releases"), "1.0.0");
        assertNull(checker.check().join());
    }

    @Test
    void testsDontAskGithub() {
        assertNull(plugin.getUpdateChecker().getUpdate(), "the tests are offline");
    }

}
