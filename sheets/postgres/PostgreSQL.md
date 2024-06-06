# PostgreSQL Shell Commands Cheat Sheet

## Database Operations

- `\l`: List all databases.
- `\c <database_name>`: Connect to a specific database.
- `\dt`: List all tables in the current database.
- `\dn`: List all schemas in the current database.
- `\du`: List all roles (users) in the current PostgreSQL database cluster.

## Table Operations

- `\d <table_name>`: Display the structure of a specific table.
- `\d+ <table_name>`: Display detailed information about a specific table.
- `\dt+`: List all tables in the current database with additional details.
- `\d <schema_name>.*`: List all tables in a specific schema.
- `\df`: List all functions in the current database.
- `\dv`: List all views in the current database.

## Data Operations

- `\dt <table_name>`: Display the first few rows of a specific table.
- `\copy <table_name> FROM '<file_path>'`: Import data from a CSV file into a table.
- `\copy <table_name> TO '<file_path>'`: Export data from a table to a CSV file.
- `SELECT * FROM <table_name>;`: Retrieve all rows from a table.
- `INSERT INTO <table_name> (column1, column2, ...) VALUES (value1, value2, ...);`: Insert a new row into a table.
- `UPDATE <table_name> SET column1 = value1, column2 = value2 WHERE condition;`: Update existing rows in a table.
- `DELETE FROM <table_name> WHERE condition;`: Delete rows from a table based on a condition.
- `TRUNCATE TABLE <table_name>;`: Remove all rows from a table (without logging each individual deletion).

## Schema Operations

- `\dn`: List all schemas in the current database.
- `\dn+`: List all schemas in the current database with additional details.
- `CREATE SCHEMA <schema_name>;`: Create a new schema in the current database.
- `DROP SCHEMA <schema_name> CASCADE;`: Delete a schema and all its contained objects.

## User and Role Operations

- `\du`: List all roles (users) in the current PostgreSQL database cluster.
- `\du+`: List all roles (users) in the current PostgreSQL database cluster with additional details.
- `CREATE ROLE <role_name>;`: Create a new role (user) in the current database cluster.
- `ALTER ROLE <role_name> WITH PASSWORD '<password>';`: Set or change the password for a role.
- `DROP ROLE <role_name>;`: Delete a role from the database cluster.

## Transaction Control

- `\set AUTOCOMMIT {ON | OFF}`: Enable or disable automatic transaction mode.
- `\commit`: Commit the current transaction.
- `\rollback`: Roll back the current transaction.

