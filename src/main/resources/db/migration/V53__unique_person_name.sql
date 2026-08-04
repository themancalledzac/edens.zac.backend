-- V53: one identity per name. Folds duplicate-name rows together, then enforces the
-- case-insensitive uniqueness of `users.name` that the person-tag lookup already assumes.
--
-- Why this is needed:
--   Person tags are keyed by NAME, not by id. The Lightroom plugin sends a bare person name
--   ("Tara Edens") per image, and ContentMutationUtil.associateExtractedKeywords resolves it
--   through PersonRepository.findByPersonNameIgnoreCase, which is a queryForObject -- exactly
--   one row or it throws IncorrectResultSizeDataAccessException. Nothing enforced that. Before
--   V35 the person table was separate and name-keyed; the merge into `users` carried the
--   name-keyed lookup over but not any uniqueness on the column (`app_user_email_key` and
--   `app_user_webauthn_user_handle_key` are the only unique constraints on the table).
--
--   The pre-existing `tag` table already models this correctly -- content_tag_tag_name_key is
--   UNIQUE on tag_name -- so a name-keyed metadata table carrying a uniqueness constraint is
--   the established shape here, not a new invention.
--
--   The duplicates in practice came from the invite flow: before the /users/{id}/upgrade
--   endpoint existed, inviting someone who was already a tagged PERSON inserted a SECOND row
--   rather than upgrading the tag row in place. Every subsequent Lightroom export that tagged
--   that person then threw inside associateExtractedKeywords -- which caught Exception and
--   logged a WARN -- so the upload reported success while silently dropping the person.
--
-- Audit queries -- run manually before deploying (same convention as V51 lines 27-58):
--
-- -- 1. Every duplicate-name group this migration will collapse, with the status of each row.
-- --    A group whose rows are ALL status <> 'PERSON' is the one shape step 1 cannot resolve
-- --    and will abort on; rename one of those accounts by hand first.
-- SELECT LOWER(name) AS key, count(*), array_agg(id ORDER BY id), array_agg(status ORDER BY id)
-- FROM users GROUP BY 1 HAVING count(*) > 1 ORDER BY 1;
--
-- -- 2. Tag volume at stake -- how many image/collection tags ride on the rows being folded.
-- SELECT u.id, u.name, u.status,
--        (SELECT count(*) FROM content_image_people WHERE person_id = u.id) AS image_tags,
--        (SELECT count(*) FROM collection_people   WHERE person_id = u.id) AS collection_tags
-- FROM users u
-- WHERE LOWER(u.name) IN (SELECT LOWER(name) FROM users GROUP BY 1 HAVING count(*) > 1)
-- ORDER BY LOWER(u.name), u.id;

-- 1. Abort loudly rather than destroy an account. Steps 2-4 only ever delete a tag-only PERSON
--    row; if a duplicate-name group holds two real accounts there is no safe automatic winner,
--    and silently renaming one would break its owner's login display. Failing here leaves the
--    schema untouched and blocks boot with an actionable message (spring.flyway.enabled=true,
--    application.properties:36), which is the correct outcome -- the alternative is the CREATE
--    UNIQUE INDEX in step 5 failing with an opaque "could not create unique index".
DO $$
DECLARE
  offenders text;
BEGIN
  SELECT string_agg(key, ', ') INTO offenders
  FROM (
    SELECT LOWER(name) AS key
    FROM users
    GROUP BY LOWER(name)
    HAVING count(*) > 1 AND count(*) FILTER (WHERE status <> 'PERSON') > 1
  ) g;

  IF offenders IS NOT NULL THEN
    RAISE EXCEPTION
      'V53: cannot collapse duplicate user names -- these have more than one real account: %. '
      'Rename or merge them by hand (POST /api/admin/users/{targetId}/merge), then redeploy.',
      offenders;
  END IF;
END $$;

-- 2. The survivor of each duplicate-name group, and the tag-only PERSON rows folding into it.
--    Survivor rule: a real account outranks a tag-only PERSON (step 1 has already guaranteed at
--    most one account per group), then lowest id. That direction matters -- the account row is
--    the one carrying the login, so tags must move ONTO it, never off it.
--
--    A temp table rather than a CTE because steps 3-4 each need the same mapping, and ON COMMIT
--    DROP scopes it to this migration's transaction.
CREATE TEMP TABLE v53_fold ON COMMIT DROP AS
WITH ranked AS (
  SELECT
    id,
    LOWER(name) AS key,
    row_number() OVER (
      PARTITION BY LOWER(name)
      ORDER BY (status = 'PERSON'), id
    ) AS rn
  FROM users
)
SELECT loser.id AS source_id, winner.id AS target_id
FROM ranked loser
JOIN ranked winner ON winner.key = loser.key AND winner.rn = 1
WHERE loser.rn > 1;

-- 3. Re-point the tag joins, mirroring PersonRepository.repointTags. All three join tables have
--    a composite primary key (content_image_people_pkey on (content_id, person_id),
--    collection_people_pkey on (collection_id, person_id), role_member_pkey on (role_id,
--    user_id)), so a row that already exists on the survivor would collide -- delete the
--    source's colliding rows FIRST, then move the remainder.
DELETE FROM content_image_people s
USING v53_fold f
WHERE s.person_id = f.source_id
  AND EXISTS (SELECT 1 FROM content_image_people t
              WHERE t.person_id = f.target_id AND t.content_id = s.content_id);

UPDATE content_image_people s SET person_id = f.target_id
FROM v53_fold f WHERE s.person_id = f.source_id;

DELETE FROM collection_people s
USING v53_fold f
WHERE s.person_id = f.source_id
  AND EXISTS (SELECT 1 FROM collection_people t
              WHERE t.person_id = f.target_id AND t.collection_id = s.collection_id);

UPDATE collection_people s SET person_id = f.target_id
FROM v53_fold f WHERE s.person_id = f.source_id;

-- role_member too, mirroring RoleRepository.repointMemberships. A tag-only PERSON should not
-- hold memberships, but UserMergeService moves them, so this stays consistent with it.
DELETE FROM role_member s
USING v53_fold f
WHERE s.user_id = f.source_id
  AND EXISTS (SELECT 1 FROM role_member t
              WHERE t.user_id = f.target_id AND t.role_id = s.role_id);

UPDATE role_member s SET user_id = f.target_id
FROM v53_fold f WHERE s.user_id = f.source_id;

-- 4. Drop the folded rows. The `status = 'PERSON'` guard mirrors
--    PersonRepository.deletePersonById and is defense-in-depth: step 1 already proved every
--    loser is a PERSON, so this deletes exactly the rows step 3 emptied.
DELETE FROM users u
USING v53_fold f
WHERE u.id = f.source_id AND u.status = 'PERSON';

-- 5. The invariant itself. A functional index on LOWER(name) is what makes
--    findByPersonNameIgnoreCase ("WHERE LOWER(name) = LOWER(:personName)") total -- it can now
--    only ever return zero or one row -- and it serves that query as an index rather than a
--    sequential scan.
--
--    Note this constrains display names globally: two distinct accounts can no longer share a
--    name. That is inherent to name-keyed person tags, not a restriction this migration
--    invents -- with two "John Smith" rows there is no answer to which one an incoming
--    Lightroom keyword means. Disambiguate at the name ("John Smith (PNWER)").
CREATE UNIQUE INDEX idx_users_name_lower ON users (LOWER(name));
