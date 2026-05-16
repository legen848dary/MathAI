CREATE TABLE admin_users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE admin_totp (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id  UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    secret         VARCHAR(255) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(admin_user_id)
);

CREATE TABLE app_settings (
    key        VARCHAR(255) PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
