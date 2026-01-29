-- V002: Create processed_transactions table for idempotency
-- Records are cleaned up periodically based on TTL

CREATE TABLE IF NOT EXISTS processed_transactions (
    transaction_id VARCHAR(100) PRIMARY KEY,
    result_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for efficient cleanup of expired records(TTL)
CREATE INDEX IF NOT EXISTS idx_processed_transactions_created_at 
    ON processed_transactions(created_at);
