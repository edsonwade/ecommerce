-- V8 — Seed image_url on existing products, grouped by category name.
-- Images are royalty-free photos from Unsplash (resized to 400px width).

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Keyboards');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1527814050087-3793815479db?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Mice');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Monitors');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Audio');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Video & Streaming');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Connectivity');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Storage');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1593642632559-0c6d3fc62b89?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Desk Setup');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1542567455-cd733f23fbb1?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Gaming Gear');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1517430816045-df4b7de11d1d?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Office Equipment');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Networking');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'PC Components');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Power');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Smart Tech');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Portability');

UPDATE product SET image_url = 'https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Printing');

-- Accessories (fallback for any remaining categories in older seed data)
UPDATE product SET image_url = 'https://images.unsplash.com/photo-1625723044792-44de16ccb4e9?w=400&auto=format&fit=crop'
WHERE category_id = (SELECT id FROM category WHERE name = 'Accessories') AND image_url IS NULL;

-- Default fallback for products that still have no image
UPDATE product SET image_url = 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop'
WHERE image_url IS NULL;
