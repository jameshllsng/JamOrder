-- Visual palette chosen by each user. NULL keeps the default palette.
ALTER TABLE app_users ADD COLUMN palette VARCHAR(20);

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_palette
    CHECK (palette IS NULL OR palette IN ('AZUL', 'PETROLEO', 'AMEIXA', 'GRAFITE', 'BORDO', 'MEIA_NOITE'));
