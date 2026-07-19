-- V48: fold PERSONAL roles into named SHARED roles (RBAC phase-2 EXPAND step).
-- Spec:  docs/superpowers/specs/2026-07-19-rbac-phase2-personal-role-fold.md  (section 3 invariants are BINDING)
-- Plan:  docs/superpowers/plans/2026-07-19-rbac-phase2-personal-role-fold.md  (Task 6)
--
-- Runs in ONE transaction (Flyway wraps each migration on Postgres). Any RAISE below rolls back the
-- ENTIRE migration, Flyway marks it failed, and the app refuses to start -- i.e. this migration
-- fails CLOSED (invariant 7) and never LOSES access silently (invariant 4).
--
-- Identifiers verified against the actual V45/V46/V47 files (2026-07-19):
--   role                 PK id; UNIQUE(name); kind has an INLINE CHECK (auto-named by Postgres,
--                        so the drop in step 11 is name-agnostic); created_at/created_by carry
--                        defaults, so INSERT (name, kind) is valid.
--   role_member          PK (role_id, user_id);       role_id ON DELETE CASCADE (V45:16).
--   role_collection      PK (role_id, collection_id); role_id ON DELETE CASCADE (V45:25);
--                        inherited_from_collection_id BIGINT NULL (V47:14) -- NULL means DIRECT grant.
--   waterfall hierarchy  role_collection -> collection_content(visible=true) -> content_collection
--                        -> referenced_collection_id (child). Step 7 mirrors V47's CTE VERBATIM.
--
-- fold_mapping is PROD DATA (which personal role folds into which named SHARED role) and is gated
-- on user approval of access GAINS (plan Task 1). It is intentionally left EMPTY in this file. With
-- an empty mapping AND zero PERSONAL roles (true of every Testcontainers + dev DB, since V45's
-- backfill created personal roles only from pre-existing user_collection rows), this migration is a
-- complete NO-OP apart from snapshotting the (empty) archive tables and tightening the kind CHECK.

-- 1. Rollback archive. Snapshot BEFORE any mutation. Retained through V49; a later janitorial
--    migration drops these once the fold is confirmed. (CREATE TABLE AS copies rows, not indexes.)
CREATE TABLE role_fold_archive_role            AS TABLE role;
CREATE TABLE role_fold_archive_role_member     AS TABLE role_member;
CREATE TABLE role_fold_archive_role_collection AS TABLE role_collection;

-- 2. Reviewed fold mapping: one row per PERSONAL role naming the SHARED role it folds into.
--    BEFORE PROD MERGE: populate fold_mapping VALUES from the Task 1 approved mapping, e.g.
--      INSERT INTO fold_mapping VALUES (3,'tylerabby'),(4,'tylerabby'),(1,'pnwer'),(2,'weigel');
--    Left EMPTY on purpose so this migration is a clean no-op at test/dev startup. Do NOT emit a
--    bare "INSERT INTO fold_mapping VALUES ;" -- that is a syntax error. An empty temp table is fine.
CREATE TEMP TABLE fold_mapping (
  personal_role_id BIGINT PRIMARY KEY,
  target_role_name TEXT   NOT NULL
) ON COMMIT DROP;

-- ===== V48_REPLAY_FOLD_STEPS_START (RoleFoldMigrationIntegrationTest replays steps 3-8 verbatim) =====

-- 3. Invariant 7 (fail closed): every PERSONAL role must be mapped. Keyed on kind, NEVER on name --
--    UserMergeService may have re-pointed members and the admin form could have named roles freely.
--    Uses correlated NOT EXISTS (not NOT IN): NOT IN evaluates to NULL if any personal_role_id were
--    NULL, which would silently pass the guard -- NOT EXISTS stays fail-closed.
DO $guard$
BEGIN
  IF EXISTS (
    SELECT 1 FROM role r
     WHERE r.kind = 'PERSONAL'
       AND NOT EXISTS (SELECT 1 FROM fold_mapping m WHERE m.personal_role_id = r.id)
  ) THEN
    RAISE EXCEPTION 'V48: unmapped PERSONAL role present -- extend fold_mapping (invariant 7)';
  END IF;
END $guard$;

-- 4. Create any missing target SHARED roles named by the mapping.
INSERT INTO role (name, kind)
SELECT DISTINCT m.target_role_name, 'SHARED'
  FROM fold_mapping m
 WHERE NOT EXISTS (SELECT 1 FROM role r WHERE r.name = m.target_role_name);

-- 5. Fold memberships (keyed on role_member, NEVER on the user: name pattern -- invariant 6).
INSERT INTO role_member (role_id, user_id)
SELECT t.id, rm.user_id
  FROM fold_mapping m
  JOIN role        t  ON t.name    = m.target_role_name
  JOIN role_member rm ON rm.role_id = m.personal_role_id
ON CONFLICT DO NOTHING;

-- 6. Fold DIRECT grants only (inherited_from IS NULL), MAX(level) per (target, collection).
--    bool_or CLIENT unions multiple personal roles that fold into the same target. On conflict with
--    a pre-existing target grant: take MAX level (never downgrade CLIENT->GENERAL -- invariant 4 /
--    hazard B1) AND clear inherited_from_collection_id. Every incoming row here is a genuine DIRECT
--    grant (the SELECT filters inherited_from IS NULL), so converting the target row to direct is
--    ALWAYS correct: it mirrors RoleRepository.setCollectionGrant (RoleRepository.java:168-186), so a
--    folded grant is never later swept away by removeInheritedGrantsByOrigin (:273-282) when the
--    target's parent grant is removed. (A collision-regression test asserts this.)
INSERT INTO role_collection (role_id, collection_id, level)
SELECT t.id, rc.collection_id,
       CASE WHEN bool_or(rc.level = 'CLIENT') THEN 'CLIENT' ELSE 'GENERAL' END
  FROM fold_mapping m
  JOIN role            t  ON t.name     = m.target_role_name
  JOIN role_collection rc ON rc.role_id = m.personal_role_id
 WHERE rc.inherited_from_collection_id IS NULL
 GROUP BY t.id, rc.collection_id
ON CONFLICT (role_id, collection_id) DO UPDATE
  SET level = CASE WHEN role_collection.level = 'CLIENT' OR EXCLUDED.level = 'CLIENT'
                   THEN 'CLIENT' ELSE 'GENERAL' END,
      inherited_from_collection_id = NULL;

-- 7. Re-materialize the waterfall for the target roles. Mirrors V47's recursive CTE VERBATIM, scoped
--    to fold targets. NOT EXISTS + ON CONFLICT DO NOTHING keep every pre-existing/direct row intact
--    (direct wins), so this only ADDS inherited descendants for the newly folded direct grants.
WITH RECURSIVE descendant AS (
  SELECT rc.role_id,
         rc.collection_id AS origin_id,
         rc.level,
         cct.referenced_collection_id AS descendant_id
    FROM role_collection rc
    JOIN collection_content  cc  ON cc.collection_id = rc.collection_id AND cc.visible = true
    JOIN content_collection  cct ON cct.id = cc.content_id
   WHERE rc.inherited_from_collection_id IS NULL
     AND cct.referenced_collection_id IS NOT NULL
     AND rc.role_id IN (SELECT DISTINCT t.id
                          FROM fold_mapping m JOIN role t ON t.name = m.target_role_name)
  UNION
  SELECT d.role_id, d.origin_id, d.level, cct.referenced_collection_id
    FROM descendant d
    JOIN collection_content  cc  ON cc.collection_id = d.descendant_id AND cc.visible = true
    JOIN content_collection  cct ON cct.id = cc.content_id
   WHERE cct.referenced_collection_id IS NOT NULL
)
INSERT INTO role_collection (role_id, collection_id, level, inherited_from_collection_id)
SELECT DISTINCT ON (d.role_id, d.descendant_id)
       d.role_id, d.descendant_id, d.level, d.origin_id
  FROM descendant d
 WHERE NOT EXISTS (SELECT 1 FROM role_collection rc
                    WHERE rc.role_id = d.role_id AND rc.collection_id = d.descendant_id)
 ORDER BY d.role_id, d.descendant_id, (d.level = 'CLIENT') DESC
ON CONFLICT (role_id, collection_id) DO NOTHING;

-- 8. Delete PERSONAL roles. CASCADE (V45:16,25) removes their memberships AND all their grants
--    (direct + inherited rows share the personal role_id), so no waterfall orphans remain.
DELETE FROM role WHERE kind = 'PERSONAL';

-- ===== V48_REPLAY_FOLD_STEPS_END =====

-- ===== V48_REPLAY_LOSS_GATE_START (replayed AFTER the delete; any RAISE rolls back the whole tx) =====

-- 9. Invariant 4 (must not LOSE access). Compare per-user effective access BEFORE (archive) vs
--    AFTER (live). RAISE on any (user, collection) that is absent-after or downgraded CLIENT->GENERAL.
--    Runs AFTER the delete so it validates the true end state.
DO $gate$
DECLARE lost RECORD;
BEGIN
  FOR lost IN
    WITH before AS (
      SELECT rm.user_id, rc.collection_id, bool_or(rc.level = 'CLIENT') AS was_client
        FROM role_fold_archive_role_member     rm
        JOIN role_fold_archive_role_collection rc ON rc.role_id = rm.role_id
       GROUP BY rm.user_id, rc.collection_id),
    after AS (
      SELECT rm.user_id, rc.collection_id, bool_or(rc.level = 'CLIENT') AS is_client
        FROM role_member     rm
        JOIN role_collection rc ON rc.role_id = rm.role_id
       GROUP BY rm.user_id, rc.collection_id)
    SELECT b.user_id, b.collection_id
      FROM before b
      LEFT JOIN after a ON a.user_id = b.user_id AND a.collection_id = b.collection_id
     WHERE a.user_id IS NULL OR (b.was_client AND NOT a.is_client)
  LOOP
    RAISE EXCEPTION 'V48 ACCESS LOSS: user % lost collection % (invariant 4)',
      lost.user_id, lost.collection_id;
  END LOOP;
END $gate$;

-- ===== V48_REPLAY_LOSS_GATE_END =====

-- 10. Gains report (invariant 5): (user, collection) present/upgraded AFTER but not BEFORE. Inspected
--     post-deploy against the Task 1 approved gains list. Persisted for the operator, not a gate.
CREATE TABLE role_fold_gain_report AS
  WITH before AS (
    SELECT rm.user_id, rc.collection_id, bool_or(rc.level = 'CLIENT') AS was_client
      FROM role_fold_archive_role_member     rm
      JOIN role_fold_archive_role_collection rc ON rc.role_id = rm.role_id
     GROUP BY rm.user_id, rc.collection_id),
  after AS (
    SELECT rm.user_id, rc.collection_id, bool_or(rc.level = 'CLIENT') AS is_client
      FROM role_member     rm
      JOIN role_collection rc ON rc.role_id = rm.role_id
     GROUP BY rm.user_id, rc.collection_id)
  SELECT a.user_id, a.collection_id, a.is_client
    FROM after a
    LEFT JOIN before b ON b.user_id = a.user_id AND b.collection_id = a.collection_id
   WHERE b.user_id IS NULL OR (a.is_client AND NOT b.was_client);

-- 11. Tighten the kind CHECK to SHARED-only. The V45 CHECK is inline/auto-named, so drop it
--     name-agnostically, then ADD a canonically-named constraint (V49 later drops role_kind_check).
DO $tighten$
DECLARE cn text;
BEGIN
  SELECT conname INTO cn
    FROM pg_constraint
   WHERE conrelid = 'role'::regclass
     AND contype = 'c'
     AND pg_get_constraintdef(oid) ILIKE '%kind%';
  IF cn IS NOT NULL THEN
    EXECUTE format('ALTER TABLE role DROP CONSTRAINT %I', cn);
  END IF;
END $tighten$;
ALTER TABLE role ADD CONSTRAINT role_kind_check CHECK (kind IN ('SHARED'));
