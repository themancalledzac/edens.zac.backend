-- format_type is a lowercase wire value ("markdown", "html", "plain", ...): TextFormType
-- serializes getValue(), the ContentTextEntity docblock documents lowercase, and every read path
-- hands the column straight to the client. But createTextContent stored TextFormType.name(), so
-- text blocks created through that path landed as "MARKDOWN" while everything else was "markdown".
--
-- Fold the uppercase rows back to the wire form. Idempotent: rows already lowercase are untouched.
UPDATE content_text
SET format_type = LOWER(format_type)
WHERE format_type IS NOT NULL
  AND format_type <> LOWER(format_type);
