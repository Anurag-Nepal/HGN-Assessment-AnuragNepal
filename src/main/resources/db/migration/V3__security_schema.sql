CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL
);

-- Seed user and admin. Both have the password 'password' (bcrypt hashed)
INSERT INTO app_user (username, password, role)
VALUES ('admin', '$2a$10$H6SPzMMj0HWH/tKk3Ls6yO2wmGlr9pXJtXcS3ATnJ/5OepqsHaIVm', 'OPERATOR');

INSERT INTO app_user (username, password, role)
VALUES ('user', '$2a$10$H6SPzMMj0HWH/tKk3Ls6yO2wmGlr9pXJtXcS3ATnJ/5OepqsHaIVm', 'USER');