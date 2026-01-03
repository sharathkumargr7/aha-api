package com.music.aha.controller;

import com.music.aha.service.YouTubeService;
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
import java.net.URLEncoder;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "*")
public class YouTubeController {

    @Autowired
    private YouTubeService youTubeService;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @PostMapping("/create-playlist")
    public ResponseEntity<?> createPlaylist(@RequestHeader("Authorization") String authorization,
                                            @RequestBody List<SongRequest> songs) {
        try {
            // Extract access token from header
            String accessToken = authorization.replace("Bearer ", "");
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

            List<YouTubeService.SongInfo> songInfos = new ArrayList<>();
            for (SongRequest song : songs) {
                songInfos.add(new YouTubeService.SongInfo(song.getTitle(), song.getArtists()));
            }

            List<String> videoIds = youTubeService.searchVideos(songInfos, credential);
            if (videoIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No videos found for the provided songs"));
            }
            String playlistUrl = youTubeService.createPlaylistUrl(videoIds);
            return ResponseEntity.ok(Map.of(
                "playlistUrl", playlistUrl,
                "videoCount", videoIds.size(),
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
