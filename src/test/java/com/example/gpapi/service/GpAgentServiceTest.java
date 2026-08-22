package com.example.gpapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpAgentServiceTest {

    @Test
    void encryptsGpPasswordWithFixedKMask() {
        assertEquals("x{x{x{x{", GpAgentService.encryptGpPassword("0000"));
    }

    @Test
    void encryptsNullAsEmptyString() {
        assertEquals("", GpAgentService.encryptGpPassword(null));
    }
}
