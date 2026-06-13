-- Mark certain users as immutable system accounts.
-- is_system_user = true  → only the user themselves can edit basic info; nobody can change roles/status.
-- NOTE: "system_user" is a reserved keyword in PostgreSQL, so the column is named is_system_user.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_system_user BOOLEAN NOT NULL DEFAULT false;

-- Retroactively mark the default platform admin as a system account.
UPDATE users SET is_system_user = true WHERE email = 'admin@platform.local';
