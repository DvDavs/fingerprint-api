package com.example.fingerprint_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // SUSTITUIR .allowedOrigins("http://localhost:3000") si lo prefieres,
        // o bien .allowedOriginPatterns("http://localhost:3000")
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000") // Ya NO "*"
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);  // si deseas credenciales
    }
}