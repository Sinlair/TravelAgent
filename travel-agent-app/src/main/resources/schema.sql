CREATE TABLE IF NOT EXISTS conversation_session (
    conversation_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    agent_type TEXT,
    summary TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_message (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    agent_type TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation_time
    ON conversation_message (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS task_memory (
    conversation_id TEXT PRIMARY KEY,
    origin TEXT,
    destination TEXT,
    days INTEGER,
    budget TEXT,
    preferences_json TEXT,
    pending_question TEXT,
    summary TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS travel_plan_snapshot (
    conversation_id TEXT PRIMARY KEY,
    plan_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_timeline (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    stage TEXT NOT NULL,
    message TEXT NOT NULL,
    details_json TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_timeline_conversation_time
    ON conversation_timeline (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS long_term_memory (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    category TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata_json TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_long_term_memory_conversation_time
    ON long_term_memory (conversation_id, created_at);

