package com.avivly.urlshortener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.avivly.urlshortener.dto.AnalyticsResponse;
import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.repository.ClickAnalyticsRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LinkControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClickAnalyticsRepository clickRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void createLink_returns201WithBody() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/ctrl-create", null, null, null, null, null, null);

        ResponseEntity<ShortLink> response = restTemplate.postForEntity(
            url("/api/links"), req, ShortLink.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ShortLink body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getOriginalUrl()).isEqualTo("https://example.com/ctrl-create");
        assertThat(body.getShortCode()).isNotBlank();
        assertThat(body.getId()).isNotNull();
    }

    @Test
    void getAll_includesCreatedLink() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/ctrl-getall", null, null, null, null, null, null);
        restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);

        ResponseEntity<List<ShortLink>> response = restTemplate.exchange(
            url("/api/links"), HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .extracting(ShortLink::getOriginalUrl)
            .contains("https://example.com/ctrl-getall");
    }

    @Test
    void updateLink_persistsNewOriginalUrl() {
        CreateLinkRequest create = new CreateLinkRequest(
            "https://example.com/ctrl-before", null, null, null, null, null, null);
        ShortLink created = restTemplate.postForEntity(url("/api/links"), create, ShortLink.class).getBody();

        UpdateLinkRequest update = new UpdateLinkRequest(
            "https://example.com/ctrl-after", null, null, null, null);
        HttpEntity<UpdateLinkRequest> entity = new HttpEntity<>(update);

        ResponseEntity<ShortLink> response = restTemplate.exchange(
            url("/api/links/" + created.getId()), HttpMethod.PUT, entity, ShortLink.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getOriginalUrl()).isEqualTo("https://example.com/ctrl-after");
    }

    @Test
    void deleteLink_returns204_andSubsequentRedirectReturns404() {
        CreateLinkRequest create = new CreateLinkRequest(
            "https://example.com/ctrl-delete", null, null, null, null, null, null);
        ShortLink created = restTemplate.postForEntity(url("/api/links"), create, ShortLink.class).getBody();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            url("/api/links/" + created.getId()), HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> redirectResponse = restTemplate.getForEntity(
            url("/" + created.getShortCode()), Void.class);
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void customAliasConflict_secondCreateReturns409() {
        CreateLinkRequest first = new CreateLinkRequest(
            "https://example.com/alias-a", "ctrl-conflict-alias", null, null, null, null, null);
        CreateLinkRequest second = new CreateLinkRequest(
            "https://example.com/alias-b", "ctrl-conflict-alias", null, null, null, null, null);

        ResponseEntity<ShortLink> firstResponse = restTemplate.postForEntity(
            url("/api/links"), first, ShortLink.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
            url("/api/links"), second, String.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void analytics_afterOneRedirect_returnsTotalClicksOneAndClicksOverTimeEntry() throws Exception {
        CreateLinkRequest create = new CreateLinkRequest(
            "https://example.com/ctrl-analytics", null, null, null, null, null, null);
        ShortLink created = restTemplate.postForEntity(url("/api/links"), create, ShortLink.class).getBody();
        String code = created.getShortCode();

        restTemplate.getForEntity(url("/" + code), Void.class);

        // Wait for async logClickAsync to persist the ClickAnalytics record
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() ->
            !clickRepo.findByShortCodeOrderByClickedAtDesc(code).isEmpty()
        );

        String json = restTemplate.getForObject(
            url("/api/links/" + code + "/analytics"), String.class);
        AnalyticsResponse analytics = objectMapper.readValue(json, AnalyticsResponse.class);

        assertThat(analytics.totalClicks()).isEqualTo(1);
        assertThat(analytics.clicksOverTime()).isNotNull().isNotEmpty();
    }

    @Test
    void createLink_withValidStrategyParams_returns201() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-valid", null, "RANDOM_BASE62",
            Map.of("length", 10), null, null, null);
        ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getShortCode()).hasSize(10);
    }

    @Test
    void createLink_withUnknownParamKey_returns400() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-unknown", null, "RANDOM_BASE62",
            Map.of("bogusKey", 5), null, null, null);
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createLink_withOutOfRangeLength_returns400() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-range", null, "RANDOM_BASE62",
            Map.of("length", 99), null, null, null);
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createLink_withInvalidAlgorithm_returns400() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-algo", null, "HASH_TRUNCATE",
            Map.of("algorithm", "MD5"), null, null, null);
        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/links"), req, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createLink_withNullStrategyParams_usesDefaults() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-null", null, "RANDOM_BASE62",
            null, null, null, null);
        ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getShortCode()).hasSize(7);
    }

    @Test
    void createLink_strategyParams_integerDeserializedCorrectly() {
        CreateLinkRequest req = new CreateLinkRequest(
            "https://example.com/params-jackson", null, "RANDOM_BASE62",
            Map.of("length", 8), null, null, null);
        ResponseEntity<ShortLink> response = restTemplate.postForEntity(url("/api/links"), req, ShortLink.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getShortCode()).hasSize(8);
    }
}
