# Kafka Commands Cheat Sheet (Windows)

## Basics

- `.\bin\windows\kafka-server-start.bat .\config\server.properties`: Start Kafka broker.
- `.\bin\windows\kafka-topics.bat --create --topic topic_name --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1`: Create a topic.
- `.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092`: List all topics.
- `.\bin\windows\kafka-console-producer.bat --topic topic_name --bootstrap-server localhost:9092`: Start a producer.
- `.\bin\windows\kafka-console-consumer.bat --topic topic_name --bootstrap-server localhost:9092 --from-beginning`: Start a consumer.

## Topic Operations

- `.\bin\windows\kafka-topics.bat --describe --topic topic_name --bootstrap-server localhost:9092`: Describe a topic.
- `.\bin\windows\kafka-topics.bat --alter --topic topic_name --partitions 3 --bootstrap-server localhost:9092`: Alter a topic to increase partitions.
- `.\bin\windows\kafka-topics.bat --delete --topic topic_name --bootstrap-server localhost:9092`: Delete a topic.

## Consumer Group Operations

- `.\bin\windows\kafka-consumer-groups.bat --list --bootstrap-server localhost:9092`: List all consumer groups.
- `.\bin\windows\kafka-consumer-groups.bat --describe --group group_name --bootstrap-server localhost:9092`: Describe a consumer group.

## Producer Performance

- `.\bin\windows\kafka-producer-perf-test.bat --topic topic_name --num-records 1000000 --record-size 1000 --throughput 100000 --producer-props bootstrap.servers=localhost:9092`: Run producer performance test.

## Consumer Performance

- `.\bin\windows\kafka-consumer-perf-test.bat --topic topic_name --bootstrap-server localhost:9092 --messages 1000000`: Run consumer performance test.

## Connectors

- `.\bin\windows\connect-standalone.bat .\config\connect-standalone.properties .\config\connect-file-source.properties .\config\connect-file-sink.properties`: Run standalone Kafka Connect with file source and sink connectors.
- `.\bin\windows\connect-distributed.bat .\config\connect-distributed.properties`: Run distributed Kafka Connect.

## Admin Operations

- `.\bin\windows\kafka-configs.bat --entity-type topics --entity-name topic_name --zookeeper localhost:2181 --alter --add-config retention.ms=60000`: Set topic-level configuration.
- `.\bin\windows\kafka-configs.bat --entity-type topics --entity-name topic_name --zookeeper localhost:2181 --describe`: Describe topic-level configurations.
- `.\bin\windows\kafka-preferred-replica-election.bat --zookeeper localhost:2181`: Trigger preferred replica leader election.

## ACLs (Access Control Lists)

- `.\bin\windows\kafka-acls.bat --authorizer-properties zookeeper.connect=localhost:2181 --add --allow-principal User:Bob --operation Read --topic topic_name --group group_name`: Add ACL for user Bob to read from topic.
- `.\bin\windows\kafka-acls.bat --authorizer-properties zookeeper.connect=localhost:2181 --remove --allow-principal User:Bob --operation Read --topic topic_name --group group_name`: Remove ACL for user Bob.

## Other Operations

- `.\bin\windows\kafka-run-class.bat kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic topic_name --time -1`: Get offset for a topic.
- `.\bin\windows\kafka-leader-election.bat --bootstrap-server localhost:9092 --election-type preferred`: Trigger leader election for all partitions.

