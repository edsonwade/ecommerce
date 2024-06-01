#Build stage
FROM maven:3.6-openjdk-17 AS  build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean install  -DskipTests

#Runtime stage
FROM amazoncorretto:17
ARG PROFILE=dev
ARG APP_VERSION=1.0.0

WORKDIR /app
COPY --from=build /build/target/product-service-*.jar /app/

EXPOSE 8082
ENV DB_URL=jdbc:postgresql://localhost:5432/product_service_db
ENV DB_USERNAME=postgres
ENV DB_PASSWORD=test
ENV ACTIVE_PROFILE=${PROFILE}
ENV JAR_VERSION=${APP_VERSION}
CMD java -jar Dspring-profiles-active=${ACTIVE_PROFILE} -Dspring.datasource.url=${DB_URL} Dspring.datasource
.username=${DB_USERNAME} -Dspring.datasource.password=${DB_PASSWORD} product-service-${APP_VERSION}.jar