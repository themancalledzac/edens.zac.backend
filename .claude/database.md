# Database Schema

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
collection_type VARCHAR  -- PROJECT, PHOTOGRAPHY, BLOG
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

## Data Access Layer
- Uses `NamedParameterJdbcTemplate` (not JPA repositories)
- DAOs extend `BaseDao` pattern
- Entities are POJOs with Lombok, mapped manually from ResultSet
