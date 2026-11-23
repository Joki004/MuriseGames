-- V16__add_live_window_start.sql
-- Allow Host Control Deck to activate a scheduled time window with multiple concurrent games.

ALTER TABLE murise.event_state
    ADD COLUMN current_window_start_at timestamptz;