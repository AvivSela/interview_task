package com.avivly.urlshortener;

import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.repository.ClickAnalyticsRepository;
import com.avivly.urlshortener.repository.ShortLinkRepository;
import com.avivly.urlshortener.service.LinkService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RedirectIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LinkService linkService;

    @Autowired
    private ClickAnalyticsRepository clickAnalyticsRepo;

    @Autowired
    private ShortLinkRepository shortLinkRepo;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void happyPath_redirectsToOriginalUrl() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/happy", null, null, null, null, null, null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
            .hasToString("https://example.com/happy");
    }

    @Test
    void expiredLink_redirectsToLinkExpired() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/expired", null, null, null, null,
            LocalDateTime.now().minusSeconds(1), null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/link-expired");
    }

    @Test
    void clickExhausted_secondRequestRedirectsToLinkExpired() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/limited", null, null, null, 1, null, null));

        restTemplate.getForEntity(url("/" + link.getShortCode()), Void.class);

        ResponseEntity<Void> second = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(second.getHeaders().getLocation()).hasToString("/link-expired");
    }

    @Test
    void clickExhausted_exactLimitAllowedThenBlocked() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/limited3", null, null, null, 3, null, null));

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> response = restTemplate.getForEntity(
                url("/" + link.getShortCode()), Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        ResponseEntity<Void> overLimit = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(overLimit.getHeaders().getLocation()).hasToString("/link-expired");
    }

    @Test
    void unknownCode_returns404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/nonexistent999"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sequential_shortCodeIsBase62EncodingOfId() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/seq1", null, "SEQUENTIAL", null, null, null, null));

        assertThat(link.getShortCode()).isNotNull();
        assertThat(link.getShortCode()).matches("^[a-zA-Z0-9]+$");
        assertThat(link.getShortCode().length()).isLessThanOrEqualTo(7);
        assertThat(link.getId()).isNotNull();
    }

    @Test
    void sequential_twoLinksGetDifferentCodes() {
        ShortLink first = linkService.create(new CreateLinkRequest(
            "https://example.com/seq2a", null, "SEQUENTIAL", null, null, null, null));
        ShortLink second = linkService.create(new CreateLinkRequest(
            "https://example.com/seq2b", null, "SEQUENTIAL", null, null, null, null));

        assertThat(first.getShortCode()).isNotEqualTo(second.getShortCode());
    }

    @Test
    void sequential_redirectWorks() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/seq3", null, "SEQUENTIAL", null, null, null, null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
            .hasToString("https://example.com/seq3");
    }

    @Test
    void redirect_persistsClickAnalyticsRow() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/track1", null, null, null, null, null, null));

        restTemplate.getForEntity(url("/" + link.getShortCode()), Void.class);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .until(() -> !clickAnalyticsRepo
                .findByShortCodeOrderByClickedAtDesc(link.getShortCode()).isEmpty());

        List<ClickAnalytics> rows =
            clickAnalyticsRepo.findByShortCodeOrderByClickedAtDesc(link.getShortCode());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getShortCode()).isEqualTo(link.getShortCode());
    }

    @Test
    void redirect_incrementsTotalClicks() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/track2", null, null, null, null, null, null));

        restTemplate.getForEntity(url("/" + link.getShortCode()), Void.class);

        ShortLink updated = shortLinkRepo.findByShortCode(link.getShortCode()).orElseThrow();
        assertThat(updated.getTotalClicks()).isEqualTo(1);
    }

    @Test
    void inactiveLink_redirectsToLinkExpired() {
        ShortLink link = linkService.create(new CreateLinkRequest(
            "https://example.com/inactive", null, null, null, null, null, null));
        linkService.update(link.getId(),
            new UpdateLinkRequest(null, false, null, null, null));

        ResponseEntity<Void> response = restTemplate.getForEntity(
            url("/" + link.getShortCode()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/link-expired");
    }
}
