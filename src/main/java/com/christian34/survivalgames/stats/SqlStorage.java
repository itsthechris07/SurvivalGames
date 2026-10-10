package com.christian34.survivalgames.stats;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The statistics in a MySQL/MariaDB database (Paper ships the driver): one row per player and game type. Several
 * servers of a network can use the same table.
 *
 * @author Christian34
 */
public class SqlStorage implements StatsStorage {
    private static final Pattern TABLE = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private final String url;
    private final String user;
    private final String password;
    private final String table;
    @Nullable
    private Connection connection;

    public SqlStorage(String url, String user, String password, String table) {
        if (!TABLE.matcher(table).matches()) throw new IllegalArgumentException("Invalid table name '" + table + "'");
        this.url = url;
        this.user = user;
        this.password = password;
        this.table = table;
    }

    /**
     * @return the connection, opened again if it was closed (e.g. by the timeout of the database)
     */
    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            close();
            this.connection = DriverManager.getConnection(url, user, password);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "uuid CHAR(36) NOT NULL, type VARCHAR(32) NOT NULL, name VARCHAR(16) NOT NULL, "
                        + "games INT NOT NULL DEFAULT 0, wins INT NOT NULL DEFAULT 0, "
                        + "kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, rating INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, type))");
            }
            addRatingColumn(connection);
        }
        return connection;
    }

    /**
     * tables of older versions have no rating yet (MySQL has no ADD COLUMN IF NOT EXISTS)
     */
    private void addRatingColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT rating FROM " + table + " WHERE 1 = 0").close();
        } catch (SQLException missing) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN rating INT NOT NULL DEFAULT 0");
            }
        }
    }

    private RuntimeException failed(SQLException ex) {
        // the next access opens a new connection
        close();
        return new IllegalStateException("Database error: " + ex.getMessage(), ex);
    }

    @Override
    public void add(UUID uuid, String name, String type, Stat stat, int amount) {
        String column = stat.getKey();
        try (PreparedStatement statement = connection().prepareStatement("INSERT INTO " + table
                + " (uuid, type, name, " + column + ") VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), "
                + column + " = " + column + " + VALUES(" + column + ")")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type);
            statement.setString(3, name);
            statement.setInt(4, amount);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw failed(ex);
        }
    }

    private static PlayerStats read(ResultSet result) throws SQLException {
        return new PlayerStats(UUID.fromString(result.getString("uuid")), result.getString("name"),
                result.getInt("games"), result.getInt("wins"), result.getInt("kills"), result.getInt("deaths"),
                result.getInt("rating"));
    }

    @Override
    public Map<String, PlayerStats> load(UUID uuid) {
        Map<String, PlayerStats> stats = new HashMap<>();
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT uuid, type, name, games, wins, kills, deaths, rating FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    stats.put(result.getString("type"), read(result));
                }
            }
        } catch (SQLException ex) {
            throw failed(ex);
        }
        return stats;
    }

    @Override
    @Nullable
    public UUID find(String name) {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT uuid FROM " + table + " WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString("uuid")) : null;
            }
        } catch (SQLException ex) {
            throw failed(ex);
        }
    }

    @Override
    public List<PlayerStats> top(Stat stat, @Nullable String type, int limit) {
        String column = stat.getKey();
        // see PlayerStats#isRanked
        String ranked = stat == Stat.RATING ? "games" : column;
        String sql = type != null
                ? "SELECT uuid, name, games, wins, kills, deaths, rating FROM " + table + " WHERE type = ? AND " + ranked
                + " > 0 ORDER BY " + column + " DESC LIMIT ?"
                : "SELECT uuid, MAX(name) AS name, SUM(games) AS games, SUM(wins) AS wins, SUM(kills) AS kills, "
                + "SUM(deaths) AS deaths, SUM(rating) AS rating FROM " + table + " GROUP BY uuid HAVING SUM(" + ranked
                + ") > 0 ORDER BY SUM(" + column + ") DESC LIMIT ?";
        List<PlayerStats> players = new ArrayList<>();
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            int index = 1;
            if (type != null) statement.setString(index++, type);
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    players.add(read(result));
                }
            }
        } catch (SQLException ex) {
            throw failed(ex);
        }
        return players;
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        this.connection = null;
    }

}
