package com.example.gpapi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @Test
    void masksOnlyLastFourDigitsAndPreservesSeparators() {
        assertThat(MaskingUtils.maskAccount("001-11-3333333"))
                .isEqualTo("001-11-333****");
    }

    @Test
    void masksAllDigitsWhenAccountHasFewerThanFour() {
        assertThat(MaskingUtils.maskAccount("A-12"))
                .isEqualTo("A-**");
    }

    @Test
    void handlesEmptyValues() {
        assertThat(MaskingUtils.maskAccount(null)).isEmpty();
        assertThat(MaskingUtils.maskAccount("")).isEmpty();
    }
}
