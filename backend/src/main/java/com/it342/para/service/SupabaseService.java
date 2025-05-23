package com.it342.para.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SupabaseService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.api-key}")
    private String supabaseApiKey;


    @Configuration
    static class SupabaseServiceConfiguration {
        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

    }
    private final RestTemplate restTemplate;
    private static final Logger logger = LoggerFactory.getLogger(SupabaseService.class);

    public SupabaseService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> signUpWithEmailPassword(String email, String password) {
        String url = supabaseUrl + "/auth/v1/signup";
        HttpHeaders headers = createHeaders(supabaseApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                logger.info("Signup successful, response contains: {}", body.keySet());
                if (body.containsKey("user")) {
                    return body;
                }
                return Map.of("user", Map.of("id", body.get("id")));
            }
            throw new RuntimeException("Signup failed with status: " + response.getStatusCode());
        } catch (Exception ex) {
            logger.error("Signup failed", ex);
            throw new RuntimeException("Signup failed: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> loginWithEmailPassword(String email, String password) {
        String url = supabaseUrl + "/auth/v1/token?grant_type=password";
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);

        try {
            HttpHeaders headers = createHeaders(supabaseApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                logger.info("Login successful, response contains: {}", body.keySet());
                return body;
            }
            throw new RuntimeException("Login failed with status: " + response.getStatusCode());
        } catch (Exception ex) {
            logger.error("Login failed", ex);
            throw new RuntimeException("Login failed: " + ex.getMessage(), ex);
        }
    }

    public boolean createProfileInSupabase(String uid, String username, String email, String userToken) {
        String url = supabaseUrl + "/rest/v1/profiles";
        Map<String, Object> profile = Map.of(
                "id", uid,
                "username", username,
                "email", email
        );

        try {
            logger.info("Creating profile for user {}", uid);
            HttpHeaders headers = createHeaders(userToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(profile, headers),
                    String.class
            );



            boolean success = response.getStatusCode() == HttpStatus.CREATED;
            logger.info("Profile creation result: {}", success ? "success" : "failed with " + response.getStatusCode());

            if (!success) {
                logger.error("Profile creation failed with status code: {}", response.getStatusCode());
                logger.error("Response body: {}", response.getBody());
            }


            return success;


        } catch (Exception e) {
            logger.error("Profile creation failed for {}: {}", uid, e.getMessage(), e);
            return false;
        }

    }

    public boolean updateProfile(String uid, String username, String email, String userToken) {
        String url = supabaseUrl + "/rest/v1/profiles?id=eq." + uid;

        try {
            logger.info("Updating profile for user {}", uid);

            // Create headers with proper content type
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + userToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Prefer", "resolution=merge-duplicates");

            // Create update payload
            Map<String, Object> updates = new HashMap<>();
            updates.put("id", uid);
            updates.put("username", username);
            if (email != null && !email.isEmpty()) {
                updates.put("email", email);
            }

            // Create request entity
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updates, headers);

            // Make the request
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // Check response status
            boolean success = response.getStatusCode().is2xxSuccessful();
            logger.info("Profile update result: {}", success ? "success" : "failed with " + response.getStatusCode());
            return success;
        } catch (Exception e) {
            logger.error("Profile update failed for {}: {}", uid, e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getProfileFromSupabase(String supabaseUid, String userToken) {
        String url = supabaseUrl + "/rest/v1/profiles?id=eq." + supabaseUid;
        int maxRetries = 3;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                logger.info("Fetching profile for user {} (attempt {})", supabaseUid, attempts + 1);
                HttpHeaders headers = createHeaders(userToken); // Use USER token, not API key
                headers.set("Accept", "application/json");

                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers), // Fix: Remove duplicate headers
                        new ParameterizedTypeReference<>() {}
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> profiles = response.getBody();
                    if (!profiles.isEmpty()) {
                        logger.info("Profile found for user {}", supabaseUid);
                        return profiles.get(0);
                    }
                }

                logger.info("No profile found for user {} on attempt {}", supabaseUid, attempts + 1);
                attempts++;
                Thread.sleep(1000); // Delay between retries
            } catch (Exception e) {
                logger.error("Failed to fetch profile for {}: {}", supabaseUid, e.getMessage());
                attempts++;
                if (attempts < maxRetries) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
            }
        }
        logger.warn("Profile not found after {} attempts for user {}", maxRetries, supabaseUid);
        return null;
    }

    public Map<String, Object> exchangeGoogleAuthCodeForToken(String code) {
        String url = supabaseUrl + "/auth/v1/token?grant_type=authorization_code";
        Map<String, Object> payload = Map.of(
                "code", code,
                "redirect_uri", "your_redirect_uri",
                "client_id", "your_client_id",
                "client_secret", "your_client_secret"
        );
        return postRequest(url, payload);
    }

    public Map<String, Object> getUserFromToken(String jwt) {
        String url = supabaseUrl + "/auth/v1/user";

        try {
            logger.info("Getting user from token");
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + jwt);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders(jwt)),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Successfully retrieved user from token");
                return response.getBody();
            }

            logger.error("Failed to get user from token: {}", response.getStatusCode());
            throw new RuntimeException("Invalid token: status " + response.getStatusCode());


        } catch (Exception e) {
            logger.error("Failed to get user from token", e);
            throw new RuntimeException("Invalid token: " + e.getMessage());
        }
    }

    private Map<String, Object> postRequest(String endpoint, Map<String, Object> payload) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, createHeaders(supabaseApiKey)),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            throw new RuntimeException("Supabase error: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("API request failed", e);
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + token);
        headers.set("Prefer", "return=minimal");
        headers.set("Content-Type", "application/json");
        return headers;
    }
}