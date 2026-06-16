package com.convexa.ai.convexa_ai_backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Reads the three Cloudinary credentials from application.properties and
 * constructs the Cloudinary SDK client as a Spring-managed singleton bean.
 *
 * Required properties:
 *   cloudinary.cloud-name=
 *   cloudinary.api-key=
 *   cloudinary.api-secret=
 */
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true          // always return https URLs
        ));
    }
}
