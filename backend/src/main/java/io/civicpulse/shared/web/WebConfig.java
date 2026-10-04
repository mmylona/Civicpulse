package io.civicpulse.shared.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (cors.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.IF_MATCH,
                        "Idempotency-Key",
                        RequestIdFilter.HEADER)
                .exposedHeaders(RequestIdFilter.HEADER, HttpHeaders.ETAG, HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER)
                .maxAge(3600);
    }
}
