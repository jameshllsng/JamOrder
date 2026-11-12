-- Requisitions cancelled before the fix kept their items PENDING, so they still
-- counted as pending. Cancelling now also cancels the pending items; this brings
-- older cancellations in line. Requisitions in the trash (deleted_at set) are
-- included on purpose. Items in any other status are left as they are.
UPDATE purchase_request_items AS item
SET status = 'CANCELLED',
    updated_at = CURRENT_TIMESTAMP
FROM purchase_requests AS request
WHERE request.id = item.purchase_request_id
  AND request.status = 'CANCELLED'
  AND item.status = 'PENDING';
