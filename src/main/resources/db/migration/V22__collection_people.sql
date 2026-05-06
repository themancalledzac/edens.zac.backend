-- V22: Add collection_people join table mirroring content_image_people.
-- Allows a Collection to have its own People list (independent from per-image people),
-- powering the FE filter row on /all-collections and per-type list pages.
--
-- IF NOT EXISTS guards: V9 already creates indexes against this table, and the
-- table itself is referenced by existing code (CollectionRepository.saveCollectionPeople,
-- PersonRepository.deleteAllAssociationsByPersonId). On environments where the table
-- was previously auto-created (or created out-of-band), this migration is a no-op.
-- Person table is named "content_people" (column person_name); this join uses person_id
-- against content_people.id.

CREATE TABLE IF NOT EXISTS collection_people (
    collection_id BIGINT NOT NULL REFERENCES collection(id)      ON DELETE CASCADE,
    person_id     BIGINT NOT NULL REFERENCES content_people(id)  ON DELETE CASCADE,
    PRIMARY KEY (collection_id, person_id)
);

CREATE INDEX IF NOT EXISTS idx_collection_people_collection ON collection_people(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_people_person     ON collection_people(person_id);
