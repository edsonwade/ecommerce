-- V1_8__fix_sequence_start_values.sql
-- Fix duplicate-key errors caused by customer_order_seq and customer_line_seq
-- starting at 1 while sample data already occupies low IDs.
-- Reset each sequence to (max existing id + 50) so Hibernate's next hi-lo
-- block is safely above all existing rows.

SELECT setval('customer_order_seq', COALESCE((SELECT MAX(order_id) FROM customer_order), 0) + 50);
SELECT setval('customer_line_seq',  COALESCE((SELECT MAX(id)       FROM customer_line),  0) + 50);
