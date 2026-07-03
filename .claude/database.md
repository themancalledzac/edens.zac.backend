# Database Schema

Schema evolves via Flyway migrations in `src/main/resources/db/migration` (currently
through `V41`). The tables below are the core content model; the auth, user, and
user-space tables added in later migrations are summarised in
[Auth, Users & User-Space Tables](#auth-users--user-space-tables).

## Primary Tables

### content (base table)
```sql
id              BIGINT PRIMARY KEY
content_type    VARCHAR NOT NULL  -- IMAGE, TEXT, GIF, COLLECTION
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### content_image (extends content)
```sql
id              BIGINT PRIMARY KEY (FK -> content.id)
title           VARCHAR
image_url_web   VARCHAR NOT NULL
image_url_full  VARCHAR
image_width     INT
image_height    INT
iso             INT
f_stop          VARCHAR
shutter_speed   VARCHAR
focal_length    VARCHAR
rating          INT
author          VARCHAR
location        VARCHAR
create_date     VARCHAR
black_and_white BOOLEAN
is_film         BOOLEAN
```

### collection
```sql
id              BIGINT PRIMARY KEY
slug            VARCHAR UNIQUE NOT NULL
title           VARCHAR NOT NULL
collection_type VARCHAR  -- BLOG, PORTFOLIO, ART_GALLERY, CLIENT_GALLERY, HOME, PARENT, MISC
cover_image_url VARCHAR
collection_date DATE
is_visible      BOOLEAN
has_access      BOOLEAN
password        VARCHAR
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### collection_content (join table)
```sql
id              BIGINT PRIMARY KEY
collection_id   BIGINT FK -> collection.id
content_id      BIGINT FK -> content.id
display_order   INT
caption         VARCHAR
is_visible      BOOLEAN
```

## Metadata Tables

### tag / content_image_tags
```sql
-- tag
id    BIGINT PRIMARY KEY
name  VARCHAR UNIQUE

-- content_image_tags (join)
image_id  BIGINT FK -> content_image.id
tag_id    BIGINT FK -> tag.id
```

Similar pattern for: `person`, `camera`, `lens`, `film_type`

## Query Patterns

### Get collection with content (common)
```sql
SELECT c.*, cc.display_order, cc.caption
FROM collection c
JOIN collection_content cc ON c.id = cc.collection_id
JOIN content_image ci ON cc.content_id = ci.id
WHERE c.slug = ?
ORDER BY cc.display_order
```

### Bulk content loading (performance)
Use IN clause with content IDs, then associate in Java:
```sql
SELECT * FROM content_image WHERE id IN (?, ?, ?)
```

## Auth, Users & User-Space Tables

Added incrementally in migrations V17-V41. Consult the migration for exact columns.

| Table | Migration | Purpose |
|-------|-----------|---------|
| `messages` | V17 | Contact-form submissions |
| `collection_locations` / `content_image_locations` | V16 / V27 | Many-to-many location joins |
| `admin_home_tile` | V19 | Configurable home-page tiles |
| `collection_people` | V22 | Collection <-> person joins |
| `users` | V29 (as `app_user`), renamed in V35 | Application users (email, BCrypt hash). **NB: entity is `AppUserEntity`; table is `users`.** |
| `user_session` | V29 | Opaque DB-backed sessions (SHA-256 token hash, expiry) |
| `gallery_access` | V29 | Client-gallery access grants |
| `webauthn_credential` | V30 | Registered passkey credentials |
| `user_invite` | V32 | Invite tokens for account creation |
| `user_rating_override` | V34 | Per-user rating overrides |
| `user_selects` | V33 | Per-user content selects (client galleries) |
| `user_collection` | V36 | Per-user gallery membership + role |
| `user_saved_image` | V40 | Per-user saved images |
| `user_followed_collection` | V40 | Per-user followed collections |
| `collection_sibling` | V28 | Sibling links between collections |

Other notable migrations: `V20` three-state collection visibility, `V21` collection rating,
`V23` camera film metadata, `V26` image caption/alt, `V39` tag -> converted-collection link,
`V41` unique collection slug.

## Data Access Layer
- Uses `NamedParameterJdbcTemplate` (not JPA/Spring Data)
- DAO classes live in `dao/`, named `*Repository`, extending a shared `BaseDao`
- Entities are POJOs with Lombok, mapped manually from `ResultSet`
