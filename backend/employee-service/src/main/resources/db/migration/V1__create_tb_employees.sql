CREATE SEQUENCE employee_id_seq;

CREATE TABLE employees
(
    employee_id    BIGINT DEFAULT nextval('employee_id_seq') PRIMARY KEY,
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    email           VARCHAR(50) unique,
    department_code VARCHAR(255)
);
