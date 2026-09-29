package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestHistoryEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcPurchaseRequestHistoryRepository implements PurchaseRequestHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseRequestHistoryRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void insert(long purchaseRequestId, String action, String performedBy, String details) {
        jdbcTemplate.update("INSERT INTO purchase_request_history (purchase_request_id, action, performed_by, details) VALUES (?, ?, ?, ?)", purchaseRequestId, action, performedBy, details);
    }

    @Override
    public List<PurchaseRequestHistoryEntry> findByPurchaseRequestId(long purchaseRequestId) {
        return jdbcTemplate.query("SELECT id, purchase_request_id, action, performed_by, details, created_at FROM purchase_request_history WHERE purchase_request_id = ? ORDER BY created_at DESC, id DESC", (rs, row) -> new PurchaseRequestHistoryEntry(rs.getLong("id"), rs.getLong("purchase_request_id"), rs.getString("action"), rs.getString("performed_by"), rs.getString("details"), rs.getTimestamp("created_at").toInstant()), purchaseRequestId);
    }
}
