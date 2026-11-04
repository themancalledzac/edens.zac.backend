-- V63: store every users.email in lowercase, and make the database enforce it.
--
-- Every write path lowercases the email before it reaches the database, and every lookup
-- compares with plain `email = :email`. Rows written before that rule, or by the admin bootstrap
-- from a mixed-case config value, would be invisible to those lookups. The CHECK closes that gap
-- for good. PERSON rows have a NULL email, which the CHECK allows.
--
-- If two rows differ only by case, lowercasing would collide on the unique email constraint.
-- That needs a human to merge them, so the migration stops with the offending addresses.
DO $$
DECLARE
  offenders text;
BEGIN
  SELECT string_agg(key, ', ') INTO offenders
  FROM (
    SELECT LOWER(email) AS key
    FROM users
    WHERE email IS NOT NULL
    GROUP BY LOWER(email)
    HAVING count(*) > 1
  ) g;

  IF offenders IS NOT NULL THEN
    RAISE EXCEPTION
      'V63: these emails exist more than once when case is ignored: %. '
      'Merge them by hand (POST /api/admin/users/{targetId}/merge), then redeploy.',
      offenders;
  END IF;
END $$;

UPDATE users
   SET email = LOWER(email)
 WHERE email <> LOWER(email);

ALTER TABLE users
  ADD CONSTRAINT users_email_lowercase CHECK (email = LOWER(email));
