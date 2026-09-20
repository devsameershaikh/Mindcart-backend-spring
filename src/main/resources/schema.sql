-- ============================================
-- 1. DROP EXISTING TABLES
-- ============================================

DROP TABLE IF EXISTS "invites" CASCADE;
DROP TABLE IF EXISTS "list_members" CASCADE;
DROP TABLE IF EXISTS "items" CASCADE;
DROP TABLE IF EXISTS "lists" CASCADE;
DROP TABLE IF EXISTS "users" CASCADE;


-- ============================================
-- 2. DROP POSTGRESQL ENUM TYPES
-- ============================================

DROP TYPE IF EXISTS "invite_status" CASCADE;
DROP TYPE IF EXISTS "role" CASCADE;


-- ============================================
-- 3. USERS
-- ============================================

CREATE TABLE "users" (
    "id"          VARCHAR(36) PRIMARY KEY,
    "google_id"   VARCHAR(255) NOT NULL UNIQUE,
    "email"       VARCHAR(320) NOT NULL UNIQUE,
    "name"        VARCHAR(255),
    "avatar_url"  TEXT,
    "created_at"  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================
-- 4. LISTS
-- ============================================

CREATE TABLE "lists" (
    "id"         VARCHAR(36) PRIMARY KEY,
    "name"       VARCHAR(255) NOT NULL,
    "owner_id"   VARCHAR(36) NOT NULL
                 REFERENCES "users"("id")
                 ON DELETE CASCADE,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX "list_owner_id_idx"
ON "lists"("owner_id");


-- ============================================
-- 5. LIST MEMBERS
-- ============================================

CREATE TABLE "list_members" (
    "id"        VARCHAR(36) PRIMARY KEY,
    "list_id"   VARCHAR(36) NOT NULL
                REFERENCES "lists"("id")
                ON DELETE CASCADE,
    "user_id"   VARCHAR(36) NOT NULL
                REFERENCES "users"("id")
                ON DELETE CASCADE,

    -- Normal VARCHAR instead of PostgreSQL enum
    "role"      VARCHAR(20) NOT NULL DEFAULT 'READ',

    "joined_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "list_members_list_user_unique"
        UNIQUE ("list_id", "user_id")
);

CREATE INDEX "list_members_user_id_idx"
ON "list_members"("user_id");


-- ============================================
-- 6. ITEMS
-- ============================================

CREATE TABLE "items" (
    "id"         VARCHAR(36) PRIMARY KEY,
    "list_id"    VARCHAR(36) NOT NULL
                 REFERENCES "lists"("id")
                 ON DELETE CASCADE,
    "name"       VARCHAR(40) NOT NULL,
    "category"   VARCHAR(100) NOT NULL,
    "qty"        INTEGER NOT NULL DEFAULT 0,
    "unit"       VARCHAR(50) NOT NULL,
    "price"      NUMERIC(12, 2),
    "checked"    BOOLEAN NOT NULL DEFAULT FALSE,
    "skipped"    BOOLEAN NOT NULL DEFAULT FALSE,
    "note"       TEXT,
    "created_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_by" VARCHAR(36)
);

CREATE INDEX "items_list_id_idx"
ON "items"("list_id");


-- ============================================
-- 7. INVITES
-- ============================================

CREATE TABLE "invites" (
    "id"               VARCHAR(36) PRIMARY KEY,

    "list_id"          VARCHAR(36)
                       REFERENCES "lists"("id")
                       ON DELETE CASCADE,

    "invite_all_lists" BOOLEAN NOT NULL DEFAULT FALSE,

    "sender_id"        VARCHAR(36) NOT NULL
                       REFERENCES "users"("id")
                       ON DELETE RESTRICT,

    "recipient_email"  VARCHAR(320) NOT NULL,

    "recipient_id"     VARCHAR(36)
                       REFERENCES "users"("id")
                       ON DELETE SET NULL,

    -- Normal VARCHAR instead of PostgreSQL enum
    "role"             VARCHAR(20) NOT NULL DEFAULT 'READ',

    -- Normal VARCHAR instead of PostgreSQL enum
    "status"           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    "token"            VARCHAR(64) NOT NULL UNIQUE,
    "created_at"       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    "responded_at"     TIMESTAMP
);

CREATE INDEX "invites_recipient_email_idx"
ON "invites"("recipient_email");

CREATE INDEX "invites_sender_id_idx"
ON "invites"("sender_id");