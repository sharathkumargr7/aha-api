package com.music.aha.controller;

import com.music.aha.service.YouTubeService;
import com.music.aha.service.AhaMusicService;
import com.music.aha.service.JwtUtils;
import com.music.aha.service.RefreshTokenService;
import com.music.aha.model.User;
import com.music.aha.model.RefreshToken;
import com.music.aha.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeController {

    @Autowired
    private YouTubeService youTubeService;
    
    @Autowired
    private AhaMusicService ahaMusicService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @PostMapping("/create-playlist")
    public ResponseEntity<?> createPlaylist(@RequestHeader("X-YouTube-Token") String youtubeToken,
                                            @RequestBody List<SongRequest> songs,
                                            @RequestParam(value = "playlistId", required = false) String existingPlaylistId) {
        try {
            // Use YouTube access token from custom header
            String accessToken = youtubeToken;
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
        String scope = "https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/userinfo.email";
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + scope
                + "&access_type=offline";
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @GetMapping("/oauth2callback")
    public ResponseEntity<?> oauth2callback(@RequestParam("code") String code, HttpServletResponse httpResponse) {
        try {
            String redirectUri = "http://localhost:8080/api/youtube/oauth2callback";
            String tokenEndpoint = "https://oauth2.googleapis.com/token";

            // Exchange code for tokens
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

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
            Map<String, Object> tokenBody = response.getBody();
            if (tokenBody == null || !tokenBody.containsKey("access_token")) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "http://localhost:4200/oauth2callback?error=failed_to_obtain_token")
                    .build();
            }

            String youtubeAccessToken = (String) tokenBody.get("access_token");

            // Fetch user info from Google
            org.springframework.http.HttpHeaders userInfoHeaders = new org.springframework.http.HttpHeaders();
            userInfoHeaders.set("Authorization", "Bearer " + youtubeAccessToken);
            org.springframework.http.HttpEntity<Void> userInfoRequest = new org.springframework.http.HttpEntity<>(userInfoHeaders);
            
            org.springframework.http.ResponseEntity<Map<String, Object>> userInfoResponse = restTemplate.exchange(
                "https://www.googleapis.com/oauth2/v2/userinfo",
                org.springframework.http.HttpMethod.GET,
                userInfoRequest,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> userInfo = userInfoResponse.getBody();
            if (userInfo == null || !userInfo.containsKey("email")) {
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "http://localhost:4200/oauth2callback?error=failed_to_get_user_info")
                    .build();
            }

            String email = (String) userInfo.get("email");
            String userId = (String) userInfo.get("id");
            String username = "google_" + userId; // Use Google ID as username

            // Find or create user in database
            User user = userRepository.findByUsername(username).orElseGet(() -> {
                User newUser = new User();
                newUser.setUsername(username);
                // Set a random password (won't be used for Google OAuth users)
                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                newUser.setRoles("ROLE_USER");
                return userRepository.save(newUser);
            });

            // Generate backend access token
            String backendAccessToken = jwtUtils.generateAccessToken(user.getUsername());
            
            // Create refresh token
            RefreshToken refreshToken = refreshTokenService.createFor(user.getUsername());
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken.getToken())
                .httpOnly(true).secure(false).path("/")
                .maxAge(Duration.ofSeconds(Long.parseLong(System.getProperty("refresh.expirationSeconds", "1209600"))))
                .sameSite("Lax").build();
            httpResponse.addHeader("Set-Cookie", refreshCookie.toString());

            // Redirect to frontend with both tokens
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "http://localhost:4200/oauth2callback?access_token=" + backendAccessToken + "&youtube_token=" + youtubeAccessToken)
                .build();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage;
            try {
                errorMessage = java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
            } catch (java.io.UnsupportedEncodingException ex) {
                errorMessage = "encoding_error";
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "http://localhost:4200/oauth2callback?error=" + errorMessage)
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
