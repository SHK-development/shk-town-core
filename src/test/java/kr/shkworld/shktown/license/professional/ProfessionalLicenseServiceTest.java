package kr.shkworld.shktown.license.professional;

import kr.shkworld.shktown.license.LicenseCategory;
import kr.shkworld.shktown.license.LicenseType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static kr.shkworld.shktown.license.professional.ProfessionalLicenseService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionalLicenseServiceTest {
    private final JdbcDataSource db = new JdbcDataSource();
    private final UUID player = UUID.randomUUID();

    private Connection openDatabase() throws Exception {
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        var connection = db.getConnection();
        try {
            for (var name : List.of(
                    "player_licenses",
                    "professional_license_slots",
                    "professional_license_progress",
                    "active_professional_licenses"
            )) {
                String path = "/db/" + name + ".sql";
                try (var schema = Objects.requireNonNull(getClass().getResourceAsStream(path), path);
                     var statement = connection.createStatement()) {
                    statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return connection;
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }

    @Test
    void fullSlotsStillAllowAcquisitionAndDeactivationFreesOneSlot() throws Exception {
        try (var connection = openDatabase()) {
            var service = new ProfessionalLicenseService(db, new ProfessionalSlotSettings(3, 9));
            var professional = LicenseCategory.PROFESSIONAL;

            assertEquals(NOT_PROFESSIONAL, service.activate(player, LicenseType.WOODCUTTING));
            assertEquals(NOT_OWNED, service.change(player, "test_a", professional, true));

            for (var id : List.of("test_a", "test_b", "test_c")) {
                service.acquireProfessional(player, id);
                assertEquals(ACTIVATED, service.change(player, id, professional, true));
            }

            service.acquireProfessional(player, "test_d");
            assertEquals(SLOTS_FULL, service.change(player, "test_d", professional, true));
            assertEquals(ALREADY_ACTIVE, service.change(player, "test_a", professional, true));

            assertEquals(RELINQUISHED, service.change(player, "test_a", professional, false));

            // 해제된 자격으로 바로 재장착하거나 중복 해제할 수 없다.
            assertEquals(NOT_OWNED, service.change(player, "test_a", professional, true));
            assertEquals(NOT_OWNED, service.change(player, "test_a", professional, false));

            // 반환된 한 칸은 이미 보유 중인 다른 자격이 사용할 수 있다.
            assertEquals(ACTIVATED, service.change(player, "test_d", professional, true));

            // A를 재취득할 수 있지만 다시 장착하려면 빈 슬롯이 필요하다.
            service.acquireProfessional(player, "test_a");
            assertEquals(SLOTS_FULL, service.change(player, "test_a", professional, true));

            assertEquals(RELINQUISHED, service.change(player, "test_d", professional, false));
            assertEquals(ACTIVATED, service.change(player, "test_a", professional, true));
        }
    }

    @Test
    void concurrentRequestsCannotTakeTheSameLastSlot() throws Exception {
        try (var connection = openDatabase()) {
            var service = new ProfessionalLicenseService(db, new ProfessionalSlotSettings(1, 9));
            service.acquireProfessional(player, "test_a");
            service.acquireProfessional(player, "test_b");

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var tasks = Stream.of("test_a", "test_b")
                        .map(id -> executor.submit(() -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("동시 실행 시작 신호 시간 초과");
                            }
                            return service.change(player, id, LicenseCategory.PROFESSIONAL, true);
                        }))
                        .toList();

                try {
                    assertTrue(ready.await(5, TimeUnit.SECONDS));
                } finally {
                    start.countDown();
                }

                var results = List.of(
                        tasks.get(0).get(10, TimeUnit.SECONDS),
                        tasks.get(1).get(10, TimeUnit.SECONDS)
                );
                assertTrue(results.contains(ACTIVATED), results.toString());
                assertTrue(results.contains(SLOTS_FULL), results.toString());
            }

            try (var statement = connection.prepareStatement("""
                    SELECT COUNT(*) FROM active_professional_licenses WHERE player_uuid = ?
                    """)) {
                statement.setString(1, player.toString());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            }
        }
    }

    @Test
    void reacquiringPreservesProfessionalGradeAndExperience() throws Exception {
        try (var connection = openDatabase()) {
            var service = new ProfessionalLicenseService(db, new ProfessionalSlotSettings(3, 9));
            var professional = LicenseCategory.PROFESSIONAL;
            String id = "test_a";

            service.acquireProfessional(player, id);
            assertProgress(connection, id, "grade_3", 0);

            // 개별 직업의 승급 기능은 이후 단계이므로 성장한 상태를 직접 준비한다.
            try (var statement = connection.prepareStatement("""
                UPDATE professional_license_progress
                SET grade_code = ?, experience = ?
                WHERE player_uuid = ? AND license_id = ?
                """)) {
                statement.setString(1, "pre_grade_2");
                statement.setInt(2, 120);
                statement.setString(3, player.toString());
                statement.setString(4, id);
                assertEquals(1, statement.executeUpdate());
            }

            assertEquals(ACTIVATED, service.change(player, id, professional, true));
            assertEquals(RELINQUISHED, service.change(player, id, professional, false));

            // 성장 기록이 남아 있어도 자격을 다시 취득하기 전에는 장착할 수 없다.
            assertProgress(connection, id, "pre_grade_2", 120);
            assertEquals(NOT_OWNED, service.change(player, id, professional, true));

            service.acquireProfessional(player, id);
            assertEquals(ACTIVATED, service.change(player, id, professional, true));
            assertProgress(connection, id, "pre_grade_2", 120);
        }
    }

    private void assertProgress(
            Connection connection, String licenseId, String expectedGrade, int expectedExperience
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT grade_code, experience
            FROM professional_license_progress
            WHERE player_uuid = ? AND license_id = ?
            """)) {
            statement.setString(1, player.toString());
            statement.setString(2, licenseId);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "전문 라이선스 성장 기록이 있어야 합니다.");
                assertEquals(expectedGrade, rows.getString("grade_code"));
                assertEquals(expectedExperience, rows.getInt("experience"));
            }
        }
    }

    @Test
    void slotUsageTracksEquippedLicensesAndKeepsPermanentExpansion() throws Exception {
        try (var connection = openDatabase()) {
            var service = new ProfessionalLicenseService(
                    db, new ProfessionalSlotSettings(3, 9)
            );
            var category = LicenseCategory.PROFESSIONAL;

            // 신규 플레이어는 슬롯 관리 행이 없어도 기본 3칸을 조회할 수 있다.
            var initial = service.getSlotUsage(player);
            assertEquals(0, initial.used());
            assertEquals(3, initial.capacity());
            assertEquals(3, initial.available());

            service.acquireProfessional(player, "test_a");
            assertEquals(0, service.getSlotUsage(player).used());

            assertEquals(ACTIVATED, service.change(player, "test_a", category, true));

            // 크레딧 결제는 이후 단계이므로 이미 1칸 확장한 상태만 준비한다.
            try (var statement = connection.prepareStatement("""
                UPDATE professional_license_slots
                SET extra_slots = 1
                WHERE player_uuid = ?
                """)) {
                statement.setString(1, player.toString());
                assertEquals(1, statement.executeUpdate());
            }

            var equipped = service.getSlotUsage(player);
            assertEquals(1, equipped.used());
            assertEquals(4, equipped.capacity());
            assertEquals(3, equipped.available());

            assertEquals(RELINQUISHED, service.change(player, "test_a", category, false));

            var afterRelinquish = service.getSlotUsage(player);
            assertEquals(0, afterRelinquish.used());
            assertEquals(4, afterRelinquish.capacity());
            assertEquals(4, afterRelinquish.available());
        }
    }
}