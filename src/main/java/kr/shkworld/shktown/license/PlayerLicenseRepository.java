package kr.shkworld.shktown.license;

import com.google.errorprone.annotations.Var;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerLicenseRepository {
    public void grantIfMissing(Connection connection, UUID playerID, LicenseType type) throws SQLException {
        String sql = """
                INSERT INTO player_licenses (player_uuid, license_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE license_id = player_licenses.license_id
                """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerID.toString());
            statement.setString(2, type.getId());
            statement.executeUpdate();
        }
    }

    public boolean owns(Connection connection, UUID playerID, LicenseType type) throws SQLException {
        String sql = """
                SELECT 1 FROM player_licenses
                WHERE player_uuid = ? AND license_id = ?
                """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerID.toString());
            statement.setString(2, type.getId());
            try (var rows =  statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public boolean meetsLicenseRequirement(Connection connection, UUID playerId, LicenseType type) throws SQLException {
        return switch (type.getCategory()) {
            case LIFE -> owns(connection, playerId, type);
            case PROFESSIONAL -> isProfessionalLicenseActive(connection, playerId, type.getId());
        };
    }

    private boolean isProfessionalLicenseActive(Connection connection, UUID playerId, String licenseId) throws SQLException {
        String sql = """
            SELECT 1
            FROM active_professional_licenses
            WHERE player_uuid = ? AND license_id = ?
            """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, licenseId);

            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}
