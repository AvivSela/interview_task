package com.avivly.urlshortener.util.strategy;

import java.util.List;
import java.util.Map;

public class SequentialStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        StrategyParamDefinition.of("prefix", ParamType.STRING, false, "",
            "Prepended to the encoded ID (e.g. 's-'). Max 16 chars, alphanumeric/hyphen/underscore only")
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        if (id == null) throw new IllegalStateException("SequentialStrategy requires a persisted ID");
        String prefix = (String) params.getOrDefault("prefix", "");
        return prefix + encodeId(id);
    }

    private String encodeId(long id) {
        if (id == 0) return String.valueOf(CHARS.charAt(0));
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            sb.append(CHARS.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
