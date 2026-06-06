package com.avivly.urlshortener.util.strategy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequentialStrategyTest {

    private final SequentialStrategy strategy = new SequentialStrategy();

    private String encode(long id) {
        return strategy.generate("https://example.com", id, Map.of());
    }

    @Test
    void encodeId_zero_returnsFirstChar() {
        assertThat(encode(0)).isEqualTo("a");
    }

    @Test
    void encodeId_one_returnsSecondChar() {
        assertThat(encode(1)).isEqualTo("b");
    }

    @Test
    void encodeId_61_returnsLastChar() {
        // CHARS[61] = '9' (last char of "...0123456789")
        assertThat(encode(61)).isEqualTo("9");
    }

    @Test
    void encodeId_62_rollsOver() {
        // 62 = 1*62 + 0 → "ba"
        assertThat(encode(62)).isEqualTo("ba");
    }

    @Test
    void generate_nullId_throwsIllegalState() {
        assertThatThrownBy(() -> strategy.generate("https://example.com", null, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generate_withEmptyParams_usesDefaultPrefix() {
        String result = strategy.generate("https://example.com", 1L, Map.of());
        assertThat(result).doesNotContain("-");
    }

    @Test
    void generate_withPrefix_prependsCorrectly() {
        Map<String, Object> params = Map.of("prefix", "s-");
        String result = strategy.generate("https://example.com", 1L, params);
        assertThat(result).startsWith("s-");
    }
}
