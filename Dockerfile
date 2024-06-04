# Use the official OpenJDK image for Java 17
FROM openjdk:17

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container
COPY ecommerce.jar /app

# Specify the command to run your application
CMD ["java", "-jar", "ecommerce.jar"]
