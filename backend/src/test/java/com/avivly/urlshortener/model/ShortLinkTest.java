package com.avivly.urlshortener.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkTest {

    @Test
    void isValid_returnsFalse_whenInactive() {
        ShortLink link = ShortLink.builder()
                .originalUrl("https://example.com")
                .isActive(false)
                .build();

        assertThat(link.isValid()).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenExpired() {
        ShortLink link = ShortLink.builder()
                .originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();

        assertThat(link.isValid()).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenClickLimitReached() {
        ShortLink link = ShortLink.builder()
                .originalUrl("https://example.com")
                .maxClicks(5)
                .totalClicks(5)
                .build();

        assertThat(link.isValid()).isFalse();
    }

    @Test
    void isValid_returnsTrue_happyPath() {
        ShortLink link = ShortLink.builder()
                .originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .maxClicks(10)
                .totalClicks(3)
                .build();

        assertThat(link.isValid()).isTrue();
    }
}
