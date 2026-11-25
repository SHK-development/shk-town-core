package kr.shkworld.shktown.license.professional;

import kr.shkworld.shktown.license.LicenseCategory;
import kr.shkworld.shktown.license.LicenseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class ProfessionalLicenseService {
    public enum Result {
        ACTIVATED,
        ALREADY_ACTIVE,
        RELINQUISHED,
        ALREADY_INACTIVE,
        NOT_OWNED,
        NOT_PROFESSIONAL,
        SLOTS_FULL
    }

    private final DataSource dataSource;
    private final ProfessionalSlotSettings settings;

    public ProfessionalLicenseService(DataSource dataSource, ProfessionalSlotSettings settings) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.settings = Objects.requireNonNull(settings);
    }

    public record SlotUsage(int used, int capacity) {
        public int available() {
            return Math.max(0, capacity - used);
        }
    }

    public SlotUsage getSlotUsage(UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId);

        String sql = """
            SELECT
                (SELECT COUNT(*)
                 FROM active_professional_licenses
                 WHERE player_uuid = ?) AS used_slots,
                COALESCE(
                    (SELECT extra_slots
                     FROM professional_license_slots
                     WHERE player_uuid = ?),
                    0
                ) AS extra_slots
            """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, playerId.toString());

            try (var rows = statement.executeQuery()) {
                rows.next();
                int used = rows.getInt("used_slots");
                int capacity = settings.capacity(rows.getInt("extra_slots"));
                return new SlotUsage(used, capacity);
            }
        }
    }

    public Result activate(UUID playerId, LicenseType type) throws SQLException {
        return change(playerId, type.getId(), type.getCategory(), true);
    }

    public Result relinquish(UUID playerId, LicenseType type) throws SQLException {
        return change(playerId, type.getId(), type.getCategory(), false);
    }

    // 공개 진입점은 LicenseType에서 ID와 분류를 함께 가져온다.
    // 같은 패키지의 DB 테스트는 아직 출시하지 않은 전문 자격 ID를 사용할 수 있다.
    Result change(UUID playerId, String licenseId, LicenseCategory category, boolean activate) throws SQLException {
        Objects.requireNonNull(playerId);
        if (category != LicenseCategory.PROFESSIONAL) {
            return Result.NOT_PROFESSIONAL;
        }

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int extraSlots = lockSlots(connection, playerId);
                Result result = changeLocked(
                        connection, playerId, licenseId, activate, settings.capacity(extraSlots)
                );
                connection.commit();
                return result;
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

    private int lockSlots(Connection connection, UUID playerId) throws SQLException {
        String ensureRow = """
                INSERT INTO professional_license_slots (player_uuid)
                VALUES (?)
                ON DUPLICATE KEY UPDATE
                    player_uuid = professional_license_slots.player_uuid
                """;
        try (var statement = connection.prepareStatement(ensureRow)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }

        String lockRow = """
                SELECT extra_slots FROM professional_license_slots
                WHERE player_uuid = ? FOR UPDATE
                """;
        try (var statement = connection.prepareStatement(lockRow)) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("슬롯 관리 행을 찾을 수 없습니다: " + playerId);
                }
                return rows.getInt("extra_slots");
            }
        }
    }

    private Result changeLocked(Connection connection, UUID playerId, String licenseId, boolean activate, int capacity) throws SQLException {
        // 보유한 자격 중 요청한 자격 한 개를 조회하고 활성 여부도 함께 확인한다.
        String find = """
                SELECT a.license_id AS active_id
                FROM player_licenses p
                LEFT JOIN active_professional_licenses a
                  ON a.player_uuid = p.player_uuid AND a.license_id = p.license_id
                WHERE p.player_uuid = ? AND p.license_id = ?
                """;
        boolean active;
        try (var statement = connection.prepareStatement(find)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, licenseId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Result.NOT_OWNED;
                }
                active = rows.getString("active_id") != null;
            }
        }

        if (activate && active) {
            return Result.ALREADY_ACTIVE;
        }
        if (!activate && !active) {
            return Result.ALREADY_INACTIVE;
        }
        if (activate && countActive(connection, playerId) >= capacity) {
            return Result.SLOTS_FULL;
        }

        if (activate) {
            try (var statement = connection.prepareStatement("""
            INSERT INTO active_professional_licenses (player_uuid, license_id)
            VALUES (?, ?)
            """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, licenseId);
                statement.executeUpdate();
            }
            return Result.ACTIVATED;
        }

        try (var statement = connection.prepareStatement("""
        DELETE FROM active_professional_licenses
        WHERE player_uuid = ? AND license_id = ?
        """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, licenseId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("직접 해제할 전문 자격의 활성 기록이 없습니다.");
            }
        }

        try (var statement = connection.prepareStatement("""
        DELETE FROM player_licenses
        WHERE player_uuid = ? AND license_id = ?
        """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, licenseId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("직접 해제할 전문 자격의 보유 기록이 없습니다.");
            }
        }

        return Result.RELINQUISHED;
    }

    private int countActive(Connection connection, UUID playerId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM active_professional_licenses WHERE player_uuid = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    public void acquire(UUID playerId, LicenseType type) throws SQLException {
        Objects.requireNonNull(type);

        if (type.getCategory() != LicenseCategory.PROFESSIONAL) {
            throw new IllegalArgumentException("전문 라이선스만 취득할 수 있습니다.");
        }

        acquireProfessional(playerId, type.getId());
    }

    void acquireProfessional(UUID playerId, String licenseId) throws SQLException {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(licenseId);

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockSlots(connection, playerId);

                String grant = """
                    INSERT INTO player_licenses (player_uuid, license_id)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE license_id = player_licenses.license_id
                    """;
                try (var statement = connection.prepareStatement(grant)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, licenseId);
                    statement.executeUpdate();
                }

                String initializeProgress = """
                    INSERT INTO professional_license_progress
                        (player_uuid, license_id, grade_code, experience)
                    VALUES (?, ?, 'grade_3', 0)
                    ON DUPLICATE KEY UPDATE
                        license_id = professional_license_progress.license_id
                    """;
                try (var statement = connection.prepareStatement(initializeProgress)) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, licenseId);
                    statement.executeUpdate();
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