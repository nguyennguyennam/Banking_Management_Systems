-- ============================================================
-- V5__Align_Schema_With_New_Design.sql
-- BVB Banking — align DB schema with redesigned entities
-- Fully idempotent: safe to run whether or not changes were
-- already applied manually.
-- ============================================================

-- ── TRANSACTION_STATUS_ enum ──────────────────────────────────────────────────
ALTER TYPE TRANSACTION_STATUS_ ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TYPE TRANSACTION_STATUS_ ADD VALUE IF NOT EXISTS 'FAILED';

-- ── TRANSACTION table: rename ACCOUNT_ID_SEND → SOURCE_ACCOUNT_ID ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'transaction'
          AND column_name  = 'account_id_send'
    ) THEN
        ALTER TABLE transaction RENAME COLUMN account_id_send TO source_account_id;
    END IF;
END $$;

-- ── TRANSACTION table: make EXECUTED_AT nullable ──────────────────────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'transaction'
          AND column_name  = 'executed_at'
          AND is_nullable  = 'NO'
    ) THEN
        ALTER TABLE transaction ALTER COLUMN executed_at DROP NOT NULL;
        ALTER TABLE transaction ALTER COLUMN executed_at DROP DEFAULT;
    END IF;
END $$;

-- ── TRANSACTION table: default status PENDING ─────────────────────────────────
-- SET DEFAULT is idempotent — safe to run even if already set.
ALTER TABLE transaction
    ALTER COLUMN transaction_status SET DEFAULT 'PENDING'::transaction_status_;

-- ── AUDIT_ACTION_ enum: add values used by AuditLogEvent.Action ───────────────
ALTER TYPE AUDIT_ACTION_ ADD VALUE IF NOT EXISTS 'ACCOUNT_ACTIVE';
ALTER TYPE AUDIT_ACTION_ ADD VALUE IF NOT EXISTS 'TRANSFER';
ALTER TYPE AUDIT_ACTION_ ADD VALUE IF NOT EXISTS 'TRANSACTION_ROLLED_BACK';
