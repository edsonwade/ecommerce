-- V7 — Add image_url column to product table
ALTER TABLE product ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
