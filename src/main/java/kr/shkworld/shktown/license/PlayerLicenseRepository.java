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
}
