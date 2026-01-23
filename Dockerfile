# Use an official Maven image to build the application
FROM --platform=linux/amd64 maven:3.9.9-eclipse-temurin-23 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and install dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline

# Copy checkstyle configuration files needed for the build
COPY checkstyle-suppressions.xml ./

# Copy the entire project and build it
COPY src ./src
RUN mvn clean package -DskipTests

# Use an official Java runtime as a parent image
FROM --platform=linux/amd64 eclipse-temurin:23-jdk-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/portfolio.backend-0.0.1-SNAPSHOT.jar ./app.jar

# Copy all properties files
COPY src/main/resources/*.properties ./

# Expose the port the app runs on
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]