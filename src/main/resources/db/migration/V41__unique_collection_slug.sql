-- V41: Enforce uniqueness on collection.slug.
--
-- Slug is the resolution key for /{slug}: CollectionRepository.findBySlug uses queryForObject and
-- throws if it ever matches more than one row. Two concurrent tag->collection converts (TagService)
-- can both pass the app-level findBySlug check and insert the same slug, after which findBySlug 500s
-- permanently for that slug. Existing slug indexes are NON-unique (V6 idx_collection_slug, V9's
-- partial idx_collection_slug_visible); this adds the missing UNIQUE guarantee — matching V8's
-- UNIQUE slug indexes on location/tag/content_people.
--
-- Safe to add without a data cleanup: duplicate slugs would ALREADY break findBySlug (queryForObject)
-- today, so the live DB provably contains none and this index will build. The app-level findBySlug
-- pre-check is retained as a friendly 409-with-message; this index is the last-line race guard.

CREATE UNIQUE INDEX IF NOT EXISTS idx_collection_slug_unique ON collection (slug);
