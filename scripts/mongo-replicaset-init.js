// mongo-replicaset-init.js — Initialize MongoDB 3-node replica set
// Run on the PRIMARY node: mongosh --host mongo1:27017 mongo-replicaset-init.js
// Nodes: mongo1 (primary), mongo2 (secondary), mongo3 (secondary)

rs.initiate({
  _id: "rs0",
  members: [
    {
      _id: 0,
      host: "mongo1:27017",
      priority: 2  // highest priority — preferred primary
    },
    {
      _id: 1,
      host: "mongo2:27017",
      priority: 1
    },
    {
      _id: 2,
      host: "mongo3:27017",
      priority: 1
    }
  ]
});

// Wait for replica set to elect primary
print("Waiting for replica set election...");
let attempts = 0;
while (attempts < 30) {
  const status = rs.status();
  const primary = status.members.find(m => m.stateStr === "PRIMARY");
  if (primary) {
    print("Primary elected: " + primary.name);
    break;
  }
  sleep(2000);
  attempts++;
}

// Create application databases and indexes on primary
db = db.getSiblingDB("customer_db");
db.createCollection("customers");
db.customers.createIndex({ customerId: 1 }, { unique: true });
db.customers.createIndex({ email: 1 }, { unique: true });
print("customer_db indexes created");

db = db.getSiblingDB("notification_db");
db.createCollection("notifications");
db.notifications.createIndex({ notificationDate: -1 });
db.notifications.createIndex({ customerId: 1 });

db.createCollection("processed_events");
db.processed_events.createIndex({ eventId: 1 }, { unique: true });
db.processed_events.createIndex({ processedAt: 1 }, { expireAfterSeconds: 604800 }); // TTL 7 days

db.createCollection("dlq_events");
db.dlq_events.createIndex({ receivedAt: -1 });
db.dlq_events.createIndex({ topic: 1 });
print("notification_db indexes created");

print("Replica set initialization complete.");
print(JSON.stringify(rs.conf(), null, 2));
