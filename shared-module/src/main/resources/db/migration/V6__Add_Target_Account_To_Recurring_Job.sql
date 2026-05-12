-- V6: Add optional target_account_id to RECURRING_JOB for SAVINGS / TRANSFER jobs

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'recurring_job'
          AND column_name  = 'target_account_id'
    ) THEN
        ALTER TABLE RECURRING_JOB
            ADD COLUMN TARGET_ACCOUNT_ID UUID REFERENCES ACCOUNT(ID);
    END IF;
END $$;

-- Ensure ROLLBACK_STATUS_ has the values used by the Java entity
ALTER TYPE ROLLBACK_STATUS_ ADD VALUE IF NOT EXISTS 'PENDING';
ALTER TYPE ROLLBACK_STATUS_ ADD VALUE IF NOT EXISTS 'COMPLETED';
