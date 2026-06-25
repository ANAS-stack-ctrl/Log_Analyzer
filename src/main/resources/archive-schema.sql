CREATE TABLE IF NOT EXISTS log_imports (
    id BIGINT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255),
    file_hash VARCHAR(128),
    file_size BIGINT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    total_lines INTEGER NOT NULL DEFAULT 0,
    parsed_lines INTEGER NOT NULL DEFAULT 0,
    failed_lines INTEGER NOT NULL DEFAULT 0,
    total_errors INTEGER NOT NULL DEFAULT 0,
    total_infos INTEGER NOT NULL DEFAULT 0,
    total_warnings INTEGER NOT NULL DEFAULT 0,
    source_server VARCHAR(100),
    source_environment VARCHAR(100),
    source_app_version VARCHAR(255),
    summary TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP,
    archived_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS log_entries (
    id BIGINT PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES log_imports(id) ON DELETE CASCADE,
    source_file_name VARCHAR(500),
    source_relative_path VARCHAR(1000),
    log_timestamp TIMESTAMP,
    session_id VARCHAR(100),
    level VARCHAR(20),
    user_name VARCHAR(255),
    source_class VARCHAR(500),
    process_name VARCHAR(500),
    step_code VARCHAR(100),
    log_code INTEGER,
    environment VARCHAR(100),
    server_name VARCHAR(100),
    app_version VARCHAR(255),
    user_correlation_id VARCHAR(100),
    message TEXT,
    raw_log TEXT,
    event_type VARCHAR(100),
    field_name VARCHAR(255),
    interface_field VARCHAR(255),
    field_class_code VARCHAR(255),
    parsed_type VARCHAR(100),
    parsed_value TEXT,
    relation_name VARCHAR(255),
    relation_key VARCHAR(255),
    business_key VARCHAR(255),
    mandatory_field VARCHAR(255),
    error_column VARCHAR(255),
    error_attribute VARCHAR(255),
    error_value TEXT,
    error_business_key VARCHAR(255),
    is_error BOOLEAN NOT NULL DEFAULT FALSE,
    duration_ms BIGINT,
    business_meaning TEXT,
    parse_quality VARCHAR(50),
    ambiguous_message BOOLEAN NOT NULL DEFAULT FALSE,
    incomplete_line BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_archive_log_entries_import_id ON log_entries(import_id);
CREATE INDEX IF NOT EXISTS idx_archive_log_entries_log_timestamp ON log_entries(log_timestamp);
CREATE INDEX IF NOT EXISTS idx_archive_log_imports_archived_at ON log_imports(archived_at);

CREATE INDEX IF NOT EXISTS idx_archive_log_entries_duration_ms ON log_entries(duration_ms);

CREATE TABLE IF NOT EXISTS log_raw_messages (
    id BIGINT PRIMARY KEY,
    log_entry_id BIGINT NOT NULL REFERENCES log_entries(id) ON DELETE CASCADE,
    raw_message TEXT,
    message_tail TEXT,
    technical_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_archive_log_raw_messages_log_entry_id ON log_raw_messages(log_entry_id);
