-- Insert categories with unique names
INSERT INTO category (id, description, name)
VALUES (nextval('category_seq'), 'Computer Keyboards', 'Keyboards')
ON CONFLICT (name) DO NOTHING;

INSERT INTO category (id, description, name)
VALUES (nextval('category_seq'), 'Computer Monitors', 'Monitors')
ON CONFLICT (name) DO NOTHING;

INSERT INTO category (id, description, name)
VALUES (nextval('category_seq'), 'Display Screens', 'Screens')
ON CONFLICT (name) DO NOTHING;

INSERT INTO category (id, description, name)
VALUES (nextval('category_seq'), 'Computer Mice', 'Mice')
ON CONFLICT (name) DO NOTHING;

INSERT INTO category (id, description, name)
VALUES (nextval('category_seq'), 'Computer Accessories', 'Accessories')
ON CONFLICT (name) DO NOTHING;
