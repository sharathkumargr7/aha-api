# Aha API

Spring Boot application that processes Excel files containing music data and stores it in a PostgreSQL database.

## Prerequisites

- Java 17
- PostgreSQL 
- Gradle

## Tech Stack

- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL
- Lombok
- JUnit 5

## Getting Started

### Database Setup

1. Make sure PostgreSQL is running on your machine
2. Default configuration:
   - Database: postgres
   - Username: postgres
   - Password: root
   - Port: 5432

You can modify these settings in `src/main/resources/application.properties`

### Building the Project

```bash
./gradlew :aha-api:build
```

### Running the Application

```bash
./gradlew :aha-api:bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

### Import Music Data
```http
POST /api/music/import?filename={excel_filename}
```
- Reads an Excel file from the current directory
- Processes music data (ACR_ID, Title, Artists, Time, Source_URL, Detail_URL)
- Stores the data in PostgreSQL

## Configuration

### Application Properties

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=root

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

## Development

### Project Structure
```
aha-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── aha/
│   │   │           └── api/
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

## Contributing

1. Create a new branch for your feature
2. Make your changes
3. Submit a pull request

## License

MIT License 