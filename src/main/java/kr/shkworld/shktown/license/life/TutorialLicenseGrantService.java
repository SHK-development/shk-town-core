package kr.shkworld.shktown.license.life;

import kr.shkworld.shktown.license.PlayerLicenseRepository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class TutorialLicenseGrantService {
    private final DataSource dataSource;
    private final LifeLicenseRepository licenses;
    private final TutorialLicenseGrantSettings settings;

    public TutorialLicenseGrantService(DataSource dataSource, LifeLicenseRepository licenses, TutorialLicenseGrantSettings settings) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.licenses = Objects.requireNonNull(licenses);
        this.settings = Objects.requireNonNull(settings);
    }

    public void grantInitialLicense(UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId);
        if (settings.types().isEmpty()) {
            return;
        }

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (var type : settings.types()) {
                    licenses.grantIfMissing(connection, playerId, type, settings.initialGrade(), settings.initialExperience());
                    String sql = """
                            INSERT INTO life_license_progress (player_uuid, license_id, grade, experience)
                            VALUES (?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE grade = life_license_progress.grade
                            """;

                    try (var statement = connection.prepareStatement(sql)) {
                        statement.setString(1, playerId.toString());
                        statement.setString(2, type.getId());
                        statement.setInt(3, settings.initialGrade());
                        statement.setInt(4, settings.initialExperience());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }
}
