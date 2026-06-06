package com.avivly.urlshortener.util.strategy;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StrategyRegistry {

    private final StrategyParamValidator validator;
    private final Map<StrategyType, CodeGenerationStrategy> strategies;

    public StrategyRegistry(StrategyParamValidator validator) {
        this.validator = validator;
        this.strategies = new EnumMap<>(StrategyType.class);
        strategies.put(StrategyType.RANDOM_BASE62, new RandomBase62Strategy());
        strategies.put(StrategyType.HASH_TRUNCATE, new HashTruncateStrategy());
        strategies.put(StrategyType.SEQUENTIAL,    new SequentialStrategy());
    }

    public String validateAndGenerate(StrategyType type, String url, Long id,
                                       Map<String, Object> rawParams) {
        CodeGenerationStrategy strategy = strategies.getOrDefault(
            type, strategies.get(StrategyType.RANDOM_BASE62));
        Map<String, Object> params = validator.validate(strategy.paramSchema(), rawParams);
        return strategy.generate(url, id, params);
    }

    public List<StrategyParamDefinition> getSchema(StrategyType type) {
        return strategies.get(type).paramSchema();
    }

    public Map<StrategyType, List<StrategyParamDefinition>> getAllSchemas() {
        Map<StrategyType, List<StrategyParamDefinition>> result = new EnumMap<>(StrategyType.class);
        strategies.forEach((type, s) -> result.put(type, s.paramSchema()));
        return result;
    }
}
