-- V61: Give messages a read marker, so read state survives the browser that set it.
--
-- A timestamp rather than a boolean: it answers "is it read" (NULL or not) and "when was it first
-- read" with one column, and mark-unread is the same UPDATE writing NULL. The write uses
-- COALESCE(read_at, NOW()), so marking an already-read message read again keeps the original time.
--
-- The index is partial and carries the list's ORDER BY. The unread filter is the only one that
-- needs an index -- it is the selective side (unread shrinks as mail is triaged, read grows without
-- bound), and it is the query the admin list runs by default once the filter ships. Reading the
-- whole table already has idx_messages_created_at from V17.

ALTER TABLE messages ADD COLUMN read_at TIMESTAMP NULL;

CREATE INDEX idx_messages_unread ON messages (created_at DESC) WHERE read_at IS NULL;

COMMENT ON COLUMN messages.read_at IS
  'When an admin first marked this message read; NULL means unread. Set by '
  'PATCH /api/admin/messages/{id}/read.';
