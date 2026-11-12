package com.focusdesktop.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiTokenConfiguration {
    @Bean
    ApiTokenStore apiTokenStore(@Value("${focusdesktop.data-dir:${user.home}/.local/share/focusdesktop}")
            String dataDirectory) {
        return new ApiTokenStore(java.nio.file.Path.of(dataDirectory));
    }

    @Bean
    ApiToken apiToken(ApiTokenStore store, @Value("${focusdesktop.api.token:}") String configuredTestToken) {
        return new ApiToken(configuredTestToken.isBlank() ? store.loadOrCreate() : configuredTestToken);
    }

    public record ApiToken(String value) { }
}
