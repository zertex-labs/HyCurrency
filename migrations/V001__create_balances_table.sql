-- V001: Create balances table for storing player currency balances
-- This table uses composite primary key (player_id, currency_type)
-- to support multiple currency types per player(and have sane fetch times)

CREATE TABLE IF NOT EXISTS balances (
    player_id UUID NOT NULL,
    currency_type VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, currency_type)
);

-- Index for efficiently querying all balances for a player
CREATE INDEX IF NOT EXISTS idx_balances_player_id ON balances(player_id);

-- Index for querying all player balances for a coin(will be useful for analytics, but completely useless now; just future proofing)
CREATE INDEX IF NOT EXISTS idx_balances_currency_type ON balances(currency_type);