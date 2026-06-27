-- Link a person-tag to an auth account (Phase C · User Concept). Nullable: un-linked persons stay
-- simple metadata. ON DELETE SET NULL: deleting an account unlinks the tag, never destroys metadata.
ALTER TABLE content_people
  ADD COLUMN user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL;

-- One user maps to at most one person identity.
CREATE UNIQUE INDEX uq_content_people_user_id
  ON content_people(user_id) WHERE user_id IS NOT NULL;
