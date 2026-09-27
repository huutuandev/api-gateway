CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(50) NOT NULL DEFAULT 'USER'
                           CHECK (role IN ('USER', 'ADMIN')),
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversations (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               title VARCHAR(255),
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_conversations_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE
);

CREATE TABLE messages (
                          id BIGSERIAL PRIMARY KEY,
                          conversation_id BIGINT NOT NULL,
                          role VARCHAR(20) NOT NULL
                              CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT')),
                          content TEXT NOT NULL,
                          token_count INTEGER,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_messages_conversation
                              FOREIGN KEY (conversation_id)
                                  REFERENCES conversations(id)
                                  ON DELETE CASCADE
);

CREATE TABLE ai_requests (
                             id BIGSERIAL PRIMARY KEY,
                             request_id UUID NOT NULL UNIQUE,

                             user_id BIGINT NOT NULL,
                             conversation_id BIGINT,

                             model VARCHAR(100) NOT NULL,

                             prompt_tokens INTEGER,
                             completion_tokens INTEGER,
                             total_tokens INTEGER,

                             latency_ms BIGINT,

                             status VARCHAR(30) NOT NULL
                                 CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT')),

                             error_message TEXT,

                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_ai_requests_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_ai_requests_conversation
                                 FOREIGN KEY (conversation_id)
                                     REFERENCES conversations(id)
                                     ON DELETE SET NULL
);

CREATE INDEX idx_conversations_user_id
    ON conversations(user_id);

CREATE INDEX idx_messages_conversation_id
    ON messages(conversation_id);

CREATE INDEX idx_ai_requests_user_id
    ON ai_requests(user_id);

CREATE INDEX idx_ai_requests_created_at
    ON ai_requests(created_at);