package com.christian34.survivalgames.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The statistics in a database: the queries for MySQL, run against H2 in MySQL mode.
 *
 * @author Christian34
 */
class SqlStorageTest {
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private SqlStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SqlStorage("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "",
                "sg_stats");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void addedStatisticsAreSummedUp() {
        storage.add(steve, "Steve", "survival-games", Stat.KILLS, 1);
        storage.add(steve, "Steve", "survival-games", Stat.KILLS, 2);
        storage.add(steve, "Steve2", "varo", Stat.WINS, 1);

        Map<String, PlayerStats> stats = storage.load(steve);
        assertEquals(2, stats.size());
        assertEquals(3, stats.get("survival-games").kills());
        assertEquals(1, stats.get("varo").wins());
        assertEquals("Steve2", stats.get("varo").name());
        assertTrue(storage.load(alex).isEmpty());
    }

    @Test
    void playersAreFoundByName() {
        storage.add(steve, "Steve", "varo", Stat.GAMES, 1);
        assertEquals(steve, storage.find("steve"));
        assertNull(storage.find("Alex"));
    }

    @Test
    void topSortsTheBestPlayersFirst() {
        storage.add(steve, "Steve", "survival-games", Stat.WINS, 2);
        storage.add(alex, "Alex", "survival-games", Stat.WINS, 1);
        storage.add(alex, "Alex", "varo", Stat.WINS, 3);
        storage.add(alex, "Alex", "varo", Stat.DEATHS, 1);

        List<PlayerStats> all = storage.top(Stat.WINS, null, 10);
        assertEquals(List.of("Alex", "Steve"), all.stream().map(PlayerStats::name).toList());
        assertEquals(4, all.getFirst().wins());

        List<PlayerStats> sg = storage.top(Stat.WINS, "survival-games", 10);
        assertEquals(List.of("Steve", "Alex"), sg.stream().map(PlayerStats::name).toList());
        assertEquals(1, storage.top(Stat.WINS, null, 1).size());
        assertEquals(List.of("Alex"), storage.top(Stat.DEATHS, null, 10).stream().map(PlayerStats::name).toList());
    }

    @Test
    void invalidTableNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SqlStorage("jdbc:h2:mem:x", "sa", "", "stats; DROP"));
    }

}
