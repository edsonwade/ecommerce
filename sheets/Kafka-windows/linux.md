# Kafka Commands Cheat Sheet

## Basics

- `bin/kafka-server-start.sh config/server.properties`: Start Kafka broker.
- `bin/kafka-topics.sh --create --topic topic_name --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1`: Create a topic.
- `bin/kafka-topics.sh --list --bootstrap-server localhost:9092`: List all topics.
- `bin/kafka-console-producer.sh --topic topic_name --bootstrap-server localhost:9092`: Start a producer.
- `bin/kafka-console-consumer.sh --topic topic_name --bootstrap-server localhost:9092 --from-beginning`: Start a consumer.

## Topic Operations

- `bin/kafka-topics.sh --describe --topic topic_name --bootstrap-server localhost:9092`: Describe a topic.
- `bin/kafka-topics.sh --alter --topic topic_name --partitions 3 --bootstrap-server localhost:9092`: Alter a topic to increase partitions.
- `bin/kafka-topics.sh --delete --topic topic_name --bootstrap-server localhost:9092`: Delete a topic.

## Consumer Group Operations

- `bin/kafka-consumer-groups.sh --list --bootstrap-server localhost:9092`: List all consumer groups.
- `bin/kafka-consumer-groups.sh --describe --group group_name --bootstrap-server localhost:9092`: Describe a consumer group.

## Producer Performance

- `bin/kafka-producer-perf-test.sh --topic topic_name --num-records 1000000 --record-size 1000 --throughput 100000 --producer-props bootstrap.servers=localhost:9092`: Run producer performance test.

## Consumer Performance

- `bin/kafka-consumer-perf-test.sh --topic topic_name --bootstrap-server localhost:9092 --messages 1000000`: Run consumer performance test.

## Connectors

- `bin/connect-standalone.sh config/connect-standalone.properties config/connect-file-source.properties config/connect-file-sink.properties`: Run standalone Kafka Connect with file source and sink connectors.
- `bin/connect-distributed.sh config/connect-distributed.properties`: Run distributed Kafka Connect.

## Admin Operations

- `bin/kafka-configs.sh --entity-type topics --entity-name topic_name --zookeeper localhost:2181 --alter --add-config retention.ms=60000`: Set topic-level configuration.
- `bin/kafka-configs.sh --entity-type topics --entity-name topic_name --zookeeper localhost:2181 --describe`: Describe topic-level configurations.
- `bin/kafka-preferred-replica-election.sh --zookeeper localhost:2181`: Trigger preferred replica leader election.

## ACLs (Access Control Lists)

- `bin/kafka-acls.sh --authorizer-properties zookeeper.connect=localhost:2181 --add --allow-principal User:Bob --operation Read --topic topic_name --group group_name`: Add ACL for user Bob to read from topic.
- `bin/kafka-acls.sh --authorizer-properties zookeeper.connect=localhost:2181 --remove --allow-principal User:Bob --operation Read --topic topic_name --group group_name`: Remove ACL for user Bob.

## Other Operations

- `bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic topic_name --time -1`: Get offset for a topic.
- `bin/kafka-leader-election.sh --bootstrap-server localhost:9092 --election-type preferred`: Trigger leader election for all partitions.

