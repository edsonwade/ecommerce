// mongo-init.js — runs once on first container start
// Creates application databases and users for customer and notification services.
// In production this is replaced by MongoDB Atlas provisioning.

db = db.getSiblingDB('customer_db');
db.createCollection('customers');
db.customers.createIndex({ "customerId": 1 }, { unique: true });
db.customers.createIndex({ "email": 1 },      { unique: true });

db = db.getSiblingDB('notification_db');
db.createCollection('notifications');
db.notifications.createIndex({ "notificationDate": -1 });
db.notifications.createIndex({ "type": 1 });

print('MongoDB initialisation complete: customer_db and notification_db created.');
