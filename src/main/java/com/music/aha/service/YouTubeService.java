package com.music.aha.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class YouTubeService {

    private final Environment environment;
    
    public YouTubeService(Environment environment) {
        this.environment = environment;
    }
    
    @PostConstruct
    public void init() {
        // If apiKey is empty, try to get it from environment or system properties
        String apiKey = environment.getProperty("youtube.api.key");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = environment.getProperty("YOUTUBE_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getProperty("youtube.api.key");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("YOUTUBE_API_KEY");
        }
        
        if (apiKey != null && !apiKey.isEmpty()) {
            System.out.println("YouTubeService: API key loaded successfully (length: " + apiKey.length() + ")");
        } else {
            System.err.println("YouTubeService: WARNING - API key is still not configured!");
        }
    }

    /**
     * Get YouTube service using user's OAuth2 credential
     */
    private YouTube getYouTubeService(Credential credential) {
        return new YouTube.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("aha-music").build();
    }

    /**
     * Search for a video on YouTube based on song title and artists using user's access token
     * @param title Song title
     * @param artists Song artists
     * @param credential User's OAuth2 credential
     * @return YouTube video ID if found, null otherwise
     */
    public String searchVideo(String title, String artists, Credential credential) {
        String query = title + " " + artists;
        try {
            YouTube youtube = getYouTubeService(credential);
            YouTube.Search.List search = youtube.search().list(Arrays.asList("id", "snippet"));
            search.setQ(query);
            search.setType(Arrays.asList("video"));
            search.setMaxResults(1L);
            search.setFields("items(id/videoId)");
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResults = searchResponse.getItems();
            if (searchResults != null && !searchResults.isEmpty()) {
                return searchResults.get(0).getId().getVideoId();
            }
        } catch (Exception e) {
            System.err.println("Error searching YouTube for: " + query);
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Search for multiple videos and return their IDs using user's access token
     * @param songs List of songs with title and artists
     * @param credential User's OAuth2 credential
     * @return List of video IDs
     */
    public List<String> searchVideos(List<SongInfo> songs, Credential credential) {
        List<String> videoIds = new ArrayList<>();
        for (SongInfo song : songs) {
            String videoId = searchVideo(song.getTitle(), song.getArtists(), credential);
            if (videoId != null) {
                videoIds.add(videoId);
            }
            // Add small delay to respect rate limits
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return videoIds;
    }

    /**
     * Create a YouTube playlist URL from video IDs
     * This URL will open YouTube and allow the user to create/save the playlist
     */
    public String createPlaylistUrl(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return null;
        }
        // YouTube playlist URL format: https://www.youtube.com/watch_videos?video_ids=VIDEO_ID_1,VIDEO_ID_2,...
        String videoIdsParam = String.join(",", videoIds);
        return "https://www.youtube.com/watch_videos?video_ids=" + videoIdsParam;
    }

    public static class SongInfo {
        private String title;
        private String artists;

        public SongInfo(String title, String artists) {
            this.title = title;
            this.artists = artists;
        }

        public String getTitle() {
            return title;
        }

        public String getArtists() {
            return artists;
        }
    }
}
