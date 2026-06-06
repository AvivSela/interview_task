package com.avivly.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "geo.db.path=")
class GeoDisabledIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void appStartsWithoutMmdbAndRedirectStillWorks() {
        ResponseEntity<Void> res = rest.getForEntity("/api/r/nonexistent", Void.class);
        // unknown code redirects to /link-expired (302), never 500
        assertThat(res.getStatusCode().value()).isIn(302, 404);
    }

    @Test
    void healthShowsGeoResolverDegraded() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("\"geoResolver\"");
        assertThat(res.getBody()).contains("\"status\":\"DEGRADED\"");
    }
}
