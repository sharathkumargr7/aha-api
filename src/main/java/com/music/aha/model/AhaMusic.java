package com.music.aha.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "aha_music", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"title", "artists"}))
public class AhaMusic {
    @Id
    private String acrId;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String artists;
    
    private LocalDateTime time;
    private String sourceUrl;
    private String detailUrl;

    // Default constructor
    public AhaMusic() {}

    // Getters and Setters
    public String getAcrId() {
        return acrId;
    }

    public void setAcrId(String acrId) {
        this.acrId = acrId;
    }

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

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }
} 