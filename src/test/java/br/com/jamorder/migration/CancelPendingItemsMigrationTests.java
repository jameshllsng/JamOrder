package br.com.jamorder.migration;

import br.com.jamorder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway applies V8 to an empty test database, so this test re-runs its SQL
 * against rows shaped like the ones it was written for.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CancelPendingItemsMigrationTests {

    private static final String V8 = "db/migration/V8__cancel_pending_items_of_cancelled_requests.sql";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cancelsOnlyPendingItemsOfCancelledRequisitionsIncludingTrash() throws IOException {
        long department = jdbcTemplate.queryForObject(
                "INSERT INTO departments (name) VALUES (?) RETURNING id", Long.class, "Migration " + UUID.randomUUID());
        long cancelled = insertRequest(department, "CANCELLED", false);
        long cancelledInTrash = insertRequest(department, "CANCELLED", true);
        long open = insertRequest(department, "OPEN", false);

        long pendingOfCancelled = insertItem(cancelled, "PENDING", 0);
        long receivedOfCancelled = insertItem(cancelled, "RECEIVED", 1);
        long pendingInTrash = insertItem(cancelledInTrash, "PENDING", 0);
        long pendingOfOpen = insertItem(open, "PENDING", 0);
        jdbcTemplate.update("UPDATE purchase_request_items SET updated_at = TIMESTAMP '2000-01-01' WHERE purchase_request_id IN (?, ?, ?)",
                cancelled, cancelledInTrash, open);

        int changed = jdbcTemplate.update(new ClassPathResource(V8).getContentAsString(StandardCharsets.UTF_8));

        assertThat(changed).isEqualTo(2);
        assertThat(statusOf(pendingOfCancelled)).isEqualTo("CANCELLED");
        assertThat(statusOf(pendingInTrash)).isEqualTo("CANCELLED");
        assertThat(statusOf(receivedOfCancelled)).isEqualTo("RECEIVED");
        assertThat(statusOf(pendingOfOpen)).isEqualTo("PENDING");
        assertThat(touched(pendingOfCancelled)).isTrue();
        assertThat(touched(pendingInTrash)).isTrue();
        assertThat(touched(receivedOfCancelled)).isFalse();
        assertThat(touched(pendingOfOpen)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM purchase_requests WHERE id = ?", String.class, open))
                .isEqualTo("OPEN");
    }

    private long insertRequest(long department, String status, boolean inTrash) {
        String number = "OPEN".equals(status) ? "MIG-" + UUID.randomUUID().toString().substring(0, 8) : null;
        return jdbcTemplate.queryForObject("""
                INSERT INTO purchase_requests (request_number, totvs_requested_on, requester_name, requested_on, purchase_reason,
                                               priority, status, department_id, deleted_at, deleted_by)
                VALUES (?, ?, 'Test requester', CURRENT_DATE, 'Test', 'MEDIUM', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, number, number == null ? null : Date.valueOf(LocalDate.now()), status, department,
                inTrash ? Timestamp.from(Instant.now()) : null, inTrash ? "Test admin" : null);
    }

    private long insertItem(long request, String status, int receivedQuantity) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO purchase_request_items (purchase_request_id, description, quantity, status, received_quantity, received_on)
                VALUES (?, 'Test item', 1, ?, ?, ?)
                RETURNING id
                """, Long.class, request, status, receivedQuantity,
                receivedQuantity > 0 ? Date.valueOf(LocalDate.now()) : null);
    }

    private String statusOf(long item) {
        return jdbcTemplate.queryForObject("SELECT status FROM purchase_request_items WHERE id = ?", String.class, item);
    }

    /** CURRENT_TIMESTAMP is the transaction start, so a touched row is newer than the 2000-01-01 marker. */
    private boolean touched(long item) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at > TIMESTAMP '2000-01-02' FROM purchase_request_items WHERE id = ?", Boolean.class, item);
    }
}
