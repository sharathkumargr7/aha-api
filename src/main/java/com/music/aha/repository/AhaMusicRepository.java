package com.music.aha.repository;

import com.music.aha.model.AhaMusic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AhaMusicRepository extends JpaRepository<AhaMusic, String> {
    
    @Query("SELECT a FROM AhaMusic a WHERE (a.title, a.artists, a.time) IN " +
           "(SELECT m.title, m.artists, MAX(m.time) FROM AhaMusic m GROUP BY m.title, m.artists)")
    List<AhaMusic> findAllUniqueByTitleAndArtists();
    
    @Query("SELECT a FROM AhaMusic a WHERE (a.title, a.artists, a.time) IN " +
           "(SELECT m.title, m.artists, MAX(m.time) FROM AhaMusic m GROUP BY m.title, m.artists)")
    Page<AhaMusic> findAllUniqueByTitleAndArtists(Pageable pageable);
    
    @Query("SELECT COUNT(DISTINCT CONCAT(a.title, ':', a.artists)) FROM AhaMusic a")
    long countUniqueTitleArtistPairs();
    
    Optional<AhaMusic> findByTitleAndArtists(String title, String artists);
    
    List<AhaMusic> findByTitleAndArtistsAndAddedToPlaylist(String title, String artists, boolean addedToPlaylist);
    
    List<AhaMusic> findByAddedToPlaylist(boolean addedToPlaylist);
} 