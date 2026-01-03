-- Create users table
CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  roles VARCHAR(255) NOT NULL
);

-- Create refresh tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  token VARCHAR(512) NOT NULL UNIQUE,
  username VARCHAR(255) NOT NULL,
  expiry_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);

-- Add index for lookup by token
CREATE INDEX IF NOT EXISTS idx_refresh_token_token ON refresh_tokens(token);
