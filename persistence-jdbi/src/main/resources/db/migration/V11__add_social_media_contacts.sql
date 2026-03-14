CREATE TABLE contact_socials (
    contact_id BIGINT NOT NULL,
    social_media VARCHAR(255) NOT NULL,
    FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE
);
