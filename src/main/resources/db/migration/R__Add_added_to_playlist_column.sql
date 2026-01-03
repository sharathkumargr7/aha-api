-- Add the added_to_playlist column to the aha_music table
ALTER TABLE aha_music ADD COLUMN IF NOT EXISTS added_to_playlist BOOLEAN NOT NULL DEFAULT false;