package com.avivly.urlshortener.util.strategy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RandomBase62StrategyTest {

    private final RandomBase62Strategy strategy = new RandomBase62Strategy();

    @Test
    void generate_isNotNull() {
        assertThat(strategy.generate("https://example.com", null, Map.of())).isNotNull();
    }

    @Test
    void generate_hasCorrectLength() {
        assertThat(strategy.generate("https://example.com", null, Map.of())).hasSize(7);
    }

    @Test
    void generate_containsOnlyBase62Chars() {
        assertThat(strategy.generate("https://example.com", null, Map.of())).matches("[a-zA-Z0-9]+");
    }

    @Test
    void generate_withEmptyParams_usesDefaultLength() {
        assertThat(strategy.generate("https://example.com", null, Map.of())).hasSize(7);
    }

    @Test
    void generate_customLength_returnsCorrectLength() {
        Map<String, Object> params = Map.of("length", 10);
        assertThat(strategy.generate("https://example.com", null, params)).hasSize(10);
    }
}
