-- ============================================================
-- V3__add_triggers_and_seed.sql
-- BVB Banking — seed data
-- ============================================================

-- ── updated_at ────────────────────────────────────────────────────────────────
-- Managed by Spring Data JPA @LastModifiedDate — no DB trigger needed.

-- ── Append-only guard ─────────────────────────────────────────────────────────
-- Enforce at permission level — plpgsql unavailable on this instance.
-- Service layer must also reject UPDATE/DELETE on these tables.

REVOKE UPDATE, DELETE ON AUDIT_LOG           FROM PUBLIC;
REVOKE UPDATE, DELETE ON RECURRING_AUDIT_LOG FROM PUBLIC;


-- ── Seed: CUSTOMER_TYPE ───────────────────────────────────────────────────────
-- UUIDs are hardcoded — stable across re-runs, safe to FK reference in future migrations.
-- ON CONFLICT DO NOTHING makes this idempotent.

INSERT INTO CUSTOMER_TYPE (ID, TYPE_NAME, MAX_TRANSACTION_LIMIT, DAILY_LIMIT)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'INDIVIDUAL',  500000000.00,  2000000000.00),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'BUSINESS',  5000000000.00, 20000000000.00)
ON CONFLICT (TYPE_NAME) DO NOTHING;