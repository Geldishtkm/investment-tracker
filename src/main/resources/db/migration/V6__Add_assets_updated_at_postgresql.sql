-- V6: V2 added an updated_at trigger to assets, but assets never had that column, so every
-- UPDATE of an asset failed ("record new has no field updated_at"). Add the column it expects.

ALTER TABLE assets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
