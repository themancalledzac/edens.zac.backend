-- V15: Add PARENT collection type support
-- The collection.type column is VARCHAR, so no schema change is required.
-- PARENT and HOME collections can only contain child collection content (enforced in application layer).
--
-- Audit query: find any non-collection content currently linked to HOME collections
-- Run manually before deploying to verify no data conflicts:
--
-- SELECT cc.id, cc.collection_id, cc.content_id, c.type AS collection_type, ct.content_type
-- FROM collection_content cc
-- JOIN collection c ON cc.collection_id = c.id
-- JOIN content ct ON cc.content_id = ct.id
-- WHERE c.type = 'HOME' AND ct.content_type != 'COLLECTION';

-- No-op migration: PARENT is a new application-level enum value, no DDL needed.
SELECT 1;
