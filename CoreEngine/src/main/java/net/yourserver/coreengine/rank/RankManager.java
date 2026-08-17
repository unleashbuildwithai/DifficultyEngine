package net.yourserver.coreengine.rank;

import net.yourserver.coreengine.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads/writes the {@code rank_tier} column of {@code player_profiles}.
 * <p>
 * Ensures a profile row exists for a player before any read/write (players
 * may never have interacted with the economy before their rank is checked,
 * e.g. an admin running {@code /givemember} on someone who has never joined
 * with this plugin active before... in that case the row is created lazily
 * with default values).
 */
public class RankManager {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public RankManager(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    /**
     * Ensures a {@code player_profiles} row exists for the given UUID,
     * inserting a default row if missing. Safe to call redundantly.
     */
    public void ensureProfile(UUID playerUuid) {
        String sql = "INSERT OR IGNORE INTO player_profiles (player_uuid) VALUES (?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to ensure player_profiles row for " + playerUuid, e);
        }
    }

    /**
     * Reads the player's current {@link PlayerRank}. Defaults to
     * {@link PlayerRank#NONE} if no profile row exists yet.
     */
    public PlayerRank getRank(UUID playerUuid) {
        String sql = "SELECT rank_tier FROM player_profiles WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return PlayerRank.fromTier(rs.getInt("rank_tier"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read rank for " + playerUuid, e);
        }
        return PlayerRank.NONE;
    }

    /**
     * Sets the player's rank tier (0-3). Creates the profile row first if it
     * does not exist yet, so {@code /givemember} works even on players who
     * have no prior economy activity.
     *
     * @return true if the update succeeded.
     */
    public boolean setRank(UUID playerUuid, int tier) {
        if (tier < 0 || tier > 3) {
            throw new IllegalArgumentException("Rank tier must be between 0 and 3, got " + tier);
        }
        ensureProfile(playerUuid);
        String sql = "UPDATE player_profiles SET rank_tier = ? WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, tier);
            stmt.setString(2, playerUuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set rank for " + playerUuid, e);
            return false;
        }
    }
}
