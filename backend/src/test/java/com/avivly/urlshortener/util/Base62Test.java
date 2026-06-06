package com.avivly.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base62Test {

    @Test
    void generate_returnsCorrectLength() {
        assertThat(Base62.generate(7)).hasSize(7);
        assertThat(Base62.generate(1)).hasSize(1);
        assertThat(Base62.generate(20)).hasSize(20);
    }

    @Test
    void generate_containsOnlyBase62Chars() {
        String result = Base62.generate(100);
        assertThat(result).matches("[a-zA-Z0-9]+");
    }
}
