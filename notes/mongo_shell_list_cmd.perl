select * from customer where customerId='1';
select * from customer;


db.customer.deleteOne({ email: "sonia@example.com" })


Here is a list of commonly used MongoDB shell commands:

use: Switch to a specific database.

use <database_name>


show databases: List all databases on the MongoDB server.


show databases
