-- Departments own requisitions and scope what each user can see.
-- The old free-text "department" column described where the items are used,
-- so it becomes the optional "usage_location".
CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_departments_name ON departments (LOWER(name));

-- Existing requisitions came from the IT purchase spreadsheet.
INSERT INTO departments (name) VALUES ('TI');

ALTER TABLE purchase_requests RENAME COLUMN department TO usage_location;
ALTER TABLE purchase_requests ALTER COLUMN usage_location DROP NOT NULL;
DROP INDEX idx_purchase_requests_department;

ALTER TABLE purchase_requests
    ADD COLUMN department_id BIGINT,
    ADD CONSTRAINT fk_purchase_requests_department FOREIGN KEY (department_id) REFERENCES departments (id);

UPDATE purchase_requests SET department_id = (SELECT id FROM departments WHERE name = 'TI');

ALTER TABLE purchase_requests ALTER COLUMN department_id SET NOT NULL;
CREATE INDEX idx_purchase_requests_department_id ON purchase_requests (department_id);

-- A user without a department can see every department (admins always can).
ALTER TABLE app_users
    ADD COLUMN department_id BIGINT,
    ADD CONSTRAINT fk_app_users_department FOREIGN KEY (department_id) REFERENCES departments (id);

-- Manager approval is informational only: it never changes the requisition status.
ALTER TABLE purchase_requests
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN approved_by VARCHAR(150),
    ADD CONSTRAINT ck_purchase_requests_approval CHECK ((approved_at IS NULL) = (approved_by IS NULL));
