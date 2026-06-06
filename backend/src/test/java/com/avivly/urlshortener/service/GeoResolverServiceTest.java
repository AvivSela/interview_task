package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.GeoResult;
import com.avivly.urlshortener.model.GeoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "geo.db.path=src/test/resources/GeoLite2-City-Test.mmdb")
class GeoResolverServiceTest {

    @Autowired
    private GeoResolverService service;

    @Test
    void publicIpResolvesToCountry() {
        GeoResult result = service.resolve("81.2.69.142");
        assertThat(result.status()).isEqualTo(GeoStatus.RESOLVED);
        assertThat(result.country()).isNotBlank();
    }

    @Test
    void privateIpIsPrivate() {
        assertThat(service.resolve("10.0.0.1").status()).isEqualTo(GeoStatus.PRIVATE);
        assertThat(service.resolve("127.0.0.1").status()).isEqualTo(GeoStatus.PRIVATE);
        assertThat(service.resolve("192.168.1.1").status()).isEqualTo(GeoStatus.PRIVATE);
    }

    @Test
    void unknownPublicIpIsNotFound() {
        // 203.0.113.x is TEST-NET-3 (RFC 5737) — not in MaxMind test DB
        GeoResult result = service.resolve("203.0.113.1");
        assertThat(result.status()).isEqualTo(GeoStatus.NOT_FOUND);
    }

    @Test
    void maskHidesLastOctetIPv4() {
        String masked = service.mask("81.2.69.142");
        assertThat(masked).doesNotContain("142");
        assertThat(masked).contains("81.2.69");
    }

    @Test
    void maskHidesLastGroupIPv6() {
        String masked = service.mask("2001:db8::1");
        assertThat(masked).doesNotContain(":1").startsWith("2001");
    }
}
