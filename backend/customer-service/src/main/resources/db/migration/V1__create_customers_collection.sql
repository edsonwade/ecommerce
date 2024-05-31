-- Create the customers collection
CREATE COLLECTION customers;

-- Define the structure of the customers collection
db.createCollection("customers", {
   validator: {
      $jsonSchema: {
         bsonType: "object",
         required: ["customerId", "firstname", "lastname", "email", "address"],
         properties: {
            customerId: {
               bsonType: "string"
            },
            firstname: {
               bsonType: "string"
            },
            lastname: {
               bsonType: "string"
            },
            email: {
               bsonType: "string",
               pattern: "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$",
               description: "must be a valid email address"
            },
            address: {
               bsonType: "object",
               properties: {
                  street: {
                     bsonType: "string"
                  },
                  house_number: {
                     bsonType: "string"
                  },
                  zip_number: {
                     bsonType: "string"
                  },
                  city: {
                     bsonType: "string"
                  },
                 country: {
                     bsonType: "string"
               }
            }
         }
      }
   }
});

-- Insert sample data into the customers collection
db.customers.insertMany([
   {
      customerId: "1",
      firstname: "John",
      lastname: "Doe",
      email: "john.doe@example.com",
      address: {
         street: "123 Street",
         house_number: "39",
         zip_code:"4000-890"
         city: "Porto",
         country: "Portugal"
      }
   },
   {
      customerId: "2",
      firstname: "Jane",
      lastname: "Smith",
      email: "jane.smith@example.com",
      address: {
         street: "456 Avenue",
         house_number: "149",
         zip_code:"4000-000"
         city: "Lisbon",
         country: "Portugal"
      }
   }
]);