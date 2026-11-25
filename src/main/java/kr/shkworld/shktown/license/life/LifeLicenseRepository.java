package kr.shkworld.shktown.license.life;

import kr.shkworld.shktown.license.LicenseCategory;
import kr.shkworld.shktown.license.LicenseType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class LifeLicenseRepository {
    public record Progress(int grade, int experience) {}

    public Optional<Progress> findProgress(Connection connection, UUID playerId, LicenseType type) throws SQLException {
        if (type.getCategory() != LicenseCategory.LIFE) {
            throw new IllegalArgumentException("생활 자격만 조회할 수 있습니다: " + type.getId());
        }

        String sql = """
                SELECT p.grade, p.experience
                FROM player_licenses l
                LEFT JOIN life_license_progress p
                  ON p.player_uuid = l.player_uuid
                 AND p.license_id = l.license_id
                WHERE l.player_uuid = ? AND l.license_id = ?
                """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, type.getId());

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }

                int grade = rows.getInt("grade");
                if (rows.wasNull()) {
                    throw new SQLException("생활 자격의 성장 기록이 없습니다. player=" + playerId + ", license=" + type.getId());
                }

                return Optional.of(new Progress(grade, rows.getInt("experience")));
            }
        }
    }
}