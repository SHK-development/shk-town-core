package kr.shkworld.shktown.license.life;

import kr.shkworld.shktown.license.LicenseType;
import kr.shkworld.shktown.license.PlayerLicenseRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialLicenseGrantServiceTest {
    private record Progress(int grade, int experience) {}

    @Test
    void grantMissingLicensesAndPreservesExistingProgress() throws Exception {
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");

        try (var connection = db.getConnection()) {
            for (var path : List.of("/db/player_licenses.sql", "/db/life_license_progress.sql")) {
                try (var schema = Objects.requireNonNull(getClass().getResourceAsStream(path), path);
                     var statement = connection.createStatement()) {
                    statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
                }
            }

            var settings = TutorialLicenseGrantSettings.from(Map.of(
                    "types", List.of("woodcutting", "mining", "fishing"),
                    "initial-grade", 9,
                    "initial-experience", 0
            ));
            var licenses = new PlayerLicenseRepository();
            var service = new TutorialLicenseGrantService(db, licenses, settings);
            var playerId =  UUID.randomUUID();
            var beginner = new Progress(9, 0);

            service.grantInitialLicense(playerId);
            assertEquals(Map.of(
                    "woodcutting", beginner,
                    "mining", beginner,
                    "fishing", beginner
            ), readLicenses(connection, playerId));
            assertTrue(licenses.owns(connection, playerId, LicenseType.FISHING));

            try (var advance = connection.prepareStatement("""
                    UPDATE life_license_progress SET grade = 8, experience = 120
                    WHERE player_uuid = ? AND license_id = 'woodcutting'
                    """);
                 var remove = connection.prepareStatement("""
                    DELETE FROM life_license_progress
                    WHERE player_uuid = ? AND license_id = 'fishing'
                    """)) {
                advance.setString(1, playerId.toString());
                remove.setString(1, playerId.toString());
                assertEquals(1, advance.executeUpdate());
                assertEquals(1, remove.executeUpdate());
            }

            service.grantInitialLicense(playerId);
            service.grantInitialLicense(playerId);

            assertEquals(Map.of(
                    "woodcutting", new Progress(8, 120),
                    "mining", beginner,
                    "fishing", beginner
            ), readLicenses(connection, playerId));

            var customSettings = TutorialLicenseGrantSettings.from(Map.of(
                    "types", List.of("mining"),
                    "initial-grade", 8,
                    "initial-experience", 25
            ));
            var customService = new TutorialLicenseGrantService(db, licenses, customSettings);

            var anotherPlayer = UUID.randomUUID();
            customService.grantInitialLicense(anotherPlayer);

            assertEquals(Map.of(
                    "mining", new Progress(8, 25)
            ), readLicenses(connection, anotherPlayer));

            try (var statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE life_license_progress
                        ADD CONSTRAINT reject_teset_player_fishing
                        CHECK (license_id <> 'fishing' OR player_uuid <> '00000000-0000-0000-0000-000000000001')
                        """);
            }

            var failedPlayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
            assertThrows(SQLException.class, () -> service.grantInitialLicense(failedPlayer));
            assertFalse(licenses.owns(connection, failedPlayer, LicenseType.WOODCUTTING));
            assertTrue(readLicenses(connection, failedPlayer).isEmpty());
        }
    }

    private Map<String, Progress> readLicenses(Connection connection, UUID playerId) throws SQLException {
        var result = new HashMap<String, Progress>();
        try (var statement = connection.prepareStatement("""
                SELECT license_id, grade, experience
                FROM life_license_progress WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(rows.getString("license_id"), new Progress(rows.getInt("grade"), rows.getInt("experience")));
                }
            }
        }
        return result;
    }
}
