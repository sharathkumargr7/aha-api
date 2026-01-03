-- Convert id columns and sequences to bigint for users and refresh_tokens
-- idempotent: safe to run multiple times

BEGIN;

-- refresh_tokens
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='refresh_tokens' AND column_name='id' AND data_type <> 'bigint'
  ) THEN
    -- ensure sequence exists
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relkind='S' AND relname='refresh_tokens_id_seq') THEN
      CREATE SEQUENCE refresh_tokens_id_seq AS bigint;
    END IF;

    ALTER SEQUENCE refresh_tokens_id_seq AS bigint;
    ALTER TABLE refresh_tokens ALTER COLUMN id TYPE bigint USING id::bigint;
    PERFORM setval('refresh_tokens_id_seq', COALESCE((SELECT MAX(id) FROM refresh_tokens), 1), true);
    ALTER TABLE refresh_tokens ALTER COLUMN id SET DEFAULT nextval('refresh_tokens_id_seq'::regclass);
    ALTER SEQUENCE refresh_tokens_id_seq OWNED BY refresh_tokens.id;
  END IF;
END$$;

-- users
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='users' AND column_name='id' AND data_type <> 'bigint'
  ) THEN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relkind='S' AND relname='users_id_seq') THEN
      CREATE SEQUENCE users_id_seq AS bigint;
    END IF;

    ALTER SEQUENCE users_id_seq AS bigint;
    ALTER TABLE users ALTER COLUMN id TYPE bigint USING id::bigint;
    PERFORM setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1), true);
    ALTER TABLE users ALTER COLUMN id SET DEFAULT nextval('users_id_seq'::regclass);
    ALTER SEQUENCE users_id_seq OWNED BY users.id;
  END IF;
END$$;

COMMIT;
