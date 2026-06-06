package com.avivly.urlshortener.util.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

public class HashTruncateStrategy implements CodeGenerationStrategy {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final List<StrategyParamDefinition> SCHEMA = List.of(
        new StrategyParamDefinition("length", ParamType.INTEGER, false, "7",
            "Characters to take from hash output", 4, 20),
        StrategyParamDefinition.of("algorithm", ParamType.STRING, false, "SHA-256",
            "Hash algorithm: SHA-256 or SHA-512")
    );

    @Override
    public List<StrategyParamDefinition> paramSchema() { return SCHEMA; }

    @Override
    public String generate(String originalUrl, Long id, Map<String, Object> params) {
        String algorithm = (String) params.getOrDefault("algorithm", "SHA-256");
        int length = (int) params.getOrDefault("length", 7);
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int index = (hash[i] & 0xFF) % 62;
                sb.append(CHARS.charAt(index));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(algorithm + " not available", e);
        }
    }
}
