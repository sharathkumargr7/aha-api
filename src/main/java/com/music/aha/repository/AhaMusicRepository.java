package com.music.aha.repository;

import com.music.aha.model.AhaMusic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AhaMusicRepository extends JpaRepository<AhaMusic, String> {
    
    @Query("SELECT a FROM AhaMusic a WHERE (a.title, a.artists, a.time) IN " +
           "(SELECT m.title, m.artists, MAX(m.time) FROM AhaMusic m GROUP BY m.title, m.artists)")
    List<AhaMusic> findAllUniqueByTitleAndArtists();
    
    @Query("SELECT COUNT(DISTINCT CONCAT(a.title, ':', a.artists)) FROM AhaMusic a")
    long countUniqueTitleArtistPairs();
} 