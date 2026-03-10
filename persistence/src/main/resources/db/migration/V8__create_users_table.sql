CREATE TABLE USERS (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Default admin user: admin / admin123
-- Hash created via BCrypt
INSERT INTO USERS (id, username, email, password_hash, role) 
VALUES (
    RANDOM_UUID(), 
    'admin', 
    'admin@outerstellar.de', 
    '$2a$12$8.Un7u.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.6Z.', -- dummy for now, will replace with real BCrypt later
    'ADMIN'
);
