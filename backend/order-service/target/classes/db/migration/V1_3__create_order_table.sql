-- Create the customer_order table
CREATE TABLE customer_order
(
    order_id           SERIAL PRIMARY KEY,
    reference          VARCHAR(255) NOT NULL UNIQUE,
    total_amount       NUMERIC(12, 2),
    payment_method     VARCHAR(50),
    customer_id        VARCHAR(255),
    created_date       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP
);

create sequence if not exists order_id_seq increment by 50;


-- Insert sample data
INSERT INTO customer_order (order_id, reference, total_amount, payment_method, customer_id, created_date)
VALUES (nextval('order_id_seq'), 'REF123', 100.00, 'CREDIT_CARD', '1', '2024-06-07T10:00:00'),
       (nextval('order_id_seq'), 'REF456', 150.50, 'PAYPAL', '2', '2024-06-07T11:00:00');
