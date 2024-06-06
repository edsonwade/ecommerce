# MongoDB Shell Commands Cheat Sheet

## Database Operations

- `use <database_name>`: Switch to a specific database.
- `show databases`: List all databases on the MongoDB server.
- `show collections`: List all collections in the current database.

## Collection Operations

- `db.collection.find()`: Retrieve documents from a collection.
- `db.collection.insertOne({ document })`: Insert a single document into a collection.
- `db.collection.insertMany([{ document1 }, { document2 }, ... ])`: Insert multiple documents into a collection.
- `db.collection.updateOne({ filter }, { $set: { field_to_update: new_value } })`: Update a single document in a collection.
- `db.collection.updateMany({ filter }, { $set: { field_to_update: new_value } })`: Update multiple documents in a collection.
- `db.collection.deleteOne({ filter })`: Delete a single document from a collection.
- `db.collection.deleteMany({ filter })`: Delete multiple documents from a collection.
- `db.collection.aggregate([ { aggregation_pipeline_stage1 }, { aggregation_pipeline_stage2 }, ... ])`: Perform aggregation operations on a collection.
- `db.collection.drop()`: Delete the specified collection.
- `db.createCollection("collection_name")`: Create a new collection in the current database.

## Database Operations

- `db.dropDatabase()`: Delete the current database.

