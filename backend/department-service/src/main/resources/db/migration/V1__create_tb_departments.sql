CREATE SEQUENCE department_id_seq;

CREATE TABLE departments
(
    department_id          BIGINT DEFAULT nextval('department_id_seq') PRIMARY KEY,
    department_name        VARCHAR(255),
    department_description VARCHAR(255),
    department_code        VARCHAR(50)
);
