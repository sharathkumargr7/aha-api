# Aha API

Spring Boot application that processes music data and stores it in a PostgreSQL database.

## Prerequisites

- Java 17
- PostgreSQL
- Gradle

## Tech Stack

- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL
- Lombok
- Spring DevTools
- JUnit 5

## Getting Started

### Database Setup

1. Make sure PostgreSQL is running on your machine
2. Default configuration (from application.properties):
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
   spring.datasource.username=postgres
   spring.datasource.password=root
   spring.datasource.driver-class-name=org.postgresql.Driver
   ```

### Building the Project

```bash
./gradlew build
```

### Running the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## Project Configuration

### Application Properties

```properties
# Application Name
spring.application.name=music

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=root
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Server Configuration
server.port=8080
```

### Gradle Dependencies

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    compileOnly 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'org.postgresql:postgresql'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

## Development

### Project Structure
```
aha-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── music/
│   │   │           └── aha/
│   │   │               ├── controller/
│   │   │               ├── model/
│   │   │               ├── repository/
│   │   │               └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── build.gradle
└── README.md
```

### Java Configuration

```groovy
sourceCompatibility = '17'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

## Testing

The project uses JUnit 5 for testing. Run tests using:

```bash
./gradlew test
```

## Contributing

1. Create a new branch for your feature
2. Make your changes
3. Submit a pull request

## License

MIT License 