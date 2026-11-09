package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.DeletedPurchaseRequestSummary;
import br.com.jamorder.purchaserequest.domain.Priority;
import br.com.jamorder.purchaserequest.domain.PurchaseRequest;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPurchaseRequestRepository implements PurchaseRequestRepository {

    private static final RowMapper<PurchaseRequest> PURCHASE_REQUEST_ROW_MAPPER = new PurchaseRequestRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PurchaseRequest insert(PurchaseRequest purchaseRequest) {
        String sql = """
                INSERT INTO purchase_requests (
                    request_number, department, requester_name, requested_on, totvs_requested_on,
                    purchase_reason, priority, cost_center, status, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                purchaseRequest.requestNumber(),
                purchaseRequest.department(),
                purchaseRequest.requesterName(),
                purchaseRequest.requestedOn(),
                purchaseRequest.totvsRequestedOn(),
                purchaseRequest.purchaseReason(),
                purchaseRequest.priority().name(),
                purchaseRequest.costCenter(),
                purchaseRequest.status().name(),
                purchaseRequest.notes());

        return findById(id).orElseThrow();
    }

    @Override
    public Optional<PurchaseRequest> findById(long id) {
        String sql = """
                SELECT id, request_number, department, requester_name, requested_on, totvs_requested_on,
                       purchase_reason, priority, cost_center, status, notes, created_at, updated_at
                FROM purchase_requests
                WHERE id = ? AND deleted_at IS NULL
                """;

        return jdbcTemplate.query(sql, PURCHASE_REQUEST_ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<PurchaseRequest> findByIdForUpdate(long id) {
        String sql = """
                SELECT id, request_number, department, requester_name, requested_on, totvs_requested_on,
                       purchase_reason, priority, cost_center, status, notes, created_at, updated_at
                FROM purchase_requests
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """;

        return jdbcTemplate.query(sql, PURCHASE_REQUEST_ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<PurchaseRequestSummary> findAll() {
        String sql = """
                SELECT request.id, request.request_number, request.department, request.requester_name,
                       request.purchase_reason, request.priority, request.status, request.requested_on,
                       request.totvs_requested_on,
                       COUNT(item.id) AS item_count,
                       COUNT(item.id) FILTER (WHERE item.status IN ('PENDING', 'PARTIALLY_RECEIVED')) AS pending_item_count,
                       COALESCE(SUM(item.total_value), 0) AS total_value
                FROM purchase_requests request
                LEFT JOIN purchase_request_items item ON item.purchase_request_id = request.id
                WHERE request.deleted_at IS NULL
                GROUP BY request.id
                ORDER BY request.created_at DESC
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new PurchaseRequestSummary(
                resultSet.getLong("id"),
                resultSet.getString("request_number"),
                resultSet.getString("department"),
                resultSet.getString("requester_name"),
                resultSet.getString("purchase_reason"),
                Priority.valueOf(resultSet.getString("priority")),
                PurchaseRequestStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("requested_on", LocalDate.class),
                resultSet.getObject("totvs_requested_on", LocalDate.class),
                resultSet.getInt("item_count"),
                resultSet.getInt("pending_item_count"),
                resultSet.getBigDecimal("total_value")));
    }

    @Override
    public Optional<PurchaseRequest> update(PurchaseRequest purchaseRequest) {
        String sql = """
                UPDATE purchase_requests
                SET request_number = ?, department = ?, requested_on = ?, totvs_requested_on = ?,
                    purchase_reason = ?, priority = ?, cost_center = ?, status = ?, notes = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """;

        int updatedRows = jdbcTemplate.update(
                sql,
                purchaseRequest.requestNumber(),
                purchaseRequest.department(),
                purchaseRequest.requestedOn(),
                purchaseRequest.totvsRequestedOn(),
                purchaseRequest.purchaseReason(),
                purchaseRequest.priority().name(),
                purchaseRequest.costCenter(),
                purchaseRequest.status().name(),
                purchaseRequest.notes(),
                purchaseRequest.id());

        return updatedRows == 0 ? Optional.empty() : findById(purchaseRequest.id());
    }

    @Override
    public Optional<PurchaseRequest> updateStatus(long id, PurchaseRequestStatus status) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE purchase_requests SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL",
                status.name(), id);
        return updatedRows == 0 ? Optional.empty() : findById(id);
    }

    @Override
    public boolean moveToTrash(long id, String deletedBy) {
        return jdbcTemplate.update(
                "UPDATE purchase_requests SET deleted_at = CURRENT_TIMESTAMP, deleted_by = ? WHERE id = ? AND deleted_at IS NULL",
                deletedBy, id) == 1;
    }

    @Override
    public List<DeletedPurchaseRequestSummary> findInTrash() {
        String sql = """
                SELECT request.id, request.request_number, request.department, request.requester_name,
                       request.purchase_reason, request.status, request.deleted_at, request.deleted_by,
                       COUNT(item.id) AS item_count,
                       COALESCE(SUM(item.total_value), 0) AS total_value
                FROM purchase_requests request
                LEFT JOIN purchase_request_items item ON item.purchase_request_id = request.id
                WHERE request.deleted_at IS NOT NULL
                GROUP BY request.id
                ORDER BY request.deleted_at DESC
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new DeletedPurchaseRequestSummary(
                resultSet.getLong("id"),
                resultSet.getString("request_number"),
                resultSet.getString("department"),
                resultSet.getString("requester_name"),
                resultSet.getString("purchase_reason"),
                PurchaseRequestStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("item_count"),
                resultSet.getBigDecimal("total_value"),
                resultSet.getTimestamp("deleted_at").toInstant(),
                resultSet.getString("deleted_by")));
    }

    @Override
    public boolean restoreFromTrash(long id) {
        return jdbcTemplate.update(
                "UPDATE purchase_requests SET deleted_at = NULL, deleted_by = NULL WHERE id = ? AND deleted_at IS NOT NULL",
                id) == 1;
    }

    private static class PurchaseRequestRowMapper implements RowMapper<PurchaseRequest> {

        @Override
        public PurchaseRequest mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new PurchaseRequest(
                    resultSet.getLong("id"),
                    resultSet.getString("request_number"),
                    resultSet.getString("department"),
                    resultSet.getString("requester_name"),
                    resultSet.getObject("requested_on", LocalDate.class),
                    resultSet.getObject("totvs_requested_on", LocalDate.class),
                    resultSet.getString("purchase_reason"),
                    Priority.valueOf(resultSet.getString("priority")),
                    resultSet.getString("cost_center"),
                    PurchaseRequestStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("notes"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());
        }
    }
}
