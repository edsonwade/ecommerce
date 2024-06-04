# Use the official OpenJDK image for Java 17
FROM openjdk:17

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container and rename it
COPY target/config-service-0.0.1-SNAPSHOT.jar /app/config-service.jar

# Specify the command to run your application
CMD ["java", "-jar", "config-service.jar"]
