# Loan Management Application

This is a Loan Management Application built with Spring Boot. It allows users to create loans, process payments, view loan details, and handle user authentication (signup and login) using JWT tokens. The application supports role-based access control where:
- **Customers** can only create loans for themselves.
- **Admins** can create loans for any customer.

The application also includes Swagger/OpenAPI documentation and is fully Dockerized for easy deployment.

## Table of Contents

- [Features](#features)
- [Technologies](#technologies)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Running the Application](#running-the-application)
    - [Using Spring Boot (Local)](#using-spring-boot-local)
    - [Switching Profiles](#switching-profiles)
- [API Documentation (Swagger)](#api-documentation-swagger)
- [Testing](#testing)
- [Dockerization](#dockerization)
    - [Multi-Stage Docker Build](#multi-stage-docker-build)
- [Data Initialization](#data-initialization)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Features

- **User Authentication:**
    - Signup: Create new users and their associated customer records.
    - Login: Authenticate users and generate JWT tokens.
- **Loan Management:**
    - Create Loan: Customers can create loans for themselves; admins can create loans for any customer.
    - View Loans: Retrieve loans for a customer along with computed remaining fees.
    - Payment Processing: Process payments against loan installments.
    - Delete Loan: Remove loans (admins only) and adjust customer credit accordingly.
- **API Documentation:**
    - Swagger UI is provided to explore and test all endpoints.
- **Docker Support:**
    - Fully Dockerized using a multi-stage build for a minimal final image.
- **Profiles:**
    - Easily switch between different profiles (e.g., dev, prod) to load specific configurations.
- **Data Initialization:**
    - A starter initializer creates sample user and customer records (2 admins and 3 customers) when running in development mode.

## Technologies

- **Backend:** Spring Boot, Spring MVC, Spring Data JPA, Spring Security (JWT)
- **API Documentation:** Swagger / OpenAPI (using `springdoc-openapi`)
- **Database:** H2 (for development) or any other relational DB configured via `application.properties`
- **Build Tool:** Maven
- **Containerization:** Docker (multi-stage build)
- **Testing:** JUnit 5, Mockito

## Prerequisites

- **Java JDK 17 or later**
- **Maven 3.6 or later**
- **Docker** (for containerization)
- **Git** (for source code management)

## Installation

1. **Clone the Repository:**

   ```bash
   git clone https://github.com/eccsm/loan-management-app.git
   cd loan-management-app
   ```
2. **Build the Application:**

   Use Maven to build the project:

   ```bash
   mvn clean package
   ```
   This will create an executable JAR file (e.g., `loan-app-0.0.1-SNAPSHOT.jar`) in the `target/` directory.

## Running the Application

### Using Spring Boot (Local)

To run the application locally using the built JAR:

```bash
java -jar target/loan-app.jar --spring.profiles.active=dev
```

Or use Maven:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Switching Profiles

You can change the active profile by passing the `--spring.profiles.active` argument. For example, to run with the production profile:

```bash
java -jar target/loan-app.jar --spring.profiles.active=prod
```

Alternatively, set the environment variable `SPRING_PROFILES_ACTIVE`:

**Linux/Mac:**

```bash
export SPRING_PROFILES_ACTIVE=dev
java -jar target/loan-app.jar
```

**Windows (cmd):**

```cmd
set SPRING_PROFILES_ACTIVE=dev
java -jar target/loan-app.jar
```

## API Documentation (Swagger)

This application uses `springdoc-openapi` to generate Swagger UI for API documentation.

**Swagger UI Endpoint:**

By default, you can access the Swagger UI at:

```bash
http://localhost:8080/swagger-ui/index.html
```

If needed, customize the path in your `application.properties`:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
```

## Testing

The project uses JUnit 5 and Mockito for unit testing.

Run Tests:

```bash
mvn test
```

## Dockerization

This project is Dockerized using a multi-stage Docker build. Below is the Dockerfile setup:

### Multi-Stage Docker Build

Create a `Dockerfile` in the root of your project:

```dockerfile
# ---------- Stage 1: Build Stage ----------
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app
# Copy Maven pom.xml and download dependencies.
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source code and build the application.
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: Runtime Stage ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/jbd-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build the Docker Image:**

```bash
docker build -t loan-app .
```

**Run the Docker Container:**

```bash
docker run -p 8080:8080 loan-app
```

Your application will now be accessible at `http://localhost:8080`.

## Data Initialization

A data initializer (using `CommandLineRunner`) is included (e.g., in a class like `DataInitializer` under the `net.casim.jbd.initializer` package). This component creates sample users (2 admins and 3 customers) when the application starts in **development mode**.

### Activation
The initializer is annotated with `@Profile("dev")`, so it runs only when the application starts with `--spring.profiles.active=dev`.

### Usage
When you run your app in **dev mode**, the initializer creates sample users that you can use to log in and test the application.

## Troubleshooting

- **Swagger UI Not Found:** If you see an error like `No static resource swagger-ui/index.html`, verify that the correct `springdoc-openapi` dependency is included and check your Swagger UI path configuration.
- **Mockito Agent Warning:** If you see a warning about Mockito self-attaching, configure the Mockito agent in your build (see Mockito’s documentation).
- **Profile Issues:** Ensure to set the active profile using `--spring.profiles.active` or an environment variable if you expect specific behavior (like data initialization in dev mode).

## License

This project is licensed under the **MIT License**. See the `LICENSE` file for details.

