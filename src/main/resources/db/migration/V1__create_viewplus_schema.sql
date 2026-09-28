CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    email VARCHAR(254) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS live_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ended_at DATETIME(6) NULL,
    started_at DATETIME(6) NOT NULL,
    status VARCHAR(10) NOT NULL,
    title VARCHAR(100) NOT NULL,
    visibility VARCHAR(10) NOT NULL,
    host_user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_live_rooms_host FOREIGN KEY (host_user_id) REFERENCES users(id),
    INDEX idx_live_rooms_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content VARCHAR(800) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    room_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (room_id) REFERENCES live_rooms(id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_user_id) REFERENCES users(id),
    INDEX idx_chat_messages_room_created (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
