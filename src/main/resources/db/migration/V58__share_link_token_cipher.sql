-- V58: let a share link's owner see their own live link again.
--
-- V56 stored only the SHA-256 of the token, copied from user_invite. That is right for a
-- single-use invite and wrong here: it makes the raw link visible exactly once, in the response
-- that mints it. A week later the owner cannot send the same link to a second person -- their only
-- option is a reset, which breaks the recipient already using it. A share link is meant to be a
-- durable thing you can text or email repeatedly until you deliberately invalidate it.
--
-- token_hash KEEPS its job as the unique lookup index, so token resolution is unchanged. This
-- column holds the same token encrypted (AES-GCM, key derived from app.access-token.secret) purely
-- so the owner's own page can display it. Encrypted rather than plaintext because the key lives in
-- config, not the database: a dump on its own still yields nothing usable, which is the property
-- hashing was there to provide.
--
-- Nullable: rows minted under V56 have no recoverable token. The owner's page reports those as
-- needing a reset rather than failing.
ALTER TABLE share_link
  ADD COLUMN token_cipher VARCHAR(512);
