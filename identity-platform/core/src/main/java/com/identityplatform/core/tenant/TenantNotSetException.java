package com.identityplatform.core.tenant;

public class TenantNotSetException extends RuntimeException {
    public TenantNotSetException(String message) {
        super(message);
    }
}
