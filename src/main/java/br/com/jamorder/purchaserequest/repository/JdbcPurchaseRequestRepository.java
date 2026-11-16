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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPurchaseRequestRepository implements PurchaseRequestRepository {

    private static final RowMapper<PurchaseRequest> PURCHASE_REQUEST_ROW_MAPPER = new PurchaseRequestRowMapper();

    private static final String SELECT_PURCHASE_REQUEST = """
            SELECT request.id, request.request_number, request.department_id, department.name AS department_name,
                   request.usage_location, request.requester_name, request.requested_on, request.totvs_requested_on,
                   request.purchase_reason, request.priority, request.cost_center, request.status, request.notes,
                   request.approved_at, request.approved_by, request.created_at, request.updated_at
            FROM purchase_requests request
            JOIN departments department ON department.id = request.department_id
            WHERE request.id = ? AND request.deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PurchaseRequest insert(PurchaseRequest purchaseRequest) {
        String sql = """
                INSERT INTO purchase_requests (
                    request_number, department_id, usage_location, requester_name, requested_on, totvs_requested_on,
                    purchase_reason, priority, cost_center, status, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                purchaseRequest.requestNumber(),
                purchaseRequest.departmentId(),
                purchaseRequest.usageLocation(),
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
        return jdbcTemplate.query(SELECT_PURCHASE_REQUEST, PURCHASE_REQUEST_ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<PurchaseRequest> findByIdForUpdate(long id) {
        return jdbcTemplate.query(SELECT_PURCHASE_REQUEST + " FOR UPDATE OF request", PURCHASE_REQUEST_ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<PurchaseRequestSummary> findAll(Long departmentId) {
        String sql = """
                SELECT request.id, request.request_number, request.department_id, department.name AS department_name, request.usage_location,
                       request.requester_name, request.purchase_reason, request.priority, request.status,
                       request.requested_on, request.totvs_requested_on, request.approved_at, request.approved_by,
                       COUNT(item.id) AS item_count,
                       COUNT(item.id) FILTER (WHERE item.status IN ('PENDING', 'PARTIALLY_RECEIVED')) AS pending_item_count,
                       SUM(item.total_value) AS total_value,
                       STRING_AGG(item.description, ' ' ORDER BY item.id) AS item_descriptions,
                       STRING_AGG(DISTINCT item.supplier_name, ', ' ORDER BY item.supplier_name) AS supplier_names
                FROM purchase_requests request
                JOIN departments department ON department.id = request.department_id
                LEFT JOIN purchase_request_items item ON item.purchase_request_id = request.id
                WHERE request.deleted_at IS NULL
                  AND (CAST(? AS BIGINT) IS NULL OR request.department_id = ?)
                GROUP BY request.id, department.name
                ORDER BY request.created_at DESC
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new PurchaseRequestSummary(
                resultSet.getLong("id"),
                resultSet.getString("request_number"),
                resultSet.getLong("department_id"),
                resultSet.getString("department_name"),
                resultSet.getString("usage_location"),
                resultSet.getString("requester_name"),
                resultSet.getString("purchase_reason"),
                Priority.valueOf(resultSet.getString("priority")),
                PurchaseRequestStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("requested_on", LocalDate.class),
                resultSet.getObject("totvs_requested_on", LocalDate.class),
                resultSet.getInt("item_count"),
                resultSet.getInt("pending_item_count"),
                resultSet.getBigDecimal("total_value"),
                toInstant(resultSet.getTimestamp("approved_at")),
                resultSet.getString("approved_by"),
                resultSet.getString("item_descriptions"),
                resultSet.getString("supplier_names")), departmentId, departmentId);
    }

    @Override
    public Optional<PurchaseRequest> update(PurchaseRequest purchaseRequest) {
        String sql = """
                UPDATE purchase_requests
                SET request_number = ?, department_id = ?, usage_location = ?, requested_on = ?, totvs_requested_on = ?,
                    purchase_reason = ?, priority = ?, cost_center = ?, status = ?, notes = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """;

        int updatedRows = jdbcTemplate.update(
                sql,
                purchaseRequest.requestNumber(),
                purchaseRequest.departmentId(),
                purchaseRequest.usageLocation(),
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
    public void approve(long id, String approvedBy) {
        jdbcTemplate.update(
                "UPDATE purchase_requests SET approved_at = CURRENT_TIMESTAMP, approved_by = ? WHERE id = ? AND deleted_at IS NULL",
                approvedBy, id);
    }

    @Override
    public void revokeApproval(long id) {
        jdbcTemplate.update(
                "UPDATE purchase_requests SET approved_at = NULL, approved_by = NULL WHERE id = ? AND deleted_at IS NULL", id);
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
                SELECT request.id, request.request_number, department.name AS department_name, request.requester_name,
                       request.purchase_reason, request.status, request.deleted_at, request.deleted_by,
                       COUNT(item.id) AS item_count,
                       SUM(item.total_value) AS total_value
                FROM purchase_requests request
                JOIN departments department ON department.id = request.department_id
                LEFT JOIN purchase_request_items item ON item.purchase_request_id = request.id
                WHERE request.deleted_at IS NOT NULL
                GROUP BY request.id, department.name
                ORDER BY request.deleted_at DESC
                """;

        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new DeletedPurchaseRequestSummary(
                resultSet.getLong("id"),
                resultSet.getString("request_number"),
                resultSet.getString("department_name"),
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

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static class PurchaseRequestRowMapper implements RowMapper<PurchaseRequest> {

        @Override
        public PurchaseRequest mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new PurchaseRequest(
                    resultSet.getLong("id"),
                    resultSet.getString("request_number"),
                    resultSet.getLong("department_id"),
                    resultSet.getString("department_name"),
                    resultSet.getString("usage_location"),
                    resultSet.getString("requester_name"),
                    resultSet.getObject("requested_on", LocalDate.class),
                    resultSet.getObject("totvs_requested_on", LocalDate.class),
                    resultSet.getString("purchase_reason"),
                    Priority.valueOf(resultSet.getString("priority")),
                    resultSet.getString("cost_center"),
                    PurchaseRequestStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("notes"),
                    toInstant(resultSet.getTimestamp("approved_at")),
                    resultSet.getString("approved_by"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());
        }
    }
}
