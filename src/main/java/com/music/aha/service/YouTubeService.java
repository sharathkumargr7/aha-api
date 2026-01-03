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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class YouTubeService {

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

    private final Environment environment;
    // Simple cache for video ID lookups to avoid repeated API calls
    private final ConcurrentHashMap<String, String> videoIdCache = new ConcurrentHashMap<>();
    
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
        String cacheKey = (title + "|" + artists).toLowerCase();
        
        // Check cache first
        String cachedVideoId = videoIdCache.get(cacheKey);
        if (cachedVideoId != null) {
            return cachedVideoId;
        }
        
        // Create a more specific search query for music videos
        String query = title + " " + artists + " official music video";
        try {
            YouTube youtube = getYouTubeService(credential);
            YouTube.Search.List search = youtube.search().list(Arrays.asList("id", "snippet"));
            search.setQ(query);
            search.setType(Arrays.asList("video"));
            search.setMaxResults(5L); // Get more results to choose from
            search.setFields("items(id/videoId,snippet(title,channelTitle,description))");
            search.setVideoCategoryId("10"); // Music category
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResults = searchResponse.getItems();
            
            if (searchResults != null && !searchResults.isEmpty()) {
                // Find the best match - prefer official channels and music videos
                for (SearchResult result : searchResults) {
                    String resultTitle = result.getSnippet().getTitle().toLowerCase();
                    String channelTitle = result.getSnippet().getChannelTitle().toLowerCase();
                    String description = result.getSnippet().getDescription() != null ? 
                        result.getSnippet().getDescription().toLowerCase() : "";
                    
                    // Prioritize results that look like official music videos
                    boolean isOfficial = channelTitle.contains("official") || 
                                       channelTitle.contains("vevo") ||
                                       resultTitle.contains("official") ||
                                       description.contains("official");
                    
                    if (isOfficial) {
                        String videoId = result.getId().getVideoId();
                        videoIdCache.put(cacheKey, videoId); // Cache the result
                        return videoId;
                    }
                }
                // If no official video found, return the first result
                String videoId = searchResults.get(0).getId().getVideoId();
                videoIdCache.put(cacheKey, videoId); // Cache the result
                return videoId;
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
        
        // Adaptive delay: shorter for small batches, longer for large ones
        int delayMs = songs.size() > 20 ? 200 : 50;
        
        for (SongInfo song : songs) {
            String videoId = searchVideo(song.getTitle(), song.getArtists(), credential);
            if (videoId != null) {
                videoIds.add(videoId);
            }
            
            // Add delay to respect rate limits (only if not the last song)
            if (songs.indexOf(song) < songs.size() - 1) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
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

    /**
     * Create a playlist under the authenticated user's account and add the provided videos.
     * If a playlist named "aha-music" already exists, add videos to it instead of creating a new one.
     * If existingPlaylistId is provided, use it directly. Otherwise, search for "aha-music" playlist.
     * Returns the playlist URL (https://www.youtube.com/playlist?list=PLAYLIST_ID) or null on error.
     */
    public String createPlaylist(List<String> videoIds, Credential credential) {
        return createPlaylist(videoIds, credential, null);
    }

    /**
     * Create a playlist under the authenticated user's account and add the provided videos.
     * If existingPlaylistId is provided, use it directly. Otherwise, search for "aha-music" playlist.
     * Returns the playlist URL (https://www.youtube.com/playlist?list=PLAYLIST_ID) or null on error.
     */
    public String createPlaylist(List<String> videoIds, Credential credential, String existingPlaylistId) {
        if (videoIds == null || videoIds.isEmpty()) {
            return null;
        }
        try {
            YouTube youtube = getYouTubeService(credential);

            String playlistId = existingPlaylistId;

            // If no existing playlist ID provided, try to find an existing playlist named "aha-music"
            if (playlistId == null) {
                try {
                    YouTube.Playlists.List listRequest = youtube.playlists().list(Arrays.asList("id", "snippet"));
                    listRequest.setMine(true);
                    listRequest.setMaxResults(50L); // Check up to 50 playlists

                    com.google.api.services.youtube.model.PlaylistListResponse playlistResponse = listRequest.execute();
                    List<com.google.api.services.youtube.model.Playlist> playlists = playlistResponse.getItems();

                    if (playlists != null) {
                        for (com.google.api.services.youtube.model.Playlist playlist : playlists) {
                            if ("aha-music".equals(playlist.getSnippet().getTitle())) {
                                playlistId = playlist.getId();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error searching for existing playlist: " + e.getMessage());
                    // Continue to create new playlist if search fails
                }
            }

            // If no existing playlist found, create a new one
            if (playlistId == null) {
                com.google.api.services.youtube.model.Playlist playlist = new com.google.api.services.youtube.model.Playlist();
                com.google.api.services.youtube.model.PlaylistSnippet snippet = new com.google.api.services.youtube.model.PlaylistSnippet();
                snippet.setTitle("aha-music");
                snippet.setDescription("Playlist created by Aha Music");
                playlist.setSnippet(snippet);

                com.google.api.services.youtube.model.PlaylistStatus status = new com.google.api.services.youtube.model.PlaylistStatus();
                status.setPrivacyStatus("private");
                playlist.setStatus(status);

                // Insert the playlist
                YouTube.Playlists.Insert insertRequest = youtube.playlists().insert(Arrays.asList("snippet","status"), playlist);
                com.google.api.services.youtube.model.Playlist inserted = insertRequest.execute();
                playlistId = inserted.getId();
            }

            // Add each video to the playlist (skip if already exists)
            int addedCount = 0;
            for (String videoId : videoIds) {
                try {
                    // Check if video is already in playlist
                    boolean alreadyInPlaylist = false;
                    try {
                        YouTube.PlaylistItems.List itemsList = youtube.playlistItems().list(Arrays.asList("id", "contentDetails"));
                        itemsList.setPlaylistId(playlistId);
                        itemsList.setMaxResults(50L); // Check recent items

                        com.google.api.services.youtube.model.PlaylistItemListResponse itemsResponse = itemsList.execute();
                        List<com.google.api.services.youtube.model.PlaylistItem> items = itemsResponse.getItems();

                        if (items != null) {
                            for (com.google.api.services.youtube.model.PlaylistItem item : items) {
                                if (videoId.equals(item.getContentDetails().getVideoId())) {
                                    alreadyInPlaylist = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // If we can't check, assume it's not there and try to add
                    }

                    if (!alreadyInPlaylist) {
                        com.google.api.services.youtube.model.PlaylistItem playlistItem = new com.google.api.services.youtube.model.PlaylistItem();
                        com.google.api.services.youtube.model.PlaylistItemSnippet itemSnippet = new com.google.api.services.youtube.model.PlaylistItemSnippet();
                        itemSnippet.setPlaylistId(playlistId);

                        com.google.api.services.youtube.model.ResourceId resourceId = new com.google.api.services.youtube.model.ResourceId();
                        resourceId.setKind("youtube#video");
                        resourceId.setVideoId(videoId);
                        itemSnippet.setResourceId(resourceId);

                        playlistItem.setSnippet(itemSnippet);

                        YouTube.PlaylistItems.Insert itemInsert = youtube.playlistItems().insert(Arrays.asList("snippet"), playlistItem);
                        itemInsert.execute();
                        addedCount++;

                        // Small delay to be kind to API quota
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error adding video " + videoId + " to playlist: " + e.getMessage());
                    // Continue with other videos
                }
            }

            // Return the playlist URL
            return "https://www.youtube.com/playlist?list=" + playlistId;
        } catch (Exception e) {
            System.err.println("Error creating/updating playlist: " + e.getMessage());
            return null;
        }
    } 
}
