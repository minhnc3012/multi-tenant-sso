package com.apiclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Client Sample — machine-to-machine authentication
 *
 * Flow:
 *   1. App starts → TokenService fetches a JWT from Identity Platform using client_credentials
 *   2. HTTP endpoints on port 8083 demonstrate calling IdP APIs with that token
 *   3. Token is refreshed automatically when it expires
 *
 * To run:
 *   cd api-client-sample && mvn spring-boot:run
 *   (requires identity-platform running on localhost:8080)
 *
 * Quick curl equivalent:
 *   curl -X POST http://localhost:8080/oauth2/token \
 *     -u "realestate-api-client:realestate-api-secret" \
 *     -d "grant_type=client_credentials&scope=openid%20api.read%20api.write"
 */
@SpringBootApplication
public class ApiClientSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiClientSampleApplication.class, args);
    }
}
