-- Aligns the model with the purchase spreadsheet: requisitions are tracked after
-- being registered in TOTVS, drafts hold items not yet registered, and the
-- unused approval workflow is removed.

UPDATE purchase_requests
SET status = 'OPEN'
WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'IN_PURCHASE');

UPDATE purchase_request_items
SET status = CASE
        WHEN received_quantity = 0 THEN 'PENDING'
        WHEN received_quantity < quantity THEN 'PARTIALLY_RECEIVED'
        ELSE 'RECEIVED'
    END,
    received_on = CASE
        WHEN received_quantity = quantity THEN COALESCE(received_on, CURRENT_DATE)
        ELSE received_on
    END
WHERE status = 'PURCHASED';

ALTER TABLE purchase_requests
    ADD COLUMN totvs_requested_on DATE;

UPDATE purchase_requests
SET totvs_requested_on = requested_on;

ALTER TABLE purchase_requests
    DROP CONSTRAINT ck_purchase_requests_status,
    DROP CONSTRAINT ck_purchase_requests_priority,
    DROP COLUMN buyer_name,
    DROP COLUMN manager_name,
    ALTER COLUMN request_number DROP NOT NULL,
    ADD CONSTRAINT ck_purchase_requests_status
        CHECK (status IN ('DRAFT', 'OPEN', 'PARTIALLY_RECEIVED', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_purchase_requests_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    ADD CONSTRAINT ck_purchase_requests_totvs_registration
        CHECK ((request_number IS NULL) = (totvs_requested_on IS NULL)),
    ADD CONSTRAINT ck_purchase_requests_draft_without_number
        CHECK (status <> 'DRAFT' OR request_number IS NULL),
    ADD CONSTRAINT ck_purchase_requests_tracked_with_number
        CHECK (status IN ('DRAFT', 'CANCELLED') OR request_number IS NOT NULL);

ALTER TABLE purchase_request_items
    DROP CONSTRAINT ck_purchase_request_items_status,
    DROP CONSTRAINT ck_purchase_request_items_receipt_state,
    ALTER COLUMN total_value DROP NOT NULL,
    ADD CONSTRAINT ck_purchase_request_items_status
        CHECK (status IN ('PENDING', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    ADD CONSTRAINT ck_purchase_request_items_receipt_state
        CHECK (
            (status = 'PENDING' AND received_quantity = 0)
            OR (status = 'PARTIALLY_RECEIVED' AND received_quantity > 0 AND received_quantity < quantity)
            OR (status = 'RECEIVED' AND received_quantity = quantity AND received_on IS NOT NULL)
            OR status = 'CANCELLED'
        );

CREATE INDEX idx_purchase_requests_totvs_requested_on ON purchase_requests (totvs_requested_on DESC);
