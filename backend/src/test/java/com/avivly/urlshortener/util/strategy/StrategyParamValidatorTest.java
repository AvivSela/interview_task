package com.avivly.urlshortener.util.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class StrategyParamValidatorTest {

    private StrategyParamValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrategyParamValidator();
    }

    // 1. Unknown key → 400 with "Unknown"
    @Test
    void unknownKey_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("length", ParamType.INTEGER, false, "8", "Code length")
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("bogus", 5)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown");
    }

    // 2. Required param absent → 400 with "missing"
    @Test
    void requiredParamMissing_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, true, null, "Code length", null, null)
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("missing");
    }

    // 3. INTEGER param receives a non-numeric string → 400 with "integer"
    @Test
    void integerParam_invalidString_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("length", ParamType.INTEGER, false, null, "Code length")
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("length", "abc")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("integer");
    }

    // 4. INTEGER param below min → 400 with ">= 4"
    @Test
    void integerParam_belowMin_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, false, null, "Code length", 4, null)
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("length", "3")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining(">= 4");
    }

    // 5. INTEGER param above max → 400 with "<= 20"
    @Test
    void integerParam_aboveMax_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, false, null, "Code length", null, 20)
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("length", "21")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("<= 20");
    }

    // 6. INTEGER param at exact bounds → passes and coerces to int
    @Test
    void integerParam_atBounds_passes() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, false, null, "Code length", 4, 20)
        );

        Map<String, Object> resultMin = validator.validate(schema, Map.of("length", "4"));
        assertThat(resultMin).containsEntry("length", 4);

        Map<String, Object> resultMax = validator.validate(schema, Map.of("length", "20"));
        assertThat(resultMax).containsEntry("length", 20);
    }

    // 7. algorithm param with unsupported value → 400 with "Unsupported algorithm"
    @Test
    void algorithmParam_invalidValue_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("algorithm", ParamType.STRING, false, null, "Hash algorithm")
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("algorithm", "MD5")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported algorithm");
    }

    // 8. algorithm param with allowed values → passes
    @Test
    void algorithmParam_allowedValues_pass() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("algorithm", ParamType.STRING, false, null, "Hash algorithm")
        );

        assertThatNoException().isThrownBy(
            () -> validator.validate(schema, Map.of("algorithm", "SHA-256")));

        assertThatNoException().isThrownBy(
            () -> validator.validate(schema, Map.of("algorithm", "SHA-512")));
    }

    // 9. prefix param with invalid chars → 400 with "prefix"
    @Test
    void prefixParam_invalidChars_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("prefix", ParamType.STRING, false, null, "Short-link prefix")
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("prefix", "../etc")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("prefix");
    }

    // 10. prefix param too long (17 chars) → 400 with "prefix"
    @Test
    void prefixParam_tooLong_returns400() {
        List<StrategyParamDefinition> schema = List.of(
            StrategyParamDefinition.of("prefix", ParamType.STRING, false, null, "Short-link prefix")
        );

        assertThatThrownBy(() -> validator.validate(schema, Map.of("prefix", "aaaaaaaaaaaaaaaaa")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("prefix");
    }

    // 11. null rawParams → defaults used, result has coerced integer
    @Test
    void nullParams_usesDefaults_passes() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, false, "7", "Code length", null, null)
        );

        Map<String, Object> result = validator.validate(schema, null);
        assertThat(result).containsEntry("length", 7);
    }

    // 12. empty rawParams → defaults used, result has coerced integer
    @Test
    void emptyParams_usesDefaults_passes() {
        List<StrategyParamDefinition> schema = List.of(
            new StrategyParamDefinition("length", ParamType.INTEGER, false, "7", "Code length", null, null)
        );

        Map<String, Object> result = validator.validate(schema, Map.of());
        assertThat(result).containsEntry("length", 7);
    }
}
