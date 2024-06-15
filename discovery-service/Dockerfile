# Use the official OpenJDK image for Java 17
FROM openjdk:17

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container and rename it
COPY target/discovery-service-*.jar /app/discovery-service.jar

# Specify the command to run your application
ENTRYPOINT ["java", "-jar", "discovery-service.jar"]
