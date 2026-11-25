package kr.shkworld.shktown.license.life;

import javax.sql.DataSource;
import java.sql.Connection;
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
                grantInitialLicense(connection, playerId);
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

    public void grantInitialLicense(Connection connection, UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId);

        if (connection.getAutoCommit()) {
            throw new IllegalStateException("최초 자격 지급에는 트랜잭션이 필요합니다.");
        }

        for (var type : settings.types()) {
            licenses.grantIfMissing(connection, playerId, type, settings.initialGrade(), settings.initialExperience()
            );
        }
    }
}
