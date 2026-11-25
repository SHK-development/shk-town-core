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

    private void grant(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO player_licenses (player_uuid, license_id) VALUES (?, ?)
                """)) {
            statement.setString(1, player.toString());
            statement.setString(2, id);
            statement.executeUpdate();
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
                grant(connection, id);
                assertEquals(ACTIVATED, service.change(player, id, professional, true));
            }

            grant(connection, "test_d");
            assertEquals(SLOTS_FULL, service.change(player, "test_d", professional, true));
            assertEquals(ALREADY_ACTIVE, service.change(player, "test_a", professional, true));

            assertEquals(RELINQUISHED, service.change(player, "test_a", professional, false));

            // 해제된 자격으로 바로 재장착하거나 중복 해제할 수 없다.
            assertEquals(NOT_OWNED, service.change(player, "test_a", professional, true));
            assertEquals(NOT_OWNED, service.change(player, "test_a", professional, false));

            // 반환된 한 칸은 이미 보유 중인 다른 자격이 사용할 수 있다.
            assertEquals(ACTIVATED, service.change(player, "test_d", professional, true));

            // A를 재취득할 수 있지만 다시 장착하려면 빈 슬롯이 필요하다.
            grant(connection, "test_a");
            assertEquals(SLOTS_FULL, service.change(player, "test_a", professional, true));

            assertEquals(RELINQUISHED, service.change(player, "test_d", professional, false));
            assertEquals(ACTIVATED, service.change(player, "test_a", professional, true));
        }
    }

    @Test
    void concurrentRequestsCannotTakeTheSameLastSlot() throws Exception {
        try (var connection = openDatabase()) {
            var service = new ProfessionalLicenseService(db, new ProfessionalSlotSettings(1, 9));
            grant(connection, "test_a");
            grant(connection, "test_b");

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var tasks = List.of("test_a", "test_b").stream()
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
}