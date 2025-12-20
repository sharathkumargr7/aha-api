package com.music.aha.service;

import com.music.aha.model.AhaMusic;
import com.music.aha.repository.AhaMusicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AhaMusicService {

    @Autowired
    private AhaMusicRepository repository;

    // Utility to find the latest matching CSV file in E:\\Downloads
    public static File getLatestAhaMusicCsv() {
        File downloadsDir = new File("E:\\Downloads");
        File[] csvFiles = downloadsDir.listFiles((dir, name) -> name.startsWith("aha-music-export") && name.endsWith(".csv"));

        if (csvFiles == null || csvFiles.length == 0) {
            return null;
        }
        Pattern datePattern = Pattern.compile("aha-music-export_(\\d{4}-\\d{2}-\\d{2})\\.csv");
        File latestFile = null;
        String maxDate = "";

        for (File file : csvFiles) {
            Matcher matcher = datePattern.matcher(file.getName());
            if (matcher.matches()) {
                String datePart = matcher.group(1); // yyyy-mm-dd
                if (datePart.compareTo(maxDate) > 0) {
                    maxDate = datePart;
                    latestFile = file;
                }
            }
        }
        return latestFile;
    }

    @Transactional
    public void importCsvFile(String filePath) throws IOException {
        Map<String, AhaMusic> uniqueRecords = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Skip the header line
            String line = br.readLine();
            
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                
                if (values.length >= 6) {
                    String title = values[1].replace("\"", "");
                    String artists = values[2].replace("\"", "");
                    String uniqueKey = title + "|" + artists;
                    LocalDateTime currentTime = LocalDateTime.parse(values[3], formatter);

                    // Only keep the most recent occurrence of a title-artist combination
                    if (!uniqueRecords.containsKey(uniqueKey) || 
                        currentTime.isAfter(uniqueRecords.get(uniqueKey).getTime())) {
                        
                        AhaMusic music = new AhaMusic();
                        music.setAcrId(values[0]);
                        music.setTitle(title);
                        music.setArtists(artists);
                        music.setTime(currentTime);
                        music.setSourceUrl(values[4]);
                        music.setDetailUrl(values[5]);
                        
                        uniqueRecords.put(uniqueKey, music);
                    }
                }
            }
        }

        // Check existing records in database and merge with CSV records
        List<AhaMusic> recordsToSave = new ArrayList<>();
        for (AhaMusic csvRecord : uniqueRecords.values()) {
            Optional<AhaMusic> existingRecord = repository.findByTitleAndArtists(
                csvRecord.getTitle(), csvRecord.getArtists());
            
            if (existingRecord.isPresent()) {
                // Record exists - update if CSV record is more recent
                AhaMusic existing = existingRecord.get();
                if (csvRecord.getTime().isAfter(existing.getTime())) {
                    // Update existing record with new data
                    existing.setAcrId(csvRecord.getAcrId());
                    existing.setTime(csvRecord.getTime());
                    existing.setSourceUrl(csvRecord.getSourceUrl());
                    existing.setDetailUrl(csvRecord.getDetailUrl());
                    recordsToSave.add(existing);
                }
                // If existing record is more recent, skip it
            } else {
                // New record - add it
                recordsToSave.add(csvRecord);
            }
        }

        // Save only new or updated records
        if (!recordsToSave.isEmpty()) {
            repository.saveAll(recordsToSave);
        }
    }

    @Transactional
    public CleanupResult cleanupDuplicates() {
        long beforeCount = repository.count();
        
        // Get all records from the database
        List<AhaMusic> allRecords = repository.findAll();
        
        // Create a map to store the most recent record for each title-artist combination
        Map<String, AhaMusic> uniqueRecords = new HashMap<>();
        
        // Process all records to keep only the most recent ones
        for (AhaMusic music : allRecords) {
            String key = music.getTitle() + "|" + music.getArtists();
            if (!uniqueRecords.containsKey(key) || 
                music.getTime().isAfter(uniqueRecords.get(key).getTime())) {
                uniqueRecords.put(key, music);
            }
        }
        
        // Clear all records and save only the unique ones
        repository.deleteAllInBatch();
        repository.saveAll(uniqueRecords.values());
        
        long afterCount = repository.count();
        return new CleanupResult(beforeCount, afterCount);
    }

    public long getUniqueRecordCount() {
        return repository.countUniqueTitleArtistPairs();
    }

    public List<AhaMusic> getAllUniqueRecords() {
        return repository.findAll();
    }

    public static class CleanupResult {
        private final long originalCount;
        private final long newCount;
        private final long removedDuplicates;

        public CleanupResult(long originalCount, long newCount) {
            this.originalCount = originalCount;
            this.newCount = newCount;
            this.removedDuplicates = originalCount - newCount;
        }

        public long getOriginalCount() {
            return originalCount;
        }

        public long getNewCount() {
            return newCount;
        }

        public long getRemovedDuplicates() {
            return removedDuplicates;
        }
    }
} 