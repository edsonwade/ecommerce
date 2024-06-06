-- Create the payment table
BEGIN;
CREATE TABLE payment
(
    payment_id         SERIAL PRIMARY KEY,
    amount             NUMERIC(10, 2),
    payment_method     VARCHAR(50),
    order_id           INT,
    created_date       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP
);

create sequence if not exists payment_seq increment by 50;


INSERT INTO payment (payment_id, amount, payment_method, order_id, created_date, last_modified_date)
VALUES (nextval('payment_seq'), 100.00, 'CREDIT_CARD', 12345, '2022-01-01 00:00:00', '2022-01-01 00:00:00'),
       (nextval('payment_seq'), 75.50, 'PAYPAL', 54321, '2022-01-02 08:30:00', '2022-01-02 08:30:00'),
       (nextval('payment_seq'), 50.25, 'DEBIT_CARD', 98765, '2022-01-03 15:45:00', '2022-01-03 15:45:00'),
       (nextval('payment_seq'), 120.75, 'MASTER_CARD', 13579, '2022-01-04 12:00:00', '2022-01-04 12:00:00');
COMMIT

