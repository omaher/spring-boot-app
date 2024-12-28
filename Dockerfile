# Stage 1: Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime Stage
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Copy the keystore file (make sure to update the path)
COPY --from=builder /app/src/main/resources/keystore.p12 /app/keystore.p12

# Expose port and set the entrypoint
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]
