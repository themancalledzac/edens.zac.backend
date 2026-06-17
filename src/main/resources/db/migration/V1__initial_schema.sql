-- V1: Initial portfolio backend schema.
-- This migration captures the baseline tables that existed before Flyway was introduced.
-- All subsequent migrations (V2+) alter or extend these tables.

-- Reference lookup tables
-- Note: location.slug added in V8; V2 creates location with IF NOT EXISTS (pre-Flyway it existed already).
CREATE TABLE location (
    id            BIGSERIAL PRIMARY KEY,
    location_name VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_location_name ON location(location_name);
-- Note: slug columns added to tag, content_people, location in V8.
CREATE TABLE tag (
    id         BIGSERIAL PRIMARY KEY,
    tag_name   VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE content_people (
    id          BIGSERIAL PRIMARY KEY,
    person_name VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE content_cameras (
    id          BIGSERIAL PRIMARY KEY,
    camera_name VARCHAR(200) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE content_lenses (
    id         BIGSERIAL PRIMARY KEY,
    lens_name  VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE content_film_types (
    id             BIGSERIAL PRIMARY KEY,
    film_type_name VARCHAR(100) NOT NULL UNIQUE,
    display_name   VARCHAR(200),
    default_iso    INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Core collection table
CREATE TABLE collection (
    id               BIGSERIAL PRIMARY KEY,
    type             VARCHAR(50)  NOT NULL,
    title            VARCHAR(100) NOT NULL,
    slug             VARCHAR(150) NOT NULL UNIQUE,
    description      VARCHAR(500),
    collection_date  DATE,
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    display_mode     VARCHAR(50),
    cover_image_id   BIGINT,
    content_per_page INTEGER,
    total_content    INTEGER,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Base content table (polymorphic parent)
CREATE TABLE content (
    id           BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Image content (child of content)
-- Note: caption and alt added in V26, rating on gif added in V24.
CREATE TABLE content_image (
    id               BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    title            VARCHAR(255),
    image_width      INTEGER,
    image_height     INTEGER,
    iso              INTEGER,
    author           VARCHAR(100),
    rating           INTEGER,
    f_stop           VARCHAR(20),
    lens_id          BIGINT REFERENCES content_lenses(id),
    black_and_white  BOOLEAN,
    is_film          BOOLEAN,
    film_type_id     BIGINT REFERENCES content_film_types(id),
    film_format      VARCHAR(20),
    shutter_speed    VARCHAR(50),
    camera_id        BIGINT REFERENCES content_cameras(id),
    focal_length     VARCHAR(50),
    image_url_web      VARCHAR(1024) NOT NULL,
    image_url_original VARCHAR(1024),
    -- image_url_raw added in V10
    -- capture_date (DATE), last_export_date, original_filename added in V4; create_date, file_identifier dropped in V4
    file_identifier    VARCHAR(512),
    create_date        VARCHAR(50)
);

-- Text content (child of content)
CREATE TABLE content_text (
    id           BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    text_content TEXT NOT NULL,
    format_type  VARCHAR(50)
);

-- GIF content (child of content)
CREATE TABLE content_gif (
    id            BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    title         VARCHAR(255),
    gif_url       VARCHAR(1024),
    -- gif_url_web added in V25
    thumbnail_url VARCHAR(1024),
    width         INTEGER,
    height        INTEGER,
    author        VARCHAR(100),
    create_date   VARCHAR(50)
    -- rating added in V24
);

-- Collection-reference content (child of content)
CREATE TABLE content_collection (
    id                     BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    referenced_collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE
);

-- Collection content join (ordered)
CREATE TABLE collection_content (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    content_id    BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    order_index   INTEGER NOT NULL DEFAULT 0,
    visible       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tag join tables
CREATE TABLE content_tags (
    content_id BIGINT NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    tag_id     BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (content_id, tag_id)
);

CREATE TABLE collection_tags (
    collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    tag_id        BIGINT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, tag_id)
);

-- People join tables
-- Note: image_id column is renamed to content_id in V27.
CREATE TABLE content_image_people (
    image_id  BIGINT NOT NULL REFERENCES content_image(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES content_people(id) ON DELETE CASCADE,
    PRIMARY KEY (image_id, person_id)
);

-- collection_people: V9 creates indexes on this table, V22 adds it with IF NOT EXISTS.
-- Create here so V9 index creation succeeds.
CREATE TABLE collection_people (
    collection_id BIGINT NOT NULL REFERENCES collection(id) ON DELETE CASCADE,
    person_id     BIGINT NOT NULL REFERENCES content_people(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, person_id)
);

-- Basic indexes
CREATE INDEX idx_collection_type   ON collection(type);
CREATE INDEX idx_collection_date   ON collection(collection_date DESC NULLS LAST);
CREATE INDEX idx_content_type      ON content(content_type);
CREATE INDEX idx_content_image_url ON content_image(image_url_web);
