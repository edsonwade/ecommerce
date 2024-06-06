# Oracle SQL*Plus and SQL Developer Commands

## General Commands

- `CONNECT username/password[@connect_identifier]`: Connects to a database.
- `DISCONNECT`: Disconnects from the current database session.
- `EXIT` or `QUIT`: Exits SQL*Plus.
- `SPOOL filename`: Redirects output to a file.
- `START filename`: Executes commands stored in a file.
- `@filename`: Executes commands stored in a file.

## Transaction Control Commands

- `COMMIT`: Commits the current transaction.
- `ROLLBACK`: Rolls back the current transaction.
- `SAVEPOINT savepoint_name`: Sets a savepoint within the current transaction.
- `RELEASE SAVEPOINT savepoint_name`: Removes a savepoint.

## Editing Commands

- `EDIT`: Invokes the default editor to edit the current SQL statement.
- `CLEAR SCREEN`: Clears the screen.
- `HOST command`: Executes an operating system command.
- `GET filename`: Retrieves and executes SQL commands from a file.

## Display Commands

- `SET AUTOTRACE {ON|OFF|TRACE|EXPLAIN}`: Controls automatic display of execution statistics.
- `SET ECHO {ON|OFF}`: Controls display of the command in the output.
- `SET FEEDBACK {ON|OFF}`: Controls display of number of rows returned.
- `SET LINESIZE n`: Sets the width of the display screen.
- `SET PAGESIZE n`: Sets the number of lines per page for output display.
- `SET VERIFY {ON|OFF}`: Controls display of the text of a SQL command.

## Formatting Commands

- `COLUMN column_name FORMAT format`: Formats the output of a column.
- `BREAK ON column_name`: Breaks output based on a column value.
- `TTITLE title`: Sets the top title of a report.
- `BTITLE title`: Sets the bottom title of a report.

## Scripting Commands

- `DEFINE variable_name = value`: Defines a substitution variable.
- `ACCEPT variable_name [DATATYPE] [FORMAT] [PROMPT]`: Prompts the user for a value.
- `PROMPT message`: Displays a message.

## Help Commands

- `HELP`: Displays help topics.
- `HELP command`: Displays help for a specific command.
- `DESC[RIBE] object_name`: Describes the structure of a table, view, or synonym.

# Oracle SQL Developer Commands

## Navigation and Editing

- `CTRL + LEFT_ARROW` and `CTRL + RIGHT_ARROW`: Move between words.
- `CTRL + BACKSPACE` and `CTRL + DELETE`: Delete word backward/forward.
- `CTRL + ENTER`: Run the current SQL statement.
- `CTRL + SPACE`: Autocomplete.
- `F2`: Describe object under cursor.
- `CTRL + F7`: Show database objects.

## Querying

- `SELECT * FROM table_name;`: Execute a SQL query.
- `SELECT column1, column2 FROM table_name WHERE condition;`: Execute a SQL query with a condition.

## Formatting and Display

- `CTRL + SHIFT + F7`: Toggle SQL Formatter.
- `CTRL + TAB`: Cycle through open files.
- `CTRL + E`: Execute script.

## Miscellaneous

- `F4`: Open object viewer/editor.
- `CTRL + R`: Open SQL history.
- `F12`: Toggle DBMS output panel.
- `CTRL + L`: Clear screen.
- `CTRL + F`: Find text.

## Debugging

- `F5`: Execute as script.
- `F9`: Execute current statement.
- `CTRL + SHIFT + X`: Execute Explain Plan.
- `CTRL + SHIFT + D`: Debug.

## Help and Documentation

- `F1`: Open help.
- `CTRL + H`: Find in files.
- `F10`: Show DBMS output.

## Administration

- `CTRL + SHIFT + K`: Kill session.
- `CTRL + SHIFT + I`: Show Instance Viewer.
