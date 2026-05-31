-- V26__collection_sibling
-- Description: Mutual ("sibling") association between collections. Rows are stored
-- reciprocally: a sibling link between A and B is two rows (A,B) and (B,A), so
-- "siblings of X" is a single-direction lookup on collection_id. ON DELETE CASCADE
-- cleans up both directions when either collection is deleted.

BEGIN;

CREATE TABLE collection_sibling (
    collection_id         BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    sibling_collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, sibling_collection_id),
    CONSTRAINT chk_collection_sibling_not_self CHECK (collection_id <> sibling_collection_id)
);

CREATE INDEX idx_collection_sibling_collection ON collection_sibling(collection_id);
CREATE INDEX idx_collection_sibling_sibling    ON collection_sibling(sibling_collection_id);

COMMIT;
