package com.avivly.urlshortener.util.strategy;

public record StrategyParamDefinition(
    String    name,
    ParamType type,
    boolean   required,
    String    defaultValue,
    String    description,
    Integer   min,
    Integer   max
) {
    public static StrategyParamDefinition of(String name, ParamType type,
                                              boolean required, String defaultValue,
                                              String description) {
        return new StrategyParamDefinition(name, type, required, defaultValue, description, null, null);
    }
}
