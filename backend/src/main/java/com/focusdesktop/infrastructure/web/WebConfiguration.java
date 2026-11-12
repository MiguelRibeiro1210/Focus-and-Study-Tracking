package com.focusdesktop.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("tauri://localhost", "http://tauri.localhost",
                        "https://tauri.localhost", "http://localhost:[*]", "https://localhost:[*]",
                        "http://127.0.0.1:[*]", "https://127.0.0.1:[*]", "http://[::1]:[*]",
                        "https://[::1]:[*]")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .exposedHeaders("Location")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
