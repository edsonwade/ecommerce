-- V1_7__add_missing_sequences.sql
-- Hibernate 6 @GeneratedValue(AUTO) resolves to a named sequence per table.
-- PostgreSQL's SERIAL only creates <table>_<column>_seq (e.g. customer_line_id_seq)
-- but Hibernate schema validation looks for <table>_seq.
-- Increment 50 matches Hibernate 6's default allocationSize.

CREATE SEQUENCE IF NOT EXISTS customer_line_seq  INCREMENT BY 50 START WITH 1;
CREATE SEQUENCE IF NOT EXISTS customer_order_seq INCREMENT BY 50 START WITH 1;
