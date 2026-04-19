-- =============================================================================
-- V6 — Seed the public electronics/hardware catalog so a user that just
-- registered + logged in already sees a populated product list.
--
-- All rows belong to 'default-tenant' (matches V4 backfill) and are stamped
-- with created_by='system' (matches V5 NOT NULL constraint).
--
-- Organization follows the 11 top-level groups: Peripherals, Accessories,
-- Gaming, Office, Networking, Hardware (PC Components), Power, Smart Tech,
-- Portability, Monitors/Displays, Printing.
-- =============================================================================

-- =========================================================================
-- 1) Categories — insert the ones missing from V2 (Keyboards / Monitors /
--    Screens / Mice / Accessories already exist). ON CONFLICT keeps V6
--    re-runnable if a category was added manually.
-- =========================================================================
INSERT INTO category (id, description, name, tenant_id) VALUES
    (nextval('category_seq'), 'Headsets, microphones and speakers',            'Audio',             'default-tenant'),
    (nextval('category_seq'), 'Webcams, ring lights and streaming gear',       'Video & Streaming', 'default-tenant'),
    (nextval('category_seq'), 'Hubs, adapters, docks and cables',              'Connectivity',      'default-tenant'),
    (nextval('category_seq'), 'SSDs, HDDs, pen drives and memory cards',       'Storage',           'default-tenant'),
    (nextval('category_seq'), 'Mousepads, stands and cable organizers',        'Desk Setup',        'default-tenant'),
    (nextval('category_seq'), 'Gaming chairs, controllers, RGB and extras',    'Gaming Gear',       'default-tenant'),
    (nextval('category_seq'), 'Office combos, printers, scanners and mics',    'Office Equipment',  'default-tenant'),
    (nextval('category_seq'), 'Routers, extenders and switches',               'Networking',        'default-tenant'),
    (nextval('category_seq'), 'RAM, GPU, CPU, motherboards, PSU and coolers',  'PC Components',     'default-tenant'),
    (nextval('category_seq'), 'Chargers, powerbanks and UPS',                  'Power',             'default-tenant'),
    (nextval('category_seq'), 'Smartwatches, smart lamps and smart-home gear', 'Smart Tech',        'default-tenant'),
    (nextval('category_seq'), 'Backpacks, bags and foldable stands',           'Portability',       'default-tenant'),
    (nextval('category_seq'), 'Ink, toner and specialty paper',                'Printing',          'default-tenant')
ON CONFLICT (name) DO NOTHING;

-- =========================================================================
-- 2) Peripherals — Keyboards
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 40, 'Full-size mechanical keyboard, blue switches, aluminium frame',   'Obsidian MX Pro Mechanical Keyboard', 139.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, 'Per-key RGB mechanical gaming keyboard with 1ms polling',         'Neon Strike RGB Gaming Keyboard',     169.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, '2.4GHz and Bluetooth wireless low-profile keyboard',              'AirType Wireless Keyboard',           119.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system'),
    (nextval('product_seq'), 25, 'Split ergonomic keyboard with tented design and wrist rest',      'ErgoSplit Ergonomic Keyboard',        189.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system'),
    (nextval('product_seq'), 45, '75 percent compact hot-swappable keyboard, PBT keycaps',          'Pebble 75 Compact Keyboard',          129.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, '60 percent ultra-compact keyboard for minimal desk setups',       'Pebble 60 Mini Keyboard',             109.99, (SELECT id FROM category WHERE name = 'Keyboards'), 'default-tenant', 'system');

-- =========================================================================
-- 3) Peripherals — Mice
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 60, '26K DPI optical gaming mouse with 8 programmable buttons',        'Raven X Gaming Mouse',            79.99, (SELECT id FROM category WHERE name = 'Mice'), 'default-tenant', 'system'),
    (nextval('product_seq'), 55, 'Silent-click wireless mouse with 90-hour battery life',           'WhisperGlide Wireless Mouse',     49.99, (SELECT id FROM category WHERE name = 'Mice'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, 'Vertical ergonomic mouse to reduce wrist strain',                 'ErgoVert Ergonomic Mouse',        59.99, (SELECT id FROM category WHERE name = 'Mice'), 'default-tenant', 'system'),
    (nextval('product_seq'), 45, '58g ultra-light honeycomb-shell esports mouse',                   'FeatherLite Ultra-Light Mouse',   69.99, (SELECT id FROM category WHERE name = 'Mice'), 'default-tenant', 'system');

-- =========================================================================
-- 4) Peripherals — Audio
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 30, '7.1 surround gaming headset with detachable mic',                 'Obsidian Gaming Headset 7.1',     99.99,  (SELECT id FROM category WHERE name = 'Audio'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, 'Bluetooth 5.3 over-ear headphones with 40h battery',              'AirPulse BT Headphones',          129.99, (SELECT id FROM category WHERE name = 'Audio'), 'default-tenant', 'system'),
    (nextval('product_seq'), 25, 'USB cardioid condenser microphone for podcasting',                'StudioOne USB Microphone',        89.99,  (SELECT id FROM category WHERE name = 'Audio'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, '2.1 desktop speaker set with subwoofer',                          'BassLine 2.1 Speakers',           74.99,  (SELECT id FROM category WHERE name = 'Audio'), 'default-tenant', 'system');

-- =========================================================================
-- 5) Peripherals — Video & Streaming
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 35, '1080p autofocus webcam with stereo mics',                         'ClearView HD Webcam',             59.99,  (SELECT id FROM category WHERE name = 'Video & Streaming'), 'default-tenant', 'system'),
    (nextval('product_seq'), 25, '4K 60fps streaming webcam with privacy shutter',                  'StreamCam 4K Pro',                149.99, (SELECT id FROM category WHERE name = 'Video & Streaming'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, '10-inch LED ring light with phone mount and tripod',              'GlowRing Ring Light Kit',         39.99,  (SELECT id FROM category WHERE name = 'Video & Streaming'), 'default-tenant', 'system'),
    (nextval('product_seq'), 20, '4K HDMI capture card with USB-C passthrough',                     'CaptureLink 4K Capture Card',     119.99, (SELECT id FROM category WHERE name = 'Video & Streaming'), 'default-tenant', 'system'),
    (nextval('product_seq'), 45, 'Adjustable aluminium desktop tripod for webcam or phone',         'FlexiStand Desk Tripod',          24.99,  (SELECT id FROM category WHERE name = 'Video & Streaming'), 'default-tenant', 'system');

-- =========================================================================
-- 6) Accessories — Connectivity
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 80, '7-in-1 USB-C hub with HDMI, SD and 100W PD pass-through',         'PortMaster 7-in-1 USB-C Hub',     49.99,  (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system'),
    (nextval('product_seq'), 100,'USB-C to HDMI 4K 60Hz adapter',                                   'LinkUp USB-C to HDMI Adapter',    19.99,  (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system'),
    (nextval('product_seq'), 20, 'Triple-display Thunderbolt 4 docking station',                    'DockStation TB4 Pro',             229.99, (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system'),
    (nextval('product_seq'), 150,'2m braided HDMI 2.1 cable, 8K @60Hz',                             'BraidLine HDMI 2.1 Cable 2m',     12.99,  (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system'),
    (nextval('product_seq'), 150,'1m USB-C to USB-C fast charging cable, 100W',                     'BraidLine USB-C Cable 100W',      9.99,   (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system'),
    (nextval('product_seq'), 120,'3m Cat 6a shielded Ethernet cable',                               'NetLink Cat6a Ethernet 3m',       8.99,   (SELECT id FROM category WHERE name = 'Connectivity'), 'default-tenant', 'system');

-- =========================================================================
-- 7) Accessories — Storage
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 40, '1TB internal SATA SSD, 560MB/s read',                             'VaultDisk 1TB Internal SSD',      69.99,  (SELECT id FROM category WHERE name = 'Storage'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, '1TB portable USB 3.2 external SSD',                               'PocketVault 1TB External SSD',    109.99, (SELECT id FROM category WHERE name = 'Storage'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, '4TB USB-C external hard drive',                                   'ArchiveOne 4TB External HDD',     99.99,  (SELECT id FROM category WHERE name = 'Storage'), 'default-tenant', 'system'),
    (nextval('product_seq'), 120,'128GB USB 3.2 pen drive, metal body',                             'Carbon 128GB Pen Drive',          14.99,  (SELECT id FROM category WHERE name = 'Storage'), 'default-tenant', 'system'),
    (nextval('product_seq'), 100,'256GB microSD UHS-I V30 card with adapter',                       'MicroVault 256GB SD Card',        29.99,  (SELECT id FROM category WHERE name = 'Storage'), 'default-tenant', 'system');

-- =========================================================================
-- 8) Accessories — Desk Setup
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 80, 'XL cloth desk mat with stitched edges, 900x400mm',                'DeskShield XL Mousepad',          19.99, (SELECT id FROM category WHERE name = 'Desk Setup'), 'default-tenant', 'system'),
    (nextval('product_seq'), 60, 'RGB gaming mousepad with 14 lighting modes, 800x300mm',           'NeonDesk RGB Mousepad',           34.99, (SELECT id FROM category WHERE name = 'Desk Setup'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, 'Aluminium ventilated laptop stand, 6 angles',                     'AirLift Laptop Stand',            29.99, (SELECT id FROM category WHERE name = 'Desk Setup'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, 'Single-arm gas-spring monitor mount, VESA 75/100',                'SkyArm Monitor Stand',            59.99, (SELECT id FROM category WHERE name = 'Desk Setup'), 'default-tenant', 'system'),
    (nextval('product_seq'), 100,'Under-desk cable management tray with Velcro straps',             'TidyRoute Cable Organizer',       17.99, (SELECT id FROM category WHERE name = 'Desk Setup'), 'default-tenant', 'system');

-- =========================================================================
-- 9) Gaming — Gear
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 30, 'Wireless keyboard and mouse gaming combo',                        'Raven Combo Wireless Gaming Set', 129.99, (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 15, 'High-back ergonomic gaming chair with lumbar support',            'ThroneRX Gaming Chair',           299.99, (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, 'Wireless gamepad with Hall-effect sticks',                        'Storm Wireless Controller',       64.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 25, 'XXL desk mat 900x400mm, water-resistant surface',                 'Arena XXL Gaming Mat',            39.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, 'Blue-light filter gaming glasses, anti-glare',                    'NightVision Gaming Glasses',      29.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 45, 'Laptop cooling pad with 5 silent fans',                           'CoolBase Laptop Cooling Pad',     34.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, 'Dual headset stand with USB-A and 3.5mm jack',                    'DoubleHook Headset Stand',        24.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system'),
    (nextval('product_seq'), 60, '5m RGB LED strip kit with remote and app control',                'GlowEdge RGB LED Strip 5m',       22.99,  (SELECT id FROM category WHERE name = 'Gaming Gear'), 'default-tenant', 'system');

-- =========================================================================
-- 10) Office / Productivity
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 40, 'Wireless keyboard + mouse office combo, silent keys',             'OfficeDuo Wireless Combo',        59.99,  (SELECT id FROM category WHERE name = 'Office Equipment'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, '1080p meeting webcam with 110-degree FOV and AI framing',         'MeetCam Pro Webcam',              89.99,  (SELECT id FROM category WHERE name = 'Office Equipment'), 'default-tenant', 'system'),
    (nextval('product_seq'), 20, 'XLR broadcast microphone for studio and podcasts',                'StudioCast XLR Microphone',       199.99, (SELECT id FROM category WHERE name = 'Office Equipment'), 'default-tenant', 'system'),
    (nextval('product_seq'), 15, 'All-in-one colour inkjet printer with Wi-Fi',                     'InkFlow AIO Colour Printer',      159.99, (SELECT id FROM category WHERE name = 'Office Equipment'), 'default-tenant', 'system'),
    (nextval('product_seq'), 18, 'Flatbed A4 document scanner with ADF',                            'PageScan A4 Document Scanner',    189.99, (SELECT id FROM category WHERE name = 'Office Equipment'), 'default-tenant', 'system');

-- =========================================================================
-- 11) Networking / Internet
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 25, 'Wi-Fi 6 dual-band AX3000 router, 1GbE WAN',                       'MeshLink AX3000 Wi-Fi 6 Router',  129.99, (SELECT id FROM category WHERE name = 'Networking'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, 'Wi-Fi 6 dual-band range extender, wall-plug',                     'MeshLink AX1800 Extender',        59.99,  (SELECT id FROM category WHERE name = 'Networking'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, 'PCIe Wi-Fi 6 + Bluetooth 5.2 network card',                       'PulseWave PCIe Wi-Fi 6 Card',     44.99,  (SELECT id FROM category WHERE name = 'Networking'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, '8-port gigabit desktop Ethernet switch',                          'SwitchBox 8-Port Gigabit Switch', 39.99,  (SELECT id FROM category WHERE name = 'Networking'), 'default-tenant', 'system');

-- =========================================================================
-- 12) Hardware — PC Components
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 50, '32GB (2x16) DDR5-6000 CL36 desktop RAM kit',                      'PulseRAM DDR5-6000 32GB Kit',     149.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, '2TB NVMe PCIe 4.0 SSD, 7000MB/s read',                            'BlazeDisk 2TB NVMe Gen4 SSD',     199.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 10, '16GB GDDR6X gaming graphics card, ray-tracing',                   'PhantomCore RTX 16GB GPU',        899.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 18, '8-core 16-thread desktop CPU with unlocked multiplier',           'Apex 8-Core Desktop CPU',         379.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 20, 'ATX DDR5 motherboard with Wi-Fi 6E and 3x M.2',                   'ForgeBoard ATX DDR5 Motherboard', 249.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 25, '850W 80 Plus Gold fully-modular PSU',                             'VoltCore 850W Gold PSU',          129.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 30, '240mm AIO liquid CPU cooler with ARGB pump',                      'IceLoop 240 AIO Liquid Cooler',   109.99,  (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, 'Dual-tower air CPU cooler with 2 quiet fans',                     'TwinPeak Air CPU Cooler',         79.99,   (SELECT id FROM category WHERE name = 'PC Components'), 'default-tenant', 'system');

-- =========================================================================
-- 13) Power
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 60, '65W GaN USB-C fast charger, 3 ports',                             'ChargeCube 65W GaN Charger',      39.99,  (SELECT id FROM category WHERE name = 'Power'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, '20000 mAh 65W laptop-capable powerbank',                          'JuicePack 20K 65W Powerbank',     69.99,  (SELECT id FROM category WHERE name = 'Power'), 'default-tenant', 'system'),
    (nextval('product_seq'), 40, '3-in-1 wireless charging base for phone, watch, earbuds',         'TriCharge 3-in-1 Wireless Base',  49.99,  (SELECT id FROM category WHERE name = 'Power'), 'default-tenant', 'system'),
    (nextval('product_seq'), 15, '1500VA line-interactive UPS for home office',                     'GuardPower 1500VA UPS',           199.99, (SELECT id FROM category WHERE name = 'Power'), 'default-tenant', 'system');

-- =========================================================================
-- 14) Smart Tech / Gadgets
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 30, 'AMOLED smartwatch with GPS and heart-rate sensor',                'Pulse Fit AMOLED Smartwatch',     189.99, (SELECT id FROM category WHERE name = 'Smart Tech'), 'default-tenant', 'system'),
    (nextval('product_seq'), 70, '15W Qi fast wireless charging pad',                               'Halo 15W Wireless Charger',       24.99,  (SELECT id FROM category WHERE name = 'Smart Tech'), 'default-tenant', 'system'),
    (nextval('product_seq'), 45, 'Smart RGB Wi-Fi light bulb, 16M colours',                         'LumenCast Smart Bulb',            14.99,  (SELECT id FROM category WHERE name = 'Smart Tech'), 'default-tenant', 'system'),
    (nextval('product_seq'), 35, 'Voice-assistant smart speaker with far-field mics',               'EchoPoint Smart Assistant',       59.99,  (SELECT id FROM category WHERE name = 'Smart Tech'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, 'Zigbee smart home door and window sensor, 2-pack',                'GuardSense Smart Door Sensor',    27.99,  (SELECT id FROM category WHERE name = 'Smart Tech'), 'default-tenant', 'system');

-- =========================================================================
-- 15) Portability
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 40, 'Water-resistant 17-inch laptop backpack with USB port',           'TrekPack 17 Laptop Backpack',     59.99,  (SELECT id FROM category WHERE name = 'Portability'), 'default-tenant', 'system'),
    (nextval('product_seq'), 50, 'Slim 15-inch laptop shoulder bag with padded sleeve',             'SlimCarry 15 Laptop Bag',         39.99,  (SELECT id FROM category WHERE name = 'Portability'), 'default-tenant', 'system'),
    (nextval('product_seq'), 80, 'Hard shell protective case for 14-inch laptops',                  'ShellGuard 14 Laptop Case',       24.99,  (SELECT id FROM category WHERE name = 'Portability'), 'default-tenant', 'system'),
    (nextval('product_seq'), 60, 'Foldable aluminium tablet and phone stand',                       'FoldUp Portable Stand',           17.99,  (SELECT id FROM category WHERE name = 'Portability'), 'default-tenant', 'system');

-- =========================================================================
-- 16) Monitors / Displays
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 20, '27-inch QHD 240Hz IPS gaming monitor, 1ms',                       'Apex 27 QHD 240Hz Gaming Monitor', 449.99, (SELECT id FROM category WHERE name = 'Monitors'), 'default-tenant', 'system'),
    (nextval('product_seq'), 15, '34-inch WQHD ultrawide 144Hz curved monitor',                     'Vista 34 Ultrawide 144Hz',         599.99, (SELECT id FROM category WHERE name = 'Monitors'), 'default-tenant', 'system'),
    (nextval('product_seq'), 18, '32-inch 4K UHD creator monitor, 98 percent DCI-P3',               'ArtPanel 32 4K Creator Monitor',   529.99, (SELECT id FROM category WHERE name = 'Monitors'), 'default-tenant', 'system'),
    (nextval('product_seq'), 60, 'Full-motion VESA dual-monitor desk mount',                        'DualArm VESA Desk Mount',          79.99,  (SELECT id FROM category WHERE name = 'Monitors'), 'default-tenant', 'system');

-- =========================================================================
-- 17) Printing
-- =========================================================================
INSERT INTO product (id, available_quantity, description, name, price, category_id, tenant_id, created_by) VALUES
    (nextval('product_seq'), 20, 'Colour laser printer with duplex and Wi-Fi',                      'LaserPrint Colour Laser Printer', 289.99, (SELECT id FROM category WHERE name = 'Printing'), 'default-tenant', 'system'),
    (nextval('product_seq'), 120,'Tri-colour ink cartridge multipack',                              'PurePrint Tri-Colour Ink Pack',   49.99,  (SELECT id FROM category WHERE name = 'Printing'), 'default-tenant', 'system'),
    (nextval('product_seq'), 80, 'High-yield black toner cartridge, 3000 pages',                    'ToneCore Black Toner 3K',         79.99,  (SELECT id FROM category WHERE name = 'Printing'), 'default-tenant', 'system'),
    (nextval('product_seq'), 200,'A4 premium matte photo paper, 50 sheets',                         'PhotoMatte A4 Premium Paper',     14.99,  (SELECT id FROM category WHERE name = 'Printing'), 'default-tenant', 'system');
