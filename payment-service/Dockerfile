# Use the official OpenJDK image for Java 17
FROM openjdk:17

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container and rename it
COPY target/payment-service-*.jar /app/payment-service.jar

# Specify the command to run your application
CMD ["java", "-jar", "payment-service.jar"]
