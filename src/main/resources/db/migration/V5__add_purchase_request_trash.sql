-- Deleting a requisition moves it to a trash that only admins can see and restore.
ALTER TABLE purchase_requests
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deleted_by VARCHAR(150),
    ADD CONSTRAINT ck_purchase_requests_deletion
        CHECK ((deleted_at IS NULL) = (deleted_by IS NULL));

CREATE INDEX idx_purchase_requests_deleted_at ON purchase_requests (deleted_at DESC)
    WHERE deleted_at IS NOT NULL;
