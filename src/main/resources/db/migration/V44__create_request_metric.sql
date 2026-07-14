-- V44: Private, admin-only aggregate API request counts. A directional "what can we learn
-- from the API reads we get" datapoint — NOT visitor analytics. NO PII is stored: no ip,
-- no user id, no user-agent, no per-request rows. Only a daily count per (route pattern, slug).
--
-- `route` is the Spring handler PATTERN (e.g. /api/read/collections/{slug}), not the raw path,
-- so cardinality stays bounded (a slug does not explode into thousands of distinct routes).
-- `slug` is the resolved path variable when the route has one (e.g. the collection slug),
-- NULL for routes without one. Counts undercount real reads (Next ISR + browser + CloudFront
-- caching serve most repeat hits, and image binaries never reach the backend) — that is known
-- and acceptable.
CREATE TABLE request_metric (
  day   DATE         NOT NULL,
  route VARCHAR(255) NOT NULL,
  slug  VARCHAR(255) NULL,
  count BIGINT       NOT NULL DEFAULT 0
);

-- Uniqueness on (day, route, slug) with NULL treated as a single distinct key. Postgres
-- normally treats NULLs as distinct in a unique index (so NULL-slug rows would never conflict
-- and the upsert's ON CONFLICT could not match them). COALESCE(slug, '') collapses every
-- NULL-slug row for a (day, route) to the same key. The upsert MUST use the identical
-- expression as its ON CONFLICT target: ON CONFLICT (day, route, (COALESCE(slug, ''))).
CREATE UNIQUE INDEX ux_request_metric_day_route_slug
  ON request_metric (day, route, (COALESCE(slug, '')));

-- Range scans by day for the admin read endpoint (?from&to).
CREATE INDEX idx_request_metric_day ON request_metric (day);
