# Banking Management System

A Spring Boot modular monolith that handles customer management, account operations, transaction processing, audit logging, and scheduled recurring payments for a banking context.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Module Breakdown](#module-breakdown)
- [Technology Stack](#technology-stack)
- [Database Schema](#database-schema)
- [Security Model](#security-model)
- [Transaction Lifecycle](#transaction-lifecycle)
- [Audit Log Pipeline](#audit-log-pipeline)
- [Scheduled Jobs](#scheduled-jobs)
- [API Reference](#api-reference)
- [Code Coverage](#code-coverage)
- [Running the Application](#running-the-application)
- [Environment Variables](#environment-variables)

---

## Architecture Overview

The system is a **modular monolith**: a single deployable Spring Boot application (`app-module`) that composes eight Maven sub-modules. Each module owns its own domain, repository, and service layer. Inter-module calls happen in-process via Spring beans — no HTTP round-trips between modules.

```
┌──────────────────────────────────────────────────────────────────┐
│                        app-module  :8080                         │
│  (single Spring context, scans all com.bvb packages)             │
│                                                                  │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐ │
│  │ customer-     │  │ account-      │  │ transaction-module    │ │
│  │ module        │  │ module        │  │                       │ │
│  │               │  │               │  │  PENDING-first state  │ │
│  │ Customer CRUD │  │ Account CRUD  │  │  machine + idempotency│ │
│  │ Auth (JWT)    │  │ Balance ops   │  │  key guard            │ │
│  │ UserAuth      │  │ Lock/Unlock   │  │                       │ │
│  └───────┬───────┘  └───────┬───────┘  └──────────┬────────────┘ │
│          │                  │                     │              │
│  ┌───────▼───────────────────▼─────────────────────▼───────────┐ │
│  │                      shared-module                          │ │
│  │  JWT auth · Security config · Redis pub/sub · PageResponse  │ │
│  │  UserPrincipal · AuditLogPublisher · InsufficientBalance    │ │
│  └───────────────────────────────────────────────────────────┬─┘ │
│                                                              │   │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────────────▼─┐ │
│  │ schedule-module │  │ alert-module │  │ audit-module        │ │
│  │                 │  │              │  │                     │ │
│  │ RecurringJob    │  │ Alert domain │  │ Redis subscriber    │ │
│  │ Reconciliation  │  │              │  │ AuditLog persistence│ │
│  └─────────────────┘  └──────────────┘  └─────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
           │                                         │
    ┌──────▼──────┐                         ┌────────▼────────┐
    │  PostgreSQL  │                         │     Redis       │
    │  (Neon)      │                         │  · JWT blacklist│
    │  · Flyway    │                         │  · Cache        │
    │  · ENUM types│                         │  · audit:log    │
    └─────────────┘                         └─────────────────┘
```

---

## Module Breakdown

| Module | Responsibility |
|---|---|
| `shared-module` | Cross-cutting concerns: JWT filter, security config, Redis pub/sub infrastructure, `UserPrincipal` interface, `PageResponse`, `InsufficientBalanceException`, `AuthService` |
| `customer-module` | Customer and `UserAuth` CRUD; login/logout/refresh endpoints; `UserDetailsService` implementation |
| `account-module` | Account lifecycle (create, lock, unlock, close); balance mutations (`deposit`, `withdraw`, `transfer`) with `SELECT FOR UPDATE` row-level locking; ownership verification |
| `transaction-module` | Transaction creation (PENDING-first), idempotency key deduplication, rollback with up to 3 retry attempts, search with `JpaSpecification` |
| `audit-module` | Consumes `AuditLogEvent` messages from Redis; persists to `audit_log` table; exposes query endpoints for ADMIN and USER roles |
| `schedule-module` | `RecurringTransactionJob` (dispatches due schedules every 60 s); `ReconciliationJob` (marks stale PENDING transactions FAILED every 60 s) |
| `alert-module` | Alert domain (amount threshold, rapid succession, unusual location detection) |
| `app-module` | Entry point only — composes all modules, Flyway migrations, Swagger/OpenAPI definition, dynamic port binding |

### Maven dependency graph

```
app-module
├── customer-module  →  shared-module
├── account-module   →  shared-module, customer-module, audit-module
├── transaction-module → shared-module, account-module
├── audit-module     →  shared-module
├── schedule-module  →  shared-module, transaction-module
└── alert-module     →  shared-module
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.4.5 |
| Web | Spring MVC, Spring Security 6 |
| Persistence | Spring Data JPA, Hibernate 6, Flyway |
| Database | PostgreSQL (Neon cloud), native ENUM types |
| Cache | Redis (Lettuce), Spring Cache (`@Cacheable`, `@CacheEvict`) |
| Messaging | Redis pub/sub (`StringRedisTemplate`) |
| Auth | JWT (JJWT 0.12.6), BCrypt password encoding, HttpOnly refresh-token cookie |
| Observability | Spring Actuator, Micrometer, Prometheus |
| API Docs | SpringDoc OpenAPI 2.8.6 (Swagger UI at `/swagger-ui.html`) |
| Testing | JUnit 5, Mockito, AssertJ, Spring Test, JaCoCo |
| Build | Maven multi-module |

---

## Database Schema

All tables use UUID primary keys. ENUMs are custom PostgreSQL types (e.g., `USER_ROLE_`, `TRANSACTION_STATUS_`) mapped via Hibernate `@ColumnTransformer` cast expressions. Flyway manages all DDL.

```
CUSTOMER_TYPE ──< CUSTOMER ──────────────── USER_AUTH
                      │
                      └──< ACCOUNT ──< TRANSACTION ──< TRANSACTION_ROLLBACK
                                │              │
                                │              └──< ALERT
                                │
                                └──< RECURRING_JOB ──< RECURRING_SCHEDULE
                                                              │
                                                    RECURRING_AUDIT_LOG

AUDIT_LOG (linked to any entity via entityType + entityId + accountId)
```

### Core tables

| Table | Key columns |
|---|---|
| `customer_type` | `type_name` (INDIVIDUAL / BUSINESS), `max_transaction_limit`, `daily_limit` |
| `customer` | `full_name`, `email`, `phone`, `national_id`, `customer_type_id` |
| `user_auth` | `customer_id` (1:1), `username`, `password_hash`, `role`, `active` |
| `account` | `account_number` (sequence), `balance`, `transaction_limit`, `status` |
| `transaction` | `idempotency_key` (UNIQUE), `transaction_type`, `transaction_status`, `source_account_id`, `target_account_id` |
| `transaction_rollback` | `attempt_number` (1–3), `rollback_status` |
| `audit_log` | `entity_type`, `entity_id`, `account_id`, `action`, `old_value`, `new_value`, `changed_by_role` |
| `recurring_job` | `job_type`, `amount`, `status` (ACTIVE / PAUSED / CANCELLED) |
| `recurring_schedule` | `cron_expression`, `next_run`, `last_run`, `retry_count`, `max_retries` |

### Account number generation

```sql
CREATE SEQUENCE account_number_seq START WITH 10000000 INCREMENT BY 1;
```

Each account gets an 8-digit number drawn from this sequence at creation time.

---

## Security Model

### Authentication flow

```
Client                        Server
  │                               │
  ├─ POST /api/auth/login ───────►│  AuthenticationManager validates credentials
  │                               │  UserDetailsService.loadUserByUsername()
  │                               │    → returns UserAuth (implements UserPrincipal)
  │                               │  JWTTokenProvider generates access + refresh tokens
  │◄── 200 {                      │
  │      accessToken,             │  Refresh token → HttpOnly Secure SameSite=Strict cookie
  │      customerId,              │  Access token  → response body (client stores in memory)
  │      role, username           │
  │    } ─────────────────────────┤
  │                               │
  ├─ GET /api/... ───────────────►│  JWTAuthFilter (runs before UsernamePasswordAuthFilter):
  │  Authorization: Bearer <tok>  │    1. Extract + validate JWT
  │                               │    2. Check token not blacklisted in Redis
  │                               │    3. Load UserAuth → set SecurityContext principal
  │◄── 200 ───────────────────────┤
  │                               │
  ├─ POST /api/auth/refresh ─────►│  Read HttpOnly refresh_token cookie
  │                               │  Validate JWT, verify type = "refresh"
  │                               │  Blacklist old refresh token (JTI → Redis with TTL)
  │◄── 200 { new token pair } ────┤  Issue new access + refresh tokens
  │                               │
  ├─ POST /api/auth/logout ──────►│  Blacklist access token + refresh token by JTI
  │◄── 204 ───────────────────────┤  Clear refresh_token cookie (maxAge = 0)
```

### JWT structure

| Claim | Value |
|---|---|
| `sub` | username (phone number) |
| `role` | `ADMIN` or `USER` |
| `type` | `access` or `refresh` |
| `jti` | UUID — used as Redis blacklist key |
| `exp` | access: 15 min · refresh: 7 days (configurable) |

The refresh token is never exposed in a response body. It travels exclusively as an HttpOnly cookie on `/api/auth` paths, preventing JavaScript access.

### Role-based access

| Role | Capabilities |
|---|---|
| `ADMIN` | Full access to all endpoints; audit logs include `oldValue` / `newValue` / `ipAddress`; actuator endpoints |
| `USER` | Own profile and password only; own accounts only; own transaction history; audit logs for own accounts (sensitive diff fields omitted) |

### Ownership verification

For USER-role requests targeting an account, the server resolves `customerId` from the principal (stored in `UserAuth`) and checks the account belongs to that customer:

```
userAuthRepository.findByUsername(phone) → customerId
accountRepository.existsByIdAndCustomerId(accountId, customerId) → 403 if false
```

---

## Transaction Lifecycle

The system uses a **PENDING-first, event-driven** state machine. The HTTP response returns immediately with `status: PENDING`; balance mutation happens asynchronously via Spring application events.

```
POST /api/transactions
        │
        ▼
[Idempotency check]──────── duplicate key? ──yes──► return existing tx (200)
        │ no
        ▼
[Validate]
  TRANSFER without targetAccountId? ──► 400
  source == target?                 ──► 400
        │
        ▼
[INSERT PENDING tx]  ← REQUIRES_NEW transaction (committed before event)
  DataIntegrityViolationException?
    ├── UniqueViolation (23505) ──► concurrent race ──► fetch winning tx
    └── FK violation   (23503) ──► 400 "Source/Target account not found"
        │
        ▼
[Publish ApplicationEvent]  ← REQUIRES_NEW transaction (committed)
  DepositRequestedEvent  /  WithdrawalRequestedEvent  /  TransferRequestedEvent
        │
        ▼
  200 { status: "PENDING" }  ◄── client receives immediately

══════════════════════════════════════════════════════════
  @Async  @TransactionalEventListener(AFTER_COMMIT)
══════════════════════════════════════════════════════════
        │
        ▼
[AccountTransactionService]
  SELECT … FOR UPDATE (row-level lock prevents concurrent balance races)
  Account.credit() / Account.debit()
  ├── InsufficientBalanceException ──► FAILED
  └── success
        │
        ▼
[TransferResultPublisher]  ← REQUIRES_NEW
  Publishes TransactionResultEvent
        │
        ├── success=true  ──► UPDATE tx status='COMPLETED'
        │                     INSERT audit_log (via Redis pub/sub)
        │
        └── success=false ──► UPDATE tx status='FAILED'

══════════════════════════════════════════════════════════
  ReconciliationJob — every 60 s
══════════════════════════════════════════════════════════
  PENDING tx older than 2 min ──► mark FAILED
  (handles lost events from crash or restart)
```

### Transaction rollback

Up to three reversal attempts are tracked in `transaction_rollback`.

```
POST /api/transactions/{id}/rollback   (ADMIN only)
        │
        ▼
[Guards]
  status != COMPLETED?      ──► 422
  status == ROLLED_BACK?    ──► 422
  attempts >= 3 exhausted?  ──► 422 RollbackExhaustedException
        │
        ▼
[ReversalExecutor]
  DEPOSIT    → reverseDeposit    (debit the credited account)
  WITHDRAWAL → reverseWithdraw   (credit the debited account)
  TRANSFER   → reverseTransfer   (debit target, credit source)
        │
        ├── success ──► INSERT rollback(SUCCESS)
        │               UPDATE tx status='ROLLED_BACK'
        │
        └── failure ──► INSERT rollback(FAILED / PERMANENTLY_FAILED)
                        attempt < 3: caller may retry
                        attempt = 3: PERMANENTLY_FAILED, no further retries
```

---

## Audit Log Pipeline

All state-changing operations publish an `AuditLogEvent` to Redis synchronously (fire-and-forget). The `audit-module` subscribes to the channel and persists events independently — a slow or failing audit write never blocks the business transaction.

```
Service layer                  Redis (audit:log)           audit-module
      │                               │                         │
      ├─ auditLogPublisher            │                         │
      │  .publish(event)             │                         │
      │  ObjectMapper → JSON ────────►│ pub to channel          │
      │                               │                         │
      │                               ├──── AuditLogSubscriber ►│
      │                               │     (MessageListener)   │
      │                               │                         ├─ deserialize JSON
      │                               │                         ├─ AuditLogPersistenceService
      │                               │                         │  @Transactional(REQUIRES_NEW)
      │                               │                         └─ auditLogRepository.save()
```

Events published by each module:

| Module | Actions |
|---|---|
| `customer-module` | `CREATE`, `UPDATE`, `DELETE` on CUSTOMER entity |
| `account-module` | `CREATE`, `UPDATE`, `ACCOUNT_LOCKED`, `ACCOUNT_ACTIVE`, `ACCOUNT_CLOSED` |
| `account-module` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `TRANSACTION_ROLLED_BACK` |

---

## Scheduled Jobs

### RecurringTransactionJob — every 60 s

```
RecurringScheduleRepository.findDue(now, ACTIVE)
        │
        └── for each schedule:
              RecurringDispatchService.dispatch(schedule)   ← REQUIRES_NEW
                ├── build TransactionRequest (from RecurringJob fields)
                ├── call TransactionService.createTransaction()
                ├── advance schedule.nextRun = CronExpression.next()
                ├── INSERT recurring_audit_log (SUCCESS)
                └── on failure: INSERT recurring_audit_log (FAILED)
                               increment retry_count; pause if max reached
```

Each schedule is isolated in its own transaction — one failure does not abort the batch.

### ReconciliationJob — every 60 s

Marks PENDING transactions older than 2 minutes as FAILED in batches of 100. This handles events that were published to Redis but never consumed (e.g., application crash between publish and delivery).

---

## API Reference

All endpoints require `Authorization: Bearer <access_token>` except `/api/auth/**`.

### Auth — `/api/auth`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/login` | Public | Authenticate; receive access token + set refresh cookie |
| `POST` | `/refresh` | Public | Rotate token pair using the HttpOnly refresh cookie |
| `POST` | `/logout` | Authenticated | Blacklist both tokens; clear refresh cookie |

### Customers — `/api/customers`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/` | ADMIN | Create customer and credential |
| `GET` | `/{id}` | ADMIN / USER (own) | Get customer by ID |
| `GET` | `/` | ADMIN | List all (paginated) |
| `GET` | `/search?name=` | ADMIN | Search by full name |
| `GET` | `/city?city=` | ADMIN | Filter by city |
| `PUT` | `/{id}/profile` | ADMIN / USER (own) | Update profile |
| `DELETE` | `/{id}` | ADMIN | Hard delete customer |
| `PUT` | `/{username}/status` | ADMIN | Activate or deactivate login |
| `PUT` | `/{id}/password` | USER (own) | Change password |

### Accounts — `/api/accounts`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/` | ADMIN | Open a new account |
| `GET` | `/{id}` | ADMIN / USER (own) | Get account detail |
| `GET` | `/` | ADMIN | List all accounts |
| `GET` | `/customer/{customerId}` | ADMIN / USER (own) | Accounts by customer |
| `PUT` | `/{id}/limit` | ADMIN | Update transaction limit |
| `PUT` | `/{id}/lock` | ADMIN | Lock account |
| `PUT` | `/{id}/unlock` | ADMIN | Unlock account |
| `DELETE` | `/{id}` | ADMIN | Close account (requires zero balance) |
| `GET` | `/{id}/audit-logs` | ADMIN / USER (own) | Audit history for account |

### Transactions — `/api/transactions`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/` | ADMIN / USER (own account) | Create DEPOSIT, WITHDRAWAL, or TRANSFER |
| `GET` | `/{id}` | ADMIN / USER (own) | Get transaction by ID |
| `GET` | `/account/{accountId}` | ADMIN / USER (own) | Transaction history (paginated) |
| `POST` | `/search` | ADMIN | Filter by type, status, date range |
| `POST` | `/{id}/rollback` | ADMIN | Initiate reversal (up to 3 attempts) |

### Audit — `/api/audit`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/` | ADMIN | All logs with pagination and sorting |
| `GET` | `/entity` | ADMIN / USER | Logs by `entityType`; USER must supply `accountId` and only sees own account |

Query params for `GET /entity`: `entityType` (required), `accountId` (required for USER), `page` (default 0), `size` (default 20).

### Recurring Jobs — `/api/recurring-jobs`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/` | ADMIN / USER | Create job with cron expression and amount |
| `GET` | `/{id}` | ADMIN / USER (own) | Get job and schedule detail |
| `GET` | `/account/{accountId}` | ADMIN / USER (own) | All jobs for an account |
| `PUT` | `/{id}/pause` | ADMIN / USER (own) | Pause job |
| `PUT` | `/{id}/activate` | ADMIN / USER (own) | Resume paused job |
| `PUT` | `/{id}/cancel` | ADMIN / USER (own) | Cancel job (irreversible) |

---

## Code Coverage

JaCoCo is configured in the parent `pom.xml` and inherited by every module. `app-module` generates a unified aggregate report across all modules.

```bash
# Run tests and generate reports
mvn verify

# Per-module HTML report
open <module>/target/site/jacoco/index.html

# Aggregate report (all modules combined)
open app-module/target/site/jacoco-aggregate/index.html
```

Excluded from coverage: `*Application.class`, `*ApplicationTests.class`, `**/config/**`, `**/DTO/**`, `**/migration/**`.

---

## Running the Application

### Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL (or Neon cloud connection string)
- Redis

### Build and run

```bash
# Clone
git clone <repo-url>
cd baking_system

# Build all modules (skip tests for a quick build)
mvn clean install -DskipTests

# Run
java -jar app-module/target/app-module-0.0.1-SNAPSHOT.jar
```

The server binds to `8080` by default. `PortConfig` auto-increments if that port is already in use.

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

Click **Authorize** and paste the access token from `POST /api/auth/login`.

### Actuator endpoints

```
http://localhost:8080/actuator/health       # public
http://localhost:8080/actuator/metrics      # ADMIN only
http://localhost:8080/actuator/prometheus   # ADMIN only
```

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | JDBC URL | required |
| `DB_USERNAME` | Database username | required |
| `DB_PASSWORD` | Database password | required |
| `REDIS_HOST` | Redis hostname | required |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | _(empty)_ |
| `JWT_SECRET` | HMAC-SHA signing key — minimum 32 characters (256-bit) | required |
| `JWT_EXPIRATION` | Access token TTL in seconds | `900` (15 min) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL in seconds | `604800` (7 days) |
