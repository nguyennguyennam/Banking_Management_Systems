-- ============================================================
-- V2__add_indexes.sql
-- BVB Banking — indexes
-- ============================================================

-- ── CUSTOMER ──────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_customer_city
    ON CUSTOMER(CITY);                              -- customer count by city (Level 1 stats API)

CREATE INDEX IF NOT EXISTS idx_customer_created_at
    ON CUSTOMER USING BRIN (CREATED_AT);            -- append-only, BRIN is cheaper than B-tree

-- ── ACCOUNT ───────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_account_customer_id
    ON ACCOUNT(CUSTOMER_ID);                        -- list accounts by customer

-- ── TRANSACTION ───────────────────────────────────────────────────────────────

-- Main search index: account + type + date covers most filter combinations
CREATE INDEX IF NOT EXISTS idx_tx_search
    ON TRANSACTION(AMOUNT, EXECUTED_AT, TRANSACTION_TYPE DESC);

CREATE INDEX IF NOT EXISTS idx_tx_amount
    ON TRANSACTION USING BRIN (AMOUNT);             -- amount range scans

CREATE INDEX IF NOT EXISTS idx_tx_created_at
    ON TRANSACTION USING BRIN (CREATED_AT);         -- append-only time range scans

CREATE INDEX IF NOT EXISTS idx_tx_target_account_id
    ON TRANSACTION(TARGET_ACCOUNT_ID);


-- ── TRANSACTION_ROLLBACK ──────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_rollback_original_tx
    ON TRANSACTION_ROLLBACK(ORIGINAL_TRANSACTION_ID);


-- ── AUDIT_LOG ─────────────────────────────────────────────────────────────────

-- Primary query pattern: fetch audit trail for a specific entity
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON AUDIT_LOG(ENTITY_TYPE, CHANGED_AT DESC );

CREATE INDEX IF NOT EXISTS idx_audit_changed_at
    ON AUDIT_LOG USING BRIN (CHANGED_AT);           -- append-only time range scans



-- ── RECURRING_SCHEDULE ────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_recurring_schedule_job_id
    ON RECURRING_SCHEDULE(JOB_ID);

-- Partial index — scheduler queries: active schedules due for execution
CREATE INDEX IF NOT EXISTS idx_recurring_schedule_next_run
    ON RECURRING_SCHEDULE(NEXT_RUN ASC)
    WHERE ACTIVE = TRUE;


-- ── RECURRING_AUDIT_LOG ───────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_recurring_audit_schedule_id
    ON RECURRING_AUDIT_LOG(SCHEDULE_ID);            -- run history by schedule

CREATE INDEX IF NOT EXISTS idx_recurring_audit_executed_at
    ON RECURRING_AUDIT_LOG USING BRIN (EXECUTED_AT);

-- Partial index — monitoring: surface failed or skipped runs
CREATE INDEX IF NOT EXISTS idx_recurring_audit_status_failed
    ON RECURRING_AUDIT_LOG(STATUS)
    WHERE STATUS IN ('FAILED', 'SKIPPED');


-- ── ALERT ─────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_alert_transaction_id
    ON ALERT(TRANSACTION_ID);

-- Partial index — admin dashboard only shows open alerts
CREATE INDEX IF NOT EXISTS idx_alert_status_open
    ON ALERT(STATUS)
    WHERE STATUS IN ('OPEN', 'UNDER_REVIEW');

CREATE INDEX IF NOT EXISTS idx_alert_created_at
    ON ALERT USING BRIN (CREATED_AT);