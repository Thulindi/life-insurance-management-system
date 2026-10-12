**Source files:** Download and extract [the project archive](life-insurance-management-system.zip). The setup instructions below refer to the extracted archive.

# Life Insurance Management System

Team web application for life insurance policy applications, claims, payments, messaging, audit logs, and administrative workflows. Built with Spring Boot, Thymeleaf, and MySQL.

## Local setup

1. Install Java 17 or newer and MySQL 8 or newer.
2. Create an empty database named `v1_16_thuli_life_insurance`.
3. Set `DB_USERNAME` and `DB_PASSWORD` in your environment. The application reads these values from `src/main/resources/application.properties`.
4. Run `./mvnw spring-boot:run` (or `mvnw.cmd spring-boot:run` on Windows).
5. Open `http://localhost:8080`.

The app uses Hibernate's `update` mode to create and update tables. Local database credentials, seeded user accounts, build output, and troubleshooting logs are excluded from this public repository.
