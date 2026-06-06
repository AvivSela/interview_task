package com.avivly.urlshortener.util.strategy;

import com.avivly.urlshortener.util.Base62;

import java.util.List;
import java.util.Map;

public class RandomBase62Strategy implements CodeGenerationStrategy {

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
            "Number of characters to generate", 4, 20)
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        int length = (int) params.getOrDefault("length", 7);
        return Base62.generate(length);
    }
}
