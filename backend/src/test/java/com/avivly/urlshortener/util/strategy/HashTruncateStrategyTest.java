package com.avivly.urlshortener.util.strategy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HashTruncateStrategyTest {

    private final HashTruncateStrategy strategy = new HashTruncateStrategy();

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
    void generate_isDeterministic() {
        String first = strategy.generate("https://example.com", null, Map.of());
        String second = strategy.generate("https://example.com", null, Map.of());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void generate_withEmptyParams_usesDefaults() {
        String result = strategy.generate("https://example.com", null, Map.of());
        assertThat(result).hasSize(7).matches("[a-zA-Z0-9]+");
    }

    @Test
    void generate_sha512_producesHash() {
        Map<String, Object> params = Map.of("algorithm", "SHA-512", "length", 7);
        String result = strategy.generate("https://example.com", null, params);
        assertThat(result).hasSize(7).matches("[a-zA-Z0-9]+");
    }

    @Test
    void generate_customLength_applied() {
        Map<String, Object> params = Map.of("length", 12);
        assertThat(strategy.generate("https://example.com", null, params)).hasSize(12);
    }
}
