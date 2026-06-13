package com.identityplatform.core.exception;

import com.identityplatform.core.tenant.TenantNotSetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GlobalExceptionHandler tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleNotFound: returns 404 with correct type")
    void handleNotFound_returns404() {
        ProblemDetail problem = handler.handleNotFound(
                new ResourceNotFoundException("User", "test-id"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).isEqualTo("/errors/not-found");
        assertThat(problem.getDetail()).contains("test-id");
        assertThat(problem.getProperties().containsKey("timestamp")).isTrue();
    }

    @Test
    @DisplayName("handleNotFound: message contains resource name")
    void handleNotFound_messageContainsResource() {
        ProblemDetail problem = handler.handleNotFound(
                new ResourceNotFoundException("Organization", "org-123"));

        assertThat(problem.getDetail()).contains("Organization not found with id: org-123");
    }

    @Test
    @DisplayName("handleNotFound: custom message")
    void handleNotFound_customMessage() {
        ProblemDetail problem = handler.handleNotFound(
                new ResourceNotFoundException("Custom message here"));

        assertThat(problem.getDetail()).isEqualTo("Custom message here");
    }

    @Test
    @DisplayName("handleDuplicate: returns 409 with correct type")
    void handleDuplicate_returns409() {
        ProblemDetail problem = handler.handleDuplicate(
                new DuplicateResourceException("Slug already exists"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType().toString()).isEqualTo("/errors/conflict");
        assertThat(problem.getDetail()).contains("Slug already exists");
        assertThat(problem.getProperties().containsKey("timestamp")).isTrue();
    }

    @Test
    @DisplayName("ResourceNotFoundException(resource, id): message format")
    void resourceNotFoundException_resourceAndId() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "user-123");
        assertThat(ex.getMessage()).isEqualTo("User not found with id: user-123");
    }

    @Test
    @DisplayName("ResourceNotFoundException(message): custom message")
    void resourceNotFoundException_customMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Custom error");
        assertThat(ex.getMessage()).isEqualTo("Custom error");
    }

    @Test
    @DisplayName("DuplicateResourceException: message is stored correctly")
    void duplicateResourceException_message() {
        DuplicateResourceException ex = new DuplicateResourceException("Duplicate entry");
        assertThat(ex.getMessage()).isEqualTo("Duplicate entry");
    }

    @Test
    @DisplayName("TenantNotSetException: message is stored correctly")
    void tenantNotSetException_message() {
        TenantNotSetException ex = new TenantNotSetException("No tenant");
        assertThat(ex.getMessage()).isEqualTo("No tenant");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ProblemDetail: all error responses have a timestamp")
    void problemDetails_haveTimestamp() {
        ProblemDetail notFound = handler.handleNotFound(new ResourceNotFoundException("X", "1"));
        ProblemDetail conflict = handler.handleDuplicate(new DuplicateResourceException("dup"));

        assertThat(notFound.getProperties().get("timestamp")).isNotNull();
        assertThat(conflict.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("ProblemDetail: all error responses have a type URL")
    void problemDetails_haveType() {
        assertThat(handler.handleNotFound(new ResourceNotFoundException("X", "1")).getType().toString())
                .isEqualTo("/errors/not-found");
        assertThat(handler.handleDuplicate(new DuplicateResourceException("x")).getType().toString())
                .isEqualTo("/errors/conflict");
    }
}
