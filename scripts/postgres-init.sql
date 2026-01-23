-- TODO: Not valid, fix later
-- PostgreSQL Schema Initialization Script
-- This script creates all tables, sequences, indexes, and constraints for the portfolio backend
-- Database: edens_zac

-- ============================================================================
-- SEQUENCES (for ID generation)
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS collection_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_id_seq;
CREATE SEQUENCE IF NOT EXISTS collection_content_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_tag_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_people_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_cameras_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_lenses_id_seq;
CREATE SEQUENCE IF NOT EXISTS content_film_types_id_seq;
CREATE SEQUENCE IF NOT EXISTS home_card_id_seq;

-- ============================================================================
-- BASE TABLES
-- ============================================================================

-- Collection table
CREATE TABLE IF NOT EXISTS collection (
    id BIGINT PRIMARY KEY DEFAULT nextval('collection_id_seq'),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    location VARCHAR(255),
    collection_date DATE,
    visible BOOLEAN NOT NULL DEFAULT true,
    display_mode VARCHAR(50),
    cover_image_id BIGINT,
    content_per_page INTEGER,
    total_content INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content base table (for JOINED inheritance)
CREATE TABLE IF NOT EXISTS content (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_id_seq'),
    content_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- CONTENT CHILD TABLES (JOINED inheritance)
-- ============================================================================

-- Content Image table
CREATE TABLE IF NOT EXISTS content_image (
    id BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    title VARCHAR(255),
    image_width INTEGER,
    image_height INTEGER,
    iso INTEGER,
    author VARCHAR(255),
    rating INTEGER,
    f_stop VARCHAR(50),
    lens_id BIGINT,
    black_and_white BOOLEAN,
    is_film BOOLEAN,
    film_type_id BIGINT,
    film_format VARCHAR(50),
    shutter_speed VARCHAR(50),
    camera_id BIGINT,
    focal_length VARCHAR(50),
    location TEXT,
    image_url_web TEXT NOT NULL,
    create_date VARCHAR(50),
    file_identifier TEXT UNIQUE
);

-- Content Text table
CREATE TABLE IF NOT EXISTS content_text (
    id BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    text_content TEXT NOT NULL,
    format_type VARCHAR(50)
);

-- Content GIF table
CREATE TABLE IF NOT EXISTS content_gif (
    id BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    title VARCHAR(255),
    gif_url TEXT NOT NULL,
    thumbnail_url TEXT,
    width INTEGER,
    height INTEGER,
    author VARCHAR(255),
    create_date VARCHAR(50)
);

-- Content Collection table (references other collections)
CREATE TABLE IF NOT EXISTS content_collection (
    id BIGINT PRIMARY KEY REFERENCES content(id) ON DELETE CASCADE,
    referenced_collection_id BIGINT NOT NULL
);

-- ============================================================================
-- JOIN TABLES
-- ============================================================================

-- Collection-Content join table
CREATE TABLE IF NOT EXISTS collection_content (
    id BIGINT PRIMARY KEY DEFAULT nextval('collection_content_id_seq'),
    collection_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    order_index INTEGER NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(collection_id, content_id)
);

-- ============================================================================
-- METADATA TABLES
-- ============================================================================

-- Content Tag table
CREATE TABLE IF NOT EXISTS content_tag (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_tag_id_seq'),
    tag_name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content People table
CREATE TABLE IF NOT EXISTS content_people (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_people_id_seq'),
    person_name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content Cameras table
CREATE TABLE IF NOT EXISTS content_cameras (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_cameras_id_seq'),
    camera_name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content Lenses table
CREATE TABLE IF NOT EXISTS content_lenses (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_lenses_id_seq'),
    lens_name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Content Film Types table
CREATE TABLE IF NOT EXISTS content_film_types (
    id BIGINT PRIMARY KEY DEFAULT nextval('content_film_types_id_seq'),
    film_type_name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    default_iso INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- MANY-TO-MANY JOIN TABLES
-- ============================================================================

-- Collection-Tags join table
CREATE TABLE IF NOT EXISTS collection_tags (
    collection_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (collection_id, tag_id)
);

-- Collection-People join table
CREATE TABLE IF NOT EXISTS collection_people (
    collection_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (collection_id, person_id)
);

-- Image-Tags join table
CREATE TABLE IF NOT EXISTS content_image_tags (
    image_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (image_id, tag_id)
);

-- Image-People join table
CREATE TABLE IF NOT EXISTS content_image_people (
    image_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (image_id, person_id)
);

-- GIF-Tags join table
CREATE TABLE IF NOT EXISTS content_gif_tags (
    gif_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (gif_id, tag_id)
);

-- ============================================================================
-- FOREIGN KEY CONSTRAINTS
-- ============================================================================

-- Collection foreign keys
ALTER TABLE collection
    ADD CONSTRAINT fk_collection_cover_image
    FOREIGN KEY (cover_image_id) REFERENCES content_image(id);

-- Content Image foreign keys
ALTER TABLE content_image
    ADD CONSTRAINT fk_content_image_lens
    FOREIGN KEY (lens_id) REFERENCES content_lenses(id);

ALTER TABLE content_image
    ADD CONSTRAINT fk_content_image_film_type
    FOREIGN KEY (film_type_id) REFERENCES content_film_types(id);

ALTER TABLE content_image
    ADD CONSTRAINT fk_content_image_camera
    FOREIGN KEY (camera_id) REFERENCES content_cameras(id);

-- Content Collection foreign key
ALTER TABLE content_collection
    ADD CONSTRAINT fk_content_collection_referenced
    FOREIGN KEY (referenced_collection_id) REFERENCES collection(id);

-- Collection-Content join table foreign keys
ALTER TABLE collection_content
    ADD CONSTRAINT fk_collection_content_collection
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE collection_content
    ADD CONSTRAINT fk_collection_content_content
    FOREIGN KEY (content_id) REFERENCES content(id) ON DELETE CASCADE;

-- Many-to-many join table foreign keys
ALTER TABLE collection_tags
    ADD CONSTRAINT fk_collection_tags_collection
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE collection_tags
    ADD CONSTRAINT fk_collection_tags_tag
    FOREIGN KEY (tag_id) REFERENCES content_tag(id) ON DELETE CASCADE;

ALTER TABLE collection_people
    ADD CONSTRAINT fk_collection_people_collection
    FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE collection_people
    ADD CONSTRAINT fk_collection_people_person
    FOREIGN KEY (person_id) REFERENCES content_people(id) ON DELETE CASCADE;

ALTER TABLE content_image_tags
    ADD CONSTRAINT fk_image_tags_image
    FOREIGN KEY (image_id) REFERENCES content_image(id) ON DELETE CASCADE;

ALTER TABLE content_image_tags
    ADD CONSTRAINT fk_image_tags_tag
    FOREIGN KEY (tag_id) REFERENCES content_tag(id) ON DELETE CASCADE;

ALTER TABLE content_image_people
    ADD CONSTRAINT fk_image_people_image
    FOREIGN KEY (image_id) REFERENCES content_image(id) ON DELETE CASCADE;

ALTER TABLE content_image_people
    ADD CONSTRAINT fk_image_people_person
    FOREIGN KEY (person_id) REFERENCES content_people(id) ON DELETE CASCADE;

ALTER TABLE content_gif_tags
    ADD CONSTRAINT fk_gif_tags_gif
    FOREIGN KEY (gif_id) REFERENCES content_gif(id) ON DELETE CASCADE;

ALTER TABLE content_gif_tags
    ADD CONSTRAINT fk_gif_tags_tag
    FOREIGN KEY (tag_id) REFERENCES content_tag(id) ON DELETE CASCADE;

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Collection indexes
CREATE INDEX IF NOT EXISTS idx_collection_slug ON collection(slug);
CREATE INDEX IF NOT EXISTS idx_collection_type ON collection(type);
CREATE INDEX IF NOT EXISTS idx_collection_created_at ON collection(created_at);

-- Content Collection index
CREATE INDEX IF NOT EXISTS idx_content_collection_col ON content_collection(referenced_collection_id);

-- Collection-Content join table indexes
CREATE INDEX IF NOT EXISTS idx_collection_content_collection ON collection_content(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_content_content ON collection_content(content_id);
CREATE INDEX IF NOT EXISTS idx_collection_content_order ON collection_content(collection_id, order_index);

-- Metadata table indexes
CREATE INDEX IF NOT EXISTS idx_content_tag_name ON content_tag(tag_name);
CREATE INDEX IF NOT EXISTS idx_content_person_name ON content_people(person_name);
CREATE INDEX IF NOT EXISTS idx_content_camera_name ON content_cameras(camera_name);
CREATE INDEX IF NOT EXISTS idx_content_lens_name ON content_lenses(lens_name);
CREATE INDEX IF NOT EXISTS idx_content_film_type_name ON content_film_types(film_type_name);

-- Many-to-many join table indexes
CREATE INDEX IF NOT EXISTS idx_collection_tags_collection ON collection_tags(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_tags_tag ON collection_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_collection_people_collection ON collection_people(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_people_person ON collection_people(person_id);
CREATE INDEX IF NOT EXISTS idx_image_tags_image ON content_image_tags(image_id);
CREATE INDEX IF NOT EXISTS idx_image_tags_tag ON content_image_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_image_people_image ON content_image_people(image_id);
CREATE INDEX IF NOT EXISTS idx_image_people_person ON content_image_people(person_id);
CREATE INDEX IF NOT EXISTS idx_gif_tags_gif ON content_gif_tags(gif_id);
CREATE INDEX IF NOT EXISTS idx_gif_tags_tag ON content_gif_tags(tag_id);

-- ============================================================================
-- COMMENTS (for documentation)
-- ============================================================================

COMMENT ON TABLE collection IS 'Main container for different types of content (blog, gallery, etc.)';
COMMENT ON TABLE content IS 'Base table for all content types using JOINED inheritance';
COMMENT ON TABLE content_image IS 'Image content extending content table';
COMMENT ON TABLE content_text IS 'Text content extending content table';
COMMENT ON TABLE content_gif IS 'GIF content extending content table';
COMMENT ON TABLE content_collection IS 'Content type that references another collection';
COMMENT ON TABLE collection_content IS 'Join table linking collections to content with ordering and visibility';
COMMENT ON TABLE content_tag IS 'Reusable tags for collections and content';
COMMENT ON TABLE content_people IS 'People who can be tagged in images';
COMMENT ON TABLE content_cameras IS 'Camera models used in images';
COMMENT ON TABLE content_lenses IS 'Lens models used in images';
COMMENT ON TABLE content_film_types IS 'Film stock types for film photography';
