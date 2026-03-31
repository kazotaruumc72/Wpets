package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the persistence layer (MySQL or SQLite).
 *
 * <p>Table schema:
 * <pre>
 * player_pets (
 *   id              INTEGER PRIMARY KEY AUTO_INCREMENT,
 *   player_uuid     VARCHAR(36)  NOT NULL,
 *   pet_id          VARCHAR(64)  NOT NULL,
 *   level           INT          NOT NULL DEFAULT 1,
 *   experience      BIGINT       NOT NULL DEFAULT 0,
 *   skill_points    INT          NOT NULL DEFAULT 0,
 *   unlocked_skills TEXT         NOT NULL DEFAULT '[]',
 *   UNIQUE (player_uuid, pet_id)
 * )
 * </pre>
 */
public class DatabaseManager {

    private final WpetsPlugin plugin;
    private Connection connection;
    private boolean mySQL;

    public DatabaseManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Opens the database connection and creates tables if needed.
     */
    public void init() throws SQLException {
        FileConfiguration cfg = plugin.getConfig();
        String type = cfg.getString("database.type", "sqlite").toLowerCase();
        mySQL = type.equals("mysql");

        if (mySQL) {
            initMySQL(cfg);
        } else {
            initSQLite(cfg);
        }

        createTables();
    }

    private void initMySQL(FileConfiguration cfg) throws SQLException {
        String host = cfg.getString("database.mysql.host", "localhost");
        int port = cfg.getInt("database.mysql.port", 3306);
        String database = cfg.getString("database.mysql.database", "wpets");
        String user = cfg.getString("database.mysql.username", "root");
        String pass = cfg.getString("database.mysql.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not found", e);
        }

        connection = DriverManager.getConnection(url, user, pass);
        plugin.getLogger().info("Connected to MySQL database.");
    }

    private void initSQLite(FileConfiguration cfg) throws SQLException {
        String fileName = cfg.getString("database.sqlite-file", "wpets.db");
        java.io.File dbFile = new java.io.File(plugin.getDataFolder(), fileName);

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found", e);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        plugin.getLogger().info("Connected to SQLite database: " + dbFile.getName());
    }

    private void createTables() throws SQLException {
        String autoIncrement = mySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String sql = "CREATE TABLE IF NOT EXISTS player_pets ("
                + "id INTEGER PRIMARY KEY " + autoIncrement + ", "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "pet_id VARCHAR(64) NOT NULL, "
                + "level INT NOT NULL DEFAULT 1, "
                + "experience BIGINT NOT NULL DEFAULT 0, "
                + "skill_points INT NOT NULL DEFAULT 0, "
                + "unlocked_skills TEXT NOT NULL DEFAULT '[]', "
                + "UNIQUE " + (mySQL ? "KEY uq_player_pet " : "") + "(player_uuid, pet_id)"
                + ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Closes the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
            }
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Loads a {@link PetData} from the database, or returns {@code null} if
     * the player has never owned this pet type.
     */
    public PetData loadPetData(UUID playerUuid, String petId) {
        String sql = "SELECT level, experience, skill_points, unlocked_skills "
                + "FROM player_pets WHERE player_uuid = ? AND pet_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, petId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PetData(
                            playerUuid,
                            petId,
                            rs.getInt("level"),
                            rs.getLong("experience"),
                            rs.getInt("skill_points"),
                            rs.getString("unlocked_skills")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pet data for " + playerUuid + "/" + petId, e);
        }
        return null;
    }

    /**
     * Loads all pets belonging to a player.
     */
    public List<PetData> loadAllPetsForPlayer(UUID playerUuid) {
        List<PetData> pets = new ArrayList<>();
        String sql = "SELECT pet_id, level, experience, skill_points, unlocked_skills "
                + "FROM player_pets WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pets.add(new PetData(
                            playerUuid,
                            rs.getString("pet_id"),
                            rs.getInt("level"),
                            rs.getLong("experience"),
                            rs.getInt("skill_points"),
                            rs.getString("unlocked_skills")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pets for " + playerUuid, e);
        }
        return pets;
    }

    /**
     * Inserts or updates a {@link PetData} row (upsert).
     */
    public void savePetData(PetData data) {
        String sql;
        if (mySQL) {
            sql = "INSERT INTO player_pets (player_uuid, pet_id, level, experience, skill_points, unlocked_skills) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "level = VALUES(level), experience = VALUES(experience), "
                    + "skill_points = VALUES(skill_points), unlocked_skills = VALUES(unlocked_skills)";
        } else {
            sql = "INSERT OR REPLACE INTO player_pets "
                    + "(player_uuid, pet_id, level, experience, skill_points, unlocked_skills) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getPlayerUuid().toString());
            ps.setString(2, data.getPetId());
            ps.setInt(3, data.getLevel());
            ps.setLong(4, data.getExperience());
            ps.setInt(5, data.getSkillPoints());
            ps.setString(6, data.getUnlockedSkillsJson());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pet data for " + data.getPlayerUuid(), e);
        }
    }

    /**
     * Deletes all pet data for a player (e.g., on account reset).
     */
    public void deleteAllPetsForPlayer(UUID playerUuid) {
        String sql = "DELETE FROM player_pets WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete pets for " + playerUuid, e);
        }
    }

    /**
     * Returns a live {@link Connection} for advanced queries.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Ensures the connection is still alive (reconnects for MySQL if needed).
     */
    public void keepAlive() {
        if (!mySQL) return;
        try {
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().warning("Database connection lost – reconnecting…");
                init();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Keep-alive failed", e);
        }
    }
}
