package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequestItem;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPurchaseRequestItemRepository implements PurchaseRequestItemRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, purchase_request_id, description, brand_model, quantity, supplier_name,
                   total_value, received_quantity, received_on, checked_by, status, notes, created_at, updated_at
            FROM purchase_request_items
            """;

    private static final RowMapper<PurchaseRequestItem> ITEM_ROW_MAPPER = (resultSet, rowNumber) -> new PurchaseRequestItem(
            resultSet.getLong("id"),
            resultSet.getLong("purchase_request_id"),
            resultSet.getString("description"),
            resultSet.getString("brand_model"),
            resultSet.getInt("quantity"),
            resultSet.getString("supplier_name"),
            resultSet.getBigDecimal("total_value"),
            resultSet.getInt("received_quantity"),
            resultSet.getObject("received_on", LocalDate.class),
            resultSet.getString("checked_by"),
            PurchaseRequestItemStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("notes"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcPurchaseRequestItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PurchaseRequestItem insert(PurchaseRequestItem item) {
        String sql = """
                INSERT INTO purchase_request_items (
                    purchase_request_id, description, brand_model, quantity, supplier_name,
                    total_value, received_quantity, received_on, checked_by, status, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                item.purchaseRequestId(),
                item.description(),
                item.brandModel(),
                item.quantity(),
                item.supplierName(),
                item.totalValue(),
                item.receivedQuantity(),
                item.receivedOn(),
                item.checkedBy(),
                item.status().name(),
                item.notes());

        return findById(id).orElseThrow();
    }

    @Override
    public List<PurchaseRequestItem> findByPurchaseRequestId(long purchaseRequestId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE purchase_request_id = ? ORDER BY id", ITEM_ROW_MAPPER, purchaseRequestId);
    }

    @Override
    public Optional<PurchaseRequestItem> findByIdForUpdate(long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ? FOR UPDATE", ITEM_ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public PurchaseRequestItem registerReceipt(
            long id,
            int totalReceivedQuantity,
            int receiptQuantity,
            LocalDate receivedOn,
            String checkedBy,
            PurchaseRequestItemStatus status) {
        jdbcTemplate.update(
                "INSERT INTO purchase_request_item_receipts (purchase_request_item_id, received_quantity, received_on, checked_by) VALUES (?, ?, ?, ?)",
                id, receiptQuantity, receivedOn, checkedBy);
        jdbcTemplate.update(
                "UPDATE purchase_request_items SET received_quantity = ?, received_on = ?, checked_by = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                totalReceivedQuantity, receivedOn, checkedBy, status.name(), id);
        return findById(id).orElseThrow();
    }

    @Override
    public void deleteByPurchaseRequestId(long purchaseRequestId) {
        jdbcTemplate.update("DELETE FROM purchase_request_items WHERE purchase_request_id = ?", purchaseRequestId);
    }

    private Optional<PurchaseRequestItem> findById(long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", ITEM_ROW_MAPPER, id).stream().findFirst();
    }
}
