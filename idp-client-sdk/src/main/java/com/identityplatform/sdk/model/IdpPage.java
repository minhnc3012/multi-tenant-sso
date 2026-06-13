package com.identityplatform.sdk.model;

import java.util.List;

/**
 * Minimal representation of a Spring Data {@code Page} response from the IDP.
 */
public record IdpPage<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {}
