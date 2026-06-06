package com.avivly.urlshortener.repository;

import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.model.GeoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClickAnalyticsRepositoryTest {

    @Autowired
    private ClickAnalyticsRepository repo;

    @Test
    void topCountriesReturnsOrderedAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("GB").geoStatus(GeoStatus.RESOLVED).build());

        List<Object[]> results = repo.topCountries("x", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)[0]).isEqualTo("US");
        assertThat(((Number) results.get(0)[1]).longValue()).isEqualTo(2);
    }

    @Test
    void topCitiesReturnsOrderedAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").city("New York").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").city("New York").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("GB").city("London").geoStatus(GeoStatus.RESOLVED).build());

        List<Object[]> results = repo.topCities("x", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)[1]).isEqualTo("US");  // country col
        assertThat(results.get(0)[0]).isEqualTo("New York"); // city col
    }

    @Test
    void topCountriesTieBreaksAlphabetically() {
        repo.save(ClickAnalytics.builder().shortCode("x").country("US").geoStatus(GeoStatus.RESOLVED).build());
        repo.save(ClickAnalytics.builder().shortCode("x").country("AU").geoStatus(GeoStatus.RESOLVED).build());

        List<Object[]> results = repo.topCountries("x", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0)[0]).isEqualTo("AU"); // equal count; AU < US
        assertThat(results.get(1)[0]).isEqualTo("US");
    }

    @Test
    void nullCountryCityExcludedFromAggregates() {
        repo.save(ClickAnalytics.builder().shortCode("x").geoStatus(GeoStatus.PRIVATE).build());
        assertThat(repo.topCountries("x", 10)).isEmpty();
        assertThat(repo.topCities("x", 10)).isEmpty();
    }
}
