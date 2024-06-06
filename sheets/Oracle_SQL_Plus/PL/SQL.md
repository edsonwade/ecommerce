# PL/SQL Commands Cheat Sheet

## Basic Commands

- `DECLARE`: Begins a declaration section of a PL/SQL block.
- `BEGIN`: Begins the executable section of a PL/SQL block.
- `END;`: Ends a PL/SQL block.
- `/`: Executes the current PL/SQL block.
- `EXIT`: Exits a loop or a block.
- `COMMIT`: Commits the current transaction.
- `ROLLBACK`: Rolls back the current transaction.

## Variables and Constants

- `DECLARE <variable_name> <data_type>;`: Declares a variable.
- `CONSTANT <constant_name> <data_type> := <value>;`: Declares a constant.
- `:=`: Assignment operator for variables and constants.

## Control Structures

- `IF condition THEN ... [ELSIF condition THEN ...] [ELSE ...] END IF;`: Conditional statement.
- `CASE ... WHEN ... THEN ... [WHEN ... THEN ...] [ELSE ...] END CASE;`: Case statement.
- `LOOP ... END LOOP;`: Infinite loop.
- `WHILE condition LOOP ... END LOOP;`: Loop with a condition.
- `FOR <variable_name> IN <lower_bound>.. <upper_bound> LOOP ... END LOOP;`: Loop with a specified range.

## Cursors

- `CURSOR <cursor_name> IS <query>;`: Declares a cursor.
- `OPEN <cursor_name>;`: Opens a cursor.
- `FETCH <cursor_name> INTO <variable>;`: Fetches data from a cursor.
- `CLOSE <cursor_name>;`: Closes a cursor.

## Exception Handling

- `EXCEPTION WHEN <exception_name> THEN ... [WHEN <exception_name> THEN ...] [WHEN OTHERS THEN ...] END;`: Handles exceptions.

## Procedures and Functions

- `CREATE OR REPLACE PROCEDURE <procedure_name> AS ... BEGIN ... END;`: Creates a procedure.
- `CREATE OR REPLACE FUNCTION <function_name> RETURN <return_type> AS ... BEGIN ... END;`: Creates a function.
- `EXECUTE <procedure_name>;`: Executes a procedure.
- `SELECT <function_name>() FROM DUAL;`: Calls a function in SQL.

## Packages

- `CREATE OR REPLACE PACKAGE <package_name> AS ... END;`: Creates a package specification.
- `CREATE OR REPLACE PACKAGE BODY <package_name> AS ... END;`: Creates a package body.
- `PACKAGE_NAME.PROCEDURE_NAME;`: Calls a procedure from a package.
- `PACKAGE_NAME.FUNCTION_NAME;`: Calls a function from a package.

## Triggers

- `CREATE OR REPLACE TRIGGER <trigger_name> BEFORE/AFTER INSERT/UPDATE/DELETE ON <table_name> FOR EACH ROW AS ... BEGIN ... END;`: Creates a trigger.

## Dynamic SQL

- `EXECUTE IMMEDIATE <sql_statement>;`: Executes a dynamic SQL statement.

