package com.music.aha;

import io.github.cdimascio.dotenv.Dotenv;
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
        // Load .env file before Spring Boot starts
        // This allows Spring Boot to read environment variables from .env
        try {
            // Try to find .env file in multiple locations
            // 1. Current working directory
            // 2. Project root (aha-api directory)
            // 3. Parent of current directory
            Dotenv dotenv = null;
            String[] possiblePaths = {
                ".env",  // Current directory
                "aha-api/.env",  // If running from parent directory
                System.getProperty("user.dir") + "/.env",  // Explicit current directory
                System.getProperty("user.dir") + "/aha-api/.env"  // Explicit aha-api directory
            };
            
            for (String path : possiblePaths) {
                try {
                    java.io.File envFile = new java.io.File(path);
                    if (envFile.exists() && envFile.isFile()) {
                        String parent = envFile.getParent();
                        String filename = envFile.getName();
                        if (parent != null) {
                            dotenv = Dotenv.configure()
                                    .directory(parent)
                                    .filename(filename)
                                    .ignoreIfMissing()
                                    .load();
                        } else {
                            // File is in current directory
                            dotenv = Dotenv.configure()
                                    .filename(filename)
                                    .ignoreIfMissing()
                                    .load();
                        }
                        System.out.println("Loaded .env file from: " + envFile.getAbsolutePath());
                        break;
                    }
                } catch (Exception e) {
                    // Try next path
                }
            }
            
            // If still not found, try default location
            if (dotenv == null) {
                dotenv = Dotenv.configure()
                        .ignoreIfMissing()
                        .load();
            }
            
            // Set system properties from .env file so Spring can access them
            if (dotenv != null) {
                dotenv.entries().forEach(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    // Only set if not already set (environment variables take precedence)
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                });
                
                // Map YOUTUBE_API_KEY from .env to Spring Boot property name
                // This ensures Spring Boot can read it even if the env var isn't set
                String youtubeApiKey = dotenv.get("YOUTUBE_API_KEY");
                if (youtubeApiKey != null && !youtubeApiKey.trim().isEmpty()) {
                    if (System.getProperty("youtube.api.key") == null) {
                        System.setProperty("youtube.api.key", youtubeApiKey);
                        System.out.println("YouTube API key loaded from .env file");
                    }
                } else {
                    System.out.println("WARNING: YOUTUBE_API_KEY not found in .env file");
                }
            } else {
                System.out.println("WARNING: .env file not found. Using environment variables or application.properties");
            }
        } catch (Exception e) {
            System.err.println("Error loading .env file: " + e.getMessage());
            e.printStackTrace();
            // Continue anyway - environment variables or application.properties will be used
        }
        
        // Run the Spring Boot application.
        SpringApplication.run(AhaMusicApplication.class, args);
    }
}