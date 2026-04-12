-- Face Gallery Database Schema

CREATE TABLE IF NOT EXISTS facegallery_sessions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    crop_padding INTEGER NOT NULL DEFAULT 45,
    tolerance DOUBLE PRECISION NOT NULL DEFAULT 0.6
);

CREATE TABLE IF NOT EXISTS facegallery_face_groups (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    primary_face_id UUID NOT NULL,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS facegallery_faces (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    name VARCHAR(255),
    primary_photo_id UUID,
    group_id UUID,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    encoding TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS facegallery_photos (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    thumbnail_path VARCHAR(1024),
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS facegallery_photo_face_assignments (
    id UUID PRIMARY KEY,
    photo_id UUID NOT NULL,
    face_id UUID NOT NULL,
    bbox_x INTEGER,
    bbox_y INTEGER,
    bbox_width INTEGER,
    bbox_height INTEGER
);

CREATE INDEX IF NOT EXISTS idx_facegallery_faces_session_id ON facegallery_faces(session_id);
CREATE INDEX IF NOT EXISTS idx_facegallery_faces_group_id ON facegallery_faces(group_id);
CREATE INDEX IF NOT EXISTS idx_facegallery_photos_session_id ON facegallery_photos(session_id);
CREATE INDEX IF NOT EXISTS idx_facegallery_photo_face_assignments_photo_id ON facegallery_photo_face_assignments(photo_id);
CREATE INDEX IF NOT EXISTS idx_facegallery_photo_face_assignments_face_id ON facegallery_photo_face_assignments(face_id);