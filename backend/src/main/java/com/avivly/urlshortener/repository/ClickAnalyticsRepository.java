package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClickAnalyticsRepository extends JpaRepository<ClickAnalytics, Long> {

    List<ClickAnalytics> findByShortCodeOrderByClickedAtDesc(String shortCode);

    @Query("SELECT CAST(c.clickedAt AS date) as date, COUNT(c) as count FROM ClickAnalytics c WHERE c.shortCode = :shortCode GROUP BY CAST(c.clickedAt AS date) ORDER BY CAST(c.clickedAt AS date)")
    List<Object[]> countClicksByDay(@Param("shortCode") String shortCode);

    @Query(value = """
            SELECT referer, COUNT(*) AS count
            FROM click_analytics
            WHERE short_code = :shortCode
              AND referer IS NOT NULL
            GROUP BY referer
            ORDER BY count DESC, referer ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topReferrers(@Param("shortCode") String shortCode,
                                 @Param("limit") int limit);

    @Query(value = """
            SELECT user_agent, COUNT(*) AS count
            FROM click_analytics
            WHERE short_code = :shortCode
              AND user_agent IS NOT NULL
            GROUP BY user_agent
            ORDER BY count DESC, user_agent ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topUserAgents(@Param("shortCode") String shortCode,
                                  @Param("limit") int limit);

    @Query(value = """
            SELECT country, COUNT(*) AS clicks
            FROM click_analytics
            WHERE short_code = :shortCode
              AND geo_status = 'RESOLVED'
              AND country IS NOT NULL
            GROUP BY country
            ORDER BY clicks DESC, country ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topCountries(@Param("shortCode") String shortCode,
                                 @Param("limit") int limit);

    @Query(value = """
            SELECT city, country, COUNT(*) AS clicks
            FROM click_analytics
            WHERE short_code = :shortCode
              AND geo_status = 'RESOLVED'
              AND city IS NOT NULL
            GROUP BY city, country
            ORDER BY clicks DESC, city ASC, country ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> topCities(@Param("shortCode") String shortCode,
                              @Param("limit") int limit);
}
