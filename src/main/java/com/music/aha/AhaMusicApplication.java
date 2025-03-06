package com.music.aha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Aha Music.
 */
@SpringBootApplication
public class AhaMusicApplication {
    /**
     * Main entry point for the application.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        // Run the Spring Boot application.
        SpringApplication.run(AhaMusicApplication.class, args);
    }
}