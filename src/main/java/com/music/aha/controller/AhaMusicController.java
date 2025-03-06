package com.music.aha.controller;

import com.music.aha.model.AhaMusic;
import com.music.aha.service.AhaMusicService;
import com.music.aha.service.AhaMusicService.CleanupResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/music")
@CrossOrigin(origins = "http://localhost:4200")
public class AhaMusicController {

    @Autowired
    private AhaMusicService musicService;

    @GetMapping("/all")
    public ResponseEntity<List<AhaMusic>> getAllMusic() {
        return ResponseEntity.ok(musicService.getAllUniqueRecords());
    }

    @GetMapping("/import")
    @PostMapping("/import")
    public ResponseEntity<String> importCsvFile() {
        try {
            String filePath = Paths.get("aha.csv").toAbsolutePath().toString();
            long beforeCount = musicService.getUniqueRecordCount();
            musicService.importCsvFile(filePath);
            long afterCount = musicService.getUniqueRecordCount();
            long newRecords = afterCount - beforeCount;
            
            return ResponseEntity.ok(String.format("CSV file imported successfully. Added %d new unique records. Total records: %d", 
                newRecords, afterCount));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error importing CSV file: " + e.getMessage());
        }
    }

    @GetMapping("/cleanup")
    public ResponseEntity<String> cleanupDuplicates() {
        try {
            CleanupResult result = musicService.cleanupDuplicates();
            return ResponseEntity.ok(String.format(
                "Cleanup completed successfully. Original records: %d, After cleanup: %d, Removed duplicates: %d",
                result.getOriginalCount(), result.getNewCount(), result.getRemovedDuplicates()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error cleaning up duplicates: " + e.getMessage());
        }
    }
} 