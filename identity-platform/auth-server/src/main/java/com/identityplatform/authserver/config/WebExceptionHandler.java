package com.identityplatform.authserver.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;

/**
 * Handles web-layer exceptions that cannot be placed in the core module
 * because core does not depend on spring-webmvc.
 */
@RestControllerAdvice
public class WebExceptionHandler {

    // Returns 404 silently — no ERROR log (browser requests favicon.ico, etc.)
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
