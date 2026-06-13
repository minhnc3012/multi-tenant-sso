package com.identityplatform.authserver.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RegisteredClientService {

    private static final String SELECT_WITH_META =
            "SELECT c.id, c.client_id, c.client_name, c.client_authentication_methods, " +
            "c.authorization_grant_types, c.redirect_uris, c.scopes, c.client_secret_expires_at, " +
            "m.organization_id, m.client_type, m.description " +
            "FROM oauth2_registered_client c " +
            "LEFT JOIN registered_client_metadata m ON m.registered_client_id = c.id ";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public RegisteredClientService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Map<String, Object>> listAllWithMetadata() {
        return jdbcTemplate.queryForList(
                SELECT_WITH_META + "ORDER BY c.client_id_issued_at DESC");
    }

    public Map<String, Object> getByIdWithMetadata(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SELECT_WITH_META + "WHERE c.id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** List all clients owned by an organization (requires metadata row). */
    public List<Map<String, Object>> listByOrganization(UUID organizationId) {
        return jdbcTemplate.queryForList(
                "SELECT c.id, c.client_id, c.client_name, c.client_authentication_methods, " +
                "c.authorization_grant_types, c.redirect_uris, c.scopes, c.client_secret_expires_at, " +
                "m.organization_id, m.client_type, m.description " +
                "FROM oauth2_registered_client c " +
                "JOIN registered_client_metadata m ON m.registered_client_id = c.id " +
                "WHERE m.organization_id = ? " +
                "ORDER BY c.client_id_issued_at DESC",
                organizationId);
    }

    public Map<String, Object> getById(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM oauth2_registered_client WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> create(String clientId, String clientName,
                                      String clientSecret, String redirectUris,
                                      String scopes, String grantTypes, String authMethods) {
        String hashedSecret = passwordEncoder.encode(clientSecret);
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO oauth2_registered_client " +
                "(id, client_id, client_id_issued_at, client_secret, client_name, " +
                "client_authentication_methods, authorization_grant_types, redirect_uris, " +
                "scopes, client_settings, token_settings) " +
                "VALUES (?, ?, NOW(), ?, ?, ?, ?, ?, ?, '{}', '{}')",
                id, clientId, hashedSecret, clientName, authMethods, grantTypes, redirectUris, scopes);
        return getById(id);
    }

    public void update(String id, String clientName, String redirectUris, String scopes) {
        jdbcTemplate.update(
                "UPDATE oauth2_registered_client SET client_name = ?, redirect_uris = ?, scopes = ? WHERE id = ?",
                clientName, redirectUris, scopes, id);
    }

    public void rotateSecret(String id, String newSecret) {
        jdbcTemplate.update(
                "UPDATE oauth2_registered_client SET client_secret = ?, client_secret_expires_at = NOW() WHERE id = ?",
                passwordEncoder.encode(newSecret), id);
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
    }
}
