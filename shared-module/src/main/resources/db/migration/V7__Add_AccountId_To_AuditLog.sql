-- ============================================================
-- V7__Add_AccountId_To_AuditLog.sql
-- BVB Banking — link audit_log rows to the relevant account
-- ============================================================

ALTER TABLE AUDIT_LOG ADD COLUMN IF NOT EXISTS ACCOUNT_ID UUID REFERENCES ACCOUNT(ID);

CREATE INDEX IF NOT EXISTS idx_audit_log_account_id ON AUDIT_LOG(ACCOUNT_ID);
