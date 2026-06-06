package com.avivly.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StrategyControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void GET_api_strategies_returns200_withAllStrategies() {
        ResponseEntity<Map<String, List<Object>>> response = restTemplate.exchange(
            url("/api/strategies"),
            org.springframework.http.HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, List<Object>>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, List<Object>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("RANDOM_BASE62");
        assertThat(body).containsKey("HASH_TRUNCATE");
        assertThat(body).containsKey("SEQUENTIAL");
        assertThat(body.get("RANDOM_BASE62")).isNotEmpty();
        assertThat(body.get("HASH_TRUNCATE")).isNotEmpty();
        assertThat(body.get("SEQUENTIAL")).isNotEmpty();
    }

    @Test
    void GET_api_strategies_doesNotExposeDefaultValueOrMinOrMax() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            url("/api/strategies"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("defaultValue");
        assertThat(body).doesNotContain("\"min\"");
        assertThat(body).doesNotContain("\"max\"");
    }
}
