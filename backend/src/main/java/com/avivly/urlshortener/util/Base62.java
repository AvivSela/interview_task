package com.avivly.urlshortener.util;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Base62 {

    private static final String CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        return IntStream.range(0, length)
            .mapToObj(i -> String.valueOf(CHARS.charAt(RANDOM.nextInt(62))))
            .collect(Collectors.joining());
    }
}
