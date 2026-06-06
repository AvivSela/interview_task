package com.avivly.urlshortener.util.strategy;

import java.util.List;
import java.util.Map;

public interface CodeGenerationStrategy {

    /**
     * @param originalUrl the URL being shortened
     * @param id          the persisted entity ID; null for strategies that don't use it
     * @param params      validated, coerced param map (never null, may be empty)
     */
    String generate(String originalUrl, Long id, Map<String, Object> params);

    List<StrategyParamDefinition> paramSchema();
}
