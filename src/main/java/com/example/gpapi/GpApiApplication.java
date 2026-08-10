package com.example.gpapi;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class GpApiApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(GpApiApplication.class)
                .headless(false)              // Swing GUI를 띄우려면 반드시 false
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }
}
