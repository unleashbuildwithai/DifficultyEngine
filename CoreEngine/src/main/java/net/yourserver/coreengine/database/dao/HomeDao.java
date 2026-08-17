package net.yourserver.coreengine.database.dao;

import net.yourserver.coreengine.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data-access for the {@code player_homes} table (Module 2: Homes system).
 */
public class HomeDao {

    /** A saved home location. */
    public record HomeEntry(int slot, String worldName, double x, double y, double z,
                            float yaw, float pitch, String name) {
    }

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public HomeDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /** Saves (or overwrites) a home slot using the default display name. */
    public void setHome(UUID playerUuid, int slot, String worldName, double x, double y, double z,
                        float yaw, float pitch) {
        setHome(playerUuid, slot, "Home " + slot, worldName, x, y, z, yaw, pitch);
    }

    /** Saves (or overwrites) a home slot with a custom display name. */
    public void setHome(UUID playerUuid, int slot, String name, String worldName, double x, double y, double z,
                        float yaw, float pitch) {
        String sql = """
            INSERT INTO player_homes (player_uuid, home_slot, world_name, x, y, z, yaw, pitch, name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid, home_slot) DO UPDATE SET
                world_name = excluded.world_name, x = excluded.x, y = excluded.y,
                z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, name = excluded.name
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, slot);
            stmt.setString(3, worldName);
            stmt.setDouble(4, x);
            stmt.setDouble(5, y);
            stmt.setDouble(6, z);
            stmt.setFloat(7, yaw);
            stmt.setFloat(8, pitch);
            stmt.setString(9, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save home for " + playerUuid, e);
        }
    }

    /** Renames an existing home slot; returns true if it existed. */
    public boolean renameHome(UUID playerUuid, int slot, String name) {
        String sql = "UPDATE player_homes SET name = ? WHERE player_uuid = ? AND home_slot = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, playerUuid.toString());
            stmt.setInt(3, slot);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to rename home for " + playerUuid, e);
            return false;
        }
    }

    public Optional<HomeEntry> getHome(UUID playerUuid, int slot) {
        String sql = "SELECT * FROM player_homes WHERE player_uuid = ? AND home_slot = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, slot);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load home for " + playerUuid, e);
        }
        return Optional.empty();
    }

    /** All saved homes for a player, ordered by slot. */
    public List<HomeEntry> getHomes(UUID playerUuid) {
        String sql = "SELECT * FROM player_homes WHERE player_uuid = ? ORDER BY home_slot ASC";
        List<HomeEntry> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load homes for " + playerUuid, e);
        }
        return result;
    }

    /** Deletes a home slot; returns true if it existed. */
    public boolean deleteHome(UUID playerUuid, int slot) {
        String sql = "DELETE FROM player_homes WHERE player_uuid = ? AND home_slot = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, slot);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete home for " + playerUuid, e);
            return false;
        }
    }

    private HomeEntry map(ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        return new HomeEntry(
                rs.getInt("home_slot"),
                rs.getString("world_name"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                name == null ? "" : name);
    }
}
