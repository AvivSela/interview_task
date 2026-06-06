package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortLink s SET s.totalClicks = s.totalClicks + 1 WHERE s.shortCode = :shortCode")
    void incrementClicks(@Param("shortCode") String shortCode);
}
