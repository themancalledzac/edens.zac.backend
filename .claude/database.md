# Database Schema

Schema evolution is managed by Flyway (`src/main/resources/db/migration`, currently V2..V52).
Tables that pre-date Flyway are reconstructed for tests by `src/test/resources/db/test-base-schema.sql`,
which Flyway then migrates on top of.

## Primary Tables

### content (base table)
```sql
id              BIGSERIAL PRIMARY KEY
content_type    VARCHAR NOT NULL  -- IMAGE, TEXT, GIF, COLLECTION
created_at      TIMESTAMP NOT NULL
updated_at      TIMESTAMP NOT NULL
```

### content_image (extends content)
```sql
id                 BIGINT PRIMARY KEY (FK -> content.id)
title              VARCHAR(255)
caption            TEXT              -- V26
alt                VARCHAR(500)      -- V26
image_width        INTEGER
image_height       INTEGER
iso                INTEGER
author             VARCHAR(100)
rating             INTEGER
f_stop             VARCHAR(20)
shutter_speed      VARCHAR(50)
focal_length       VARCHAR(50)
camera_id          BIGINT FK -> content_cameras.id
lens_id            BIGINT FK -> content_lenses.id
film_type_id       BIGINT FK -> content_film_types.id
film_format        VARCHAR(20)       -- 35MM, 120, 4X5, ...
black_and_white    BOOLEAN
is_film            BOOLEAN
image_url_web      VARCHAR(1024) NOT NULL
image_url_original VARCHAR(1024)
image_url_raw      VARCHAR(512)      -- V10
capture_date       TIMESTAMP         -- V4 (DATE) -> V7/V14 (TIMESTAMP)
last_export_date   TIMESTAMP         -- V4, dedupe
original_filename  VARCHAR(512)      -- V4, dedupe
```

Partial unique dedupe index on `(original_filename, capture_date)` where both are non-null.
`create_date` and `file_identifier` were dropped by V4 -- do not reference them.

### content_text / content_gif / content_collection (extend content)
```sql
-- content_text
id            BIGINT PRIMARY KEY (FK -> content.id)
text_content  TEXT NOT NULL
format_type   VARCHAR(50)

-- content_gif
id            BIGINT PRIMARY KEY (FK -> content.id)
title         VARCHAR(255)
gif_url       VARCHAR(1024)   -- 2000px "full" master
gif_url_web   VARCHAR(512)    -- V25, 1080px "web" display variant
thumbnail_url VARCHAR(1024)
width         INTEGER
height        INTEGER
author        VARCHAR(100)
create_date   VARCHAR(50)     -- still present here; only content_image dropped it (V4)
rating        INTEGER         -- V24
capture_date  TIMESTAMP       -- V49

-- content_collection (a reference to another collection, rendered as a content block)
id                       BIGINT PRIMARY KEY (FK -> content.id)
referenced_collection_id BIGINT NOT NULL FK -> collection.id
```

### collection
```sql
id                  BIGSERIAL PRIMARY KEY
is_client           BOOLEAN NOT NULL DEFAULT FALSE  -- V50: client gallery, password/role gated
is_blog             BOOLEAN NOT NULL DEFAULT FALSE  -- V50: appears in blog listings
title               VARCHAR(100) NOT NULL
slug                VARCHAR(150) NOT NULL UNIQUE    -- V41: idx_collection_slug_unique
description         VARCHAR(500)
collection_date     DATE                            -- start of the date range
collection_end_date DATE                            -- V43: inclusive end; NULL = single-day
visibility          VARCHAR(16) NOT NULL            -- V20: LISTED, UNLISTED, HIDDEN
rating              INTEGER                         -- V21: NULL or 0-5
display_mode        VARCHAR(50)
cover_image_id      BIGINT
content_per_page    INTEGER
total_content       INTEGER
rows_wide           INT
gallery_password    VARCHAR(255)                    -- V18: plaintext, client galleries only
recipient_emails    TEXT[] NOT NULL DEFAULT '{}'    -- V18
created_at          TIMESTAMP NOT NULL
updated_at          TIMESTAMP NOT NULL
```

Check constraints: `chk_collection_client_blog_excl` (NOT (is_client AND is_blog)),
`collection_visibility_chk`, `collection_rating_chk`.

**There is no `type` column.** V52 dropped it. A collection is a named, slugged, ordered grouping
of any mix of content, and `is_client` / `is_blog` are the only two stored discriminators. They are
mutually exclusive; carrying neither is a valid, meaningful state. Everything else is derived or
gone:

| Former `type` value | Where it lives now |
|---|---|
| `CLIENT_GALLERY` | `collection.is_client = true` (stored) |
| `BLOG` | `collection.is_blog = true` (stored) |
| `PARENT` | Derived, never stored: the collection holds >= 1 COLLECTION content block. Computed server-side and exposed as `hasChildren` / `childCollectionIds` (`CollectionRepository.hasChildCollections`). |
| `HOME` | Derived, never stored: `slug = 'home'`. V41's unique slug index guarantees at most one. |
| `PORTFOLIO` | Gone. No successor concept. |
| `ART_GALLERY` | Gone. No successor concept. |
| `MISC` | Gone. No successor concept -- carrying neither flag *is* the meaning. |

Never add `"type"` to a `collection` projection, INSERT or UPDATE: the column does not exist and
naming it fails at runtime.

### collection_type_archive (retain indefinitely)
```sql
id           BIGINT     -- collection.id at snapshot time; no PK/FK, created by CTAS
slug         VARCHAR
type         VARCHAR    -- the dropped enum value
is_client    BOOLEAN
is_blog      BOOLEAN
archived_at  TIMESTAMP
```

Created by V51 as `CREATE TABLE ... AS SELECT` over `collection` immediately before V52 dropped the
column. **Do not drop this table.** `PORTFOLIO`, `ART_GALLERY`, `PARENT`, `HOME` and `MISC` are all
`is_client = false, is_blog = false`, so five distinct former values collapse onto one flag pair and
the old `type` is *not* reconstructable from the flags. This table is the only faithful rollback
record. The rollback recipe lives in the header of `V52__drop_collection_type.sql`; it also requires
reverting the application to a pre-U4 build.

### collection_content (join table, ordered)
```sql
id              BIGSERIAL PRIMARY KEY
collection_id   BIGINT NOT NULL FK -> collection.id
content_id      BIGINT NOT NULL FK -> content.id
order_index     INTEGER NOT NULL DEFAULT 0
visible         BOOLEAN NOT NULL DEFAULT TRUE
created_at      TIMESTAMP NOT NULL
updated_at      TIMESTAMP NOT NULL
```

A single content row can belong to multiple collections with different ordering and per-collection
visibility.

## Metadata Tables

### tag / content_tags / collection_tags
```sql
-- tag
id                      BIGINT PRIMARY KEY
tag_name                VARCHAR(100) NOT NULL UNIQUE
slug                    VARCHAR(100) NOT NULL UNIQUE   -- V8
converted_collection_id BIGINT FK -> collection.id     -- V39, set when a tag was saved as a collection
created_at              TIMESTAMP

-- content_tags (join, content-keyed)
content_id  BIGINT FK -> content.id
tag_id      BIGINT FK -> tag.id

-- collection_tags (join)
collection_id  BIGINT FK -> collection.id
tag_id         BIGINT FK -> tag.id
```

### location / users
```sql
-- location joins: content_image_locations (content_id, location_id)   [V16; the key column was
--                 renamed image_id -> content_id in V27]
--                 collection_locations (collection_id, location_id)   [V16]
-- people joins:   content_image_people (content_id, person_id)        [renamed in V27]
--                 collection_people (collection_id, person_id)        [V22]
```

`person_id` references `users.id`. The old `content_people` table was merged into `app_user` and
dropped by V35; `app_user` was renamed to `users` in the same migration. A person with no account is
a `users` row with `status = 'PERSON'`.

`content_cameras`, `content_lenses` and `content_film_types` are **not** join tables -- images
reference them directly via the `camera_id` / `lens_id` / `film_type_id` FK columns above.

## Query Patterns

### Get collection with content (common)
```sql
SELECT ci.*, cc.order_index
FROM collection c
JOIN collection_content cc ON c.id = cc.collection_id
JOIN content_image ci ON cc.content_id = ci.id
WHERE c.slug = :slug AND cc.visible = true
ORDER BY cc.order_index
```

### Derived parent-ness (replaces the dropped PARENT type)
```sql
SELECT EXISTS (
  SELECT 1
  FROM collection_content cc
  JOIN content_collection cct ON cct.id = cc.content_id
  WHERE cc.collection_id = :collectionId
    AND cct.referenced_collection_id IS NOT NULL
)
```

### Bulk content loading (performance)
Use IN clause with content IDs, then associate in Java:
```sql
SELECT * FROM content_image WHERE id IN (?, ?, ?)
```

## Data Access Layer
- Uses `NamedParameterJdbcTemplate` (not JPA repositories)
- DAOs live in `dao/`, are named `*Repository`, and extend the abstract `BaseDao`
- Entities are POJOs with Lombok, mapped manually from ResultSet
