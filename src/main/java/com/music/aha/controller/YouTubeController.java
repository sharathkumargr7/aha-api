package com.music.aha.controller;

import com.music.aha.service.YouTubeService;
import com.music.aha.service.AhaMusicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URLEncoder;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "*")
public class YouTubeController {

    @Autowired
    private YouTubeService youTubeService;
    
    @Autowired
    private AhaMusicService ahaMusicService;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @PostMapping("/create-playlist")
    public ResponseEntity<?> createPlaylist(@RequestHeader("Authorization") String authorization,
                                            @RequestBody List<SongRequest> songs,
                                            @RequestParam(value = "playlistId", required = false) String existingPlaylistId) {
        try {
            // Extract access token from header
            String accessToken = authorization.replace("Bearer ", "");
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

            // Filter songs that exist in database and haven't been added to playlist yet
            List<SongRequest> availableSongs = new ArrayList<>();
            List<SongRequest> alreadyAddedSongs = new ArrayList<>();
            List<SongRequest> notFoundSongs = new ArrayList<>();
            List<com.music.aha.model.AhaMusic> recordsToUpdate = new ArrayList<>();
            
            for (SongRequest song : songs) {
                Optional<com.music.aha.model.AhaMusic> existingRecord = ahaMusicService.findByTitleAndArtists(
                    song.getTitle(), song.getArtists());
                if (existingRecord.isPresent()) {
                    com.music.aha.model.AhaMusic record = existingRecord.get();
                    if (!record.isAddedToPlaylist()) {
                        availableSongs.add(song);
                        recordsToUpdate.add(record);
                    } else {
                        alreadyAddedSongs.add(song);
                    }
                } else {
                    notFoundSongs.add(song);
                }
            }

            if (availableSongs.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("No new songs available to add to playlist.");
                if (!alreadyAddedSongs.isEmpty()) {
                    errorMsg.append(" ").append(alreadyAddedSongs.size()).append(" songs have already been added.");
                }
                if (!notFoundSongs.isEmpty()) {
                    errorMsg.append(" ").append(notFoundSongs.size()).append(" songs were not found in the database.");
                }
                return ResponseEntity.badRequest()
                    .body(Map.of("error", errorMsg.toString()));
            }

            List<YouTubeService.SongInfo> songInfos = new ArrayList<>();
            for (SongRequest song : availableSongs) {
                songInfos.add(new YouTubeService.SongInfo(song.getTitle(), song.getArtists()));
            }

            List<String> videoIds = youTubeService.searchVideos(songInfos, credential);
            if (videoIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No videos found for the available songs"));
            }
            
            // Create the playlist under the authenticated user's account
            String playlistUrl = youTubeService.createPlaylist(videoIds, credential, existingPlaylistId);
            if (playlistUrl == null) {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create or update playlist on YouTube"));
            }
            
            // Mark the songs as added to playlist
            for (com.music.aha.model.AhaMusic record : recordsToUpdate) {
                record.setAddedToPlaylist(true);
                ahaMusicService.save(record);
            }
            
            // Extract playlist ID from URL for caching
            String playlistId = null;
            if (playlistUrl != null && playlistUrl.contains("list=")) {
                playlistId = playlistUrl.substring(playlistUrl.indexOf("list=") + 5);
            }
            
            return ResponseEntity.ok(Map.of(
                "playlistUrl", playlistUrl,
                "playlistId", playlistId,
                "videoCount", videoIds.size(),
                "addedCount", availableSongs.size(),
                "alreadyAddedCount", alreadyAddedSongs.size(),
                "notFoundCount", notFoundSongs.size(),
                "requestedCount", songs.size()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error creating playlist: " + e.getMessage()));
        }
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        String redirectUri = "http://localhost:8080/api/youtube/oauth2callback";
        String scope = "https://www.googleapis.com/auth/youtube";
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + scope
                + "&access_type=offline";
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @GetMapping("/oauth2callback")
    public ResponseEntity<?> oauth2callback(@RequestParam("code") String code) {
        try {
            String redirectUri = "http://localhost:8080/api/youtube/oauth2callback";
            String tokenEndpoint = "https://oauth2.googleapis.com/token";

            // Prepare request body
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

            // Use RestTemplate to send POST request
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            org.springframework.http.HttpEntity<MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(params, headers);

            org.springframework.http.ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenEndpoint,
                org.springframework.http.HttpMethod.POST,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("access_token")) {
                String accessToken = (String) body.get("access_token");
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "http://localhost:4200?access_token=" + accessToken)
                    .build();
            } else {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "http://localhost:4200?error=failed_to_obtain_token")
                    .build();
            }
        } catch (Exception e) {
            String errorMessage;
            try {
                errorMessage = java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
            } catch (java.io.UnsupportedEncodingException ex) {
                errorMessage = "encoding_error";
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "http://localhost:4200?error=" + errorMessage)
                .build();
        }
    }

    public static class SongRequest {
        private String title;
        private String artists;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getArtists() {
            return artists;
        }

        public void setArtists(String artists) {
            this.artists = artists;
        }
    }
}
