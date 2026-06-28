-- =============================================================================
-- V10 — Real, granular hardware-store categories.
--
-- Removes abstract umbrella categories ("Audio", "PC Components") and the
-- duplicate "Screens", replacing them with concrete categories a real
-- electronics store uses. Existing products are re-pointed (no data loss),
-- then a few extra products are seeded so every new category is populated.
--
-- Forward-only and additive: no product rows are deleted, only re-categorised.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) New granular categories
-- -----------------------------------------------------------------------------
INSERT INTO category (id, description, name, tenant_id) VALUES
    (nextval('category_seq'), 'Desktop and gaming motherboards',              'Motherboards',   'default-tenant'),
    (nextval('category_seq'), 'Desktop and laptop processors (CPUs)',         'Processors',     'default-tenant'),
    (nextval('category_seq'), 'Gaming and workstation graphics cards (GPUs)', 'Graphics Cards', 'default-tenant'),
    (nextval('category_seq'), 'DDR4 and DDR5 memory (RAM) kits',              'Memory',         'default-tenant'),
    (nextval('category_seq'), 'ATX and SFX power supply units (PSUs)',        'Power Supplies', 'default-tenant'),
    (nextval('category_seq'), 'Air and liquid CPU coolers',                   'CPU Coolers',    'default-tenant'),
    (nextval('category_seq'), 'NVMe SSDs, SATA drives, and storage solutions', 'Storage',       'default-tenant'),
    (nextval('category_seq'), 'Over-ear and in-ear headphones',              'Headphones',     'default-tenant'),
    (nextval('category_seq'), 'Gaming and office headsets with microphone',   'Headsets',       'default-tenant'),
    (nextval('category_seq'), 'Desktop and bookshelf speakers',               'Speakers',       'default-tenant'),
    (nextval('category_seq'), 'USB and XLR microphones',                      'Microphones',    'default-tenant')
ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2) Merge the duplicate "Screens" into "Monitors", then drop it
-- -----------------------------------------------------------------------------
UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Monitors')
WHERE category_id = (SELECT id FROM category WHERE name = 'Screens');

DELETE FROM category WHERE name = 'Screens';

-- -----------------------------------------------------------------------------
-- 3) Re-point existing "PC Components" products into the granular categories
-- -----------------------------------------------------------------------------
UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Memory'),
       image_url = 'https://images.unsplash.com/photo-1562976540-1502c2145186?w=400&auto=format&fit=crop'
WHERE name = 'PulseRAM DDR5-6000 32GB Kit';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Storage'),
       image_url = 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=400&auto=format&fit=crop'
WHERE name = 'BlazeDisk 2TB NVMe Gen4 SSD';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Graphics Cards'),
       image_url = 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop'
WHERE name = 'PhantomCore RTX 16GB GPU';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Processors'),
       image_url = 'https://images.unsplash.com/photo-1555617981-dac3880eac6e?w=400&auto=format&fit=crop'
WHERE name = 'Apex 8-Core Desktop CPU';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Motherboards'),
       image_url = 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop'
WHERE name = 'ForgeBoard ATX DDR5 Motherboard';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Power Supplies'),
       image_url = 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop'
WHERE name = 'VoltCore 850W Gold PSU';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'CPU Coolers'),
       image_url = 'https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=400&auto=format&fit=crop'
WHERE name IN ('IceLoop 240 AIO Liquid Cooler', 'TwinPeak Air CPU Cooler');

-- -----------------------------------------------------------------------------
-- 4) Re-point existing "Audio" products into the granular categories
-- -----------------------------------------------------------------------------
UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Headsets'),
       image_url = 'https://images.unsplash.com/photo-1599669454699-248893623440?w=400&auto=format&fit=crop'
WHERE name = 'Obsidian Gaming Headset 7.1';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Headphones'),
       image_url = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop'
WHERE name = 'AirPulse BT Headphones';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Microphones'),
       image_url = 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400&auto=format&fit=crop'
WHERE name = 'StudioOne USB Microphone';

UPDATE product SET category_id = (SELECT id FROM category WHERE name = 'Speakers'),
       image_url = 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=400&auto=format&fit=crop'
WHERE name = 'BassLine 2.1 Speakers';

-- -----------------------------------------------------------------------------
-- 5) Drop the now-empty umbrella categories
-- -----------------------------------------------------------------------------
DELETE FROM category WHERE name IN ('Audio', 'PC Components');

-- -----------------------------------------------------------------------------
-- 6) Seed extra products so every new category looks like a real store shelf
-- -----------------------------------------------------------------------------
-- Motherboards
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 22, 'Mid-range B-series ATX motherboard, DDR5, PCIe 4.0',          'ForgeBoard B650 ATX Motherboard',     179.99, (SELECT id FROM category WHERE name = 'Motherboards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 16, 'High-end X-series ATX motherboard, DDR5, Wi-Fi 6E, 4x M.2',   'ForgeBoard X670E Pro Motherboard',    329.99, (SELECT id FROM category WHERE name = 'Motherboards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 28, 'Compact mini-ITX motherboard for small-form-factor builds',   'ForgeBoard B650 Mini-ITX Board',      199.99, (SELECT id FROM category WHERE name = 'Motherboards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=400&auto=format&fit=crop');

-- Processors
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 24, '6-core 12-thread desktop CPU, great value for gaming',        'Apex 6-Core Desktop CPU',             229.99, (SELECT id FROM category WHERE name = 'Processors'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1555617981-dac3880eac6e?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 12, '12-core 24-thread enthusiast desktop CPU, unlocked',          'Apex 12-Core Desktop CPU',            549.99, (SELECT id FROM category WHERE name = 'Processors'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1555617981-dac3880eac6e?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 18, '16-core 32-thread workstation CPU for content creation',      'Apex 16-Core Workstation CPU',        799.99, (SELECT id FROM category WHERE name = 'Processors'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1555617981-dac3880eac6e?w=400&auto=format&fit=crop');

-- Graphics Cards
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 14, '8GB GDDR6 1440p gaming graphics card',                        'PhantomCore RTX 8GB GPU',             449.99, (SELECT id FROM category WHERE name = 'Graphics Cards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 8,  '24GB GDDR6X flagship 4K ray-tracing graphics card',           'PhantomCore RTX 24GB Flagship GPU',   1599.99,(SELECT id FROM category WHERE name = 'Graphics Cards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 20, '12GB GDDR6 1080p/1440p sweet-spot graphics card',             'PhantomCore RTX 12GB GPU',            599.99, (SELECT id FROM category WHERE name = 'Graphics Cards'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400&auto=format&fit=crop');

-- Memory
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 40, '16GB (2x8) DDR4-3200 CL16 desktop RAM kit',                   'PulseRAM DDR4-3200 16GB Kit',         44.99,  (SELECT id FROM category WHERE name = 'Memory'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1562976540-1502c2145186?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 30, '64GB (2x32) DDR5-6000 CL30 desktop RAM kit',                  'PulseRAM DDR5-6000 64GB Kit',         279.99, (SELECT id FROM category WHERE name = 'Memory'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1562976540-1502c2145186?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 50, '32GB (2x16) DDR4-3600 CL18 RGB RAM kit',                      'PulseRAM DDR4-3600 32GB RGB Kit',     99.99,  (SELECT id FROM category WHERE name = 'Memory'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1562976540-1502c2145186?w=400&auto=format&fit=crop');

-- Power Supplies
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 30, '650W 80 Plus Bronze semi-modular PSU',                        'VoltCore 650W Bronze PSU',            69.99,  (SELECT id FROM category WHERE name = 'Power Supplies'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 20, '1000W 80 Plus Gold fully-modular PSU',                        'VoltCore 1000W Gold PSU',             169.99, (SELECT id FROM category WHERE name = 'Power Supplies'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 14, '1200W 80 Plus Platinum fully-modular ATX 3.0 PSU',            'VoltCore 1200W Platinum PSU',         239.99, (SELECT id FROM category WHERE name = 'Power Supplies'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=400&auto=format&fit=crop');

-- CPU Coolers
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 30, '360mm AIO liquid CPU cooler with ARGB pump and fans',         'IceLoop 360 AIO Liquid Cooler',       139.99, (SELECT id FROM category WHERE name = 'CPU Coolers'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 45, 'Single-tower air CPU cooler, 120mm quiet fan',                'TwinPeak Single Air CPU Cooler',      44.99,  (SELECT id FROM category WHERE name = 'CPU Coolers'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=400&auto=format&fit=crop');

-- Headphones
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 40, 'Active noise-cancelling over-ear wireless headphones',        'AirPulse ANC Wireless Headphones',    199.99, (SELECT id FROM category WHERE name = 'Headphones'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 60, 'Wired studio over-ear headphones, 50mm drivers',              'StudioOne Wired Headphones',          89.99,  (SELECT id FROM category WHERE name = 'Headphones'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 70, 'True wireless in-ear earbuds with charging case',             'AirPulse TWS Earbuds',                79.99,  (SELECT id FROM category WHERE name = 'Headphones'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&auto=format&fit=crop');

-- Headsets
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 35, 'Wireless 2.4GHz gaming headset, 50h battery',                 'Obsidian Wireless Gaming Headset',    129.99, (SELECT id FROM category WHERE name = 'Headsets'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1599669454699-248893623440?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 50, 'USB stereo office headset with noise-cancelling mic',         'MeetCast Office Headset',             59.99,  (SELECT id FROM category WHERE name = 'Headsets'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1599669454699-248893623440?w=400&auto=format&fit=crop');

-- Speakers
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 40, '2.0 bookshelf speaker pair with Bluetooth 5.0',               'BassLine 2.0 Bookshelf Speakers',     99.99,  (SELECT id FROM category WHERE name = 'Speakers'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 30, '2.1 desktop speaker set with wired subwoofer',                'BassLine 2.1 Desktop Speakers',       74.99,  (SELECT id FROM category WHERE name = 'Speakers'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=400&auto=format&fit=crop');

-- Microphones
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by, image_url) VALUES
    (nextval('product_seq'), 30, 'USB cardioid condenser microphone with desk stand',           'StudioOne USB Condenser Mic',         99.99,  (SELECT id FROM category WHERE name = 'Microphones'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400&auto=format&fit=crop'),
    (nextval('product_seq'), 18, 'XLR dynamic broadcast microphone for studio use',             'StudioCast XLR Dynamic Mic',          179.99, (SELECT id FROM category WHERE name = 'Microphones'), 'default-tenant', 'system', 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?w=400&auto=format&fit=crop');
