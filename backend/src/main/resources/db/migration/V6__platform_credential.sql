-- Sprint 10 — platform-level credentials for Veracode + SonarQube.
-- One row per kind so the developer pastes their token once via Settings,
-- the system validates it live, persists the SecretStore reference, and
-- scan sources can later opt to use it instead of carrying their own copy.

CREATE TABLE platform_credential (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    kind                     VARCHAR(40)    NOT NULL,
    base_url                 VARCHAR(1000)  NOT NULL,
    secret_key_vault_ref     VARCHAR(500)   NOT NULL,
    last_validated_at        DATETIME2      NULL,
    last_validated_ok        BIT            NULL,
    last_validated_message   NVARCHAR(MAX)  NULL,
    updated_at               DATETIME2      NOT NULL,
    updated_by_user_id       BIGINT         NULL,
    CONSTRAINT uq_platform_credential_kind UNIQUE (kind),
    CONSTRAINT fk_pc_user FOREIGN KEY (updated_by_user_id) REFERENCES app_user(id)
);
