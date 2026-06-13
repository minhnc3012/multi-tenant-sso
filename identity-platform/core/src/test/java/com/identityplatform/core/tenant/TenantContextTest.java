package com.identityplatform.core.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantContext tests")
class TenantContextTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getCurrentTenant: throws TenantNotSetException when not set")
    void getCurrentTenant_notSet_throwsException() {
        TenantContext.clear();

        assertThatThrownBy(TenantContext::getCurrentTenant)
                .isInstanceOf(TenantNotSetException.class)
                .hasMessageContaining("No tenant context");
    }

    @Test
    @DisplayName("getCurrentTenantOrNull: returns null when not set")
    void getCurrentTenantOrNull_notSet_returnsNull() {
        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenantOrNull()).isNull();
    }

    @Test
    @DisplayName("getCurrentTenantOrNull: returns UUID when set")
    void getCurrentTenantOrNull_set_returnsUuid() {
        UUID testId = UUID.randomUUID();
        TenantContext.setCurrentTenant(testId);

        assertThat(TenantContext.getCurrentTenantOrNull()).isEqualTo(testId);
    }

    @Test
    @DisplayName("hasTenant: false when not set")
    void hasTenant_notSet_returnsFalse() {
        TenantContext.clear();
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    @Test
    @DisplayName("hasTenant: true when set")
    void hasTenant_set_returnsTrue() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        assertThat(TenantContext.hasTenant()).isTrue();
    }

    @Test
    @DisplayName("setCurrentTenant + getCurrentTenant: stores and reads the correct UUID")
    void setAndGet_correctValue() {
        UUID testId = UUID.randomUUID();
        TenantContext.setCurrentTenant(testId);

        assertThat(TenantContext.getCurrentTenant()).isEqualTo(testId);
    }

    @Test
    @DisplayName("setCurrentTenant: overwrites the previous value")
    void setCurrentTenant_overwrite() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        TenantContext.setCurrentTenant(first);
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(first);

        TenantContext.setCurrentTenant(second);
        assertThat(TenantContext.getCurrentTenant()).isEqualTo(second);
    }

    @Test
    @DisplayName("clear: removes tenant context")
    void clear_removesContext() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        assertThat(TenantContext.hasTenant()).isTrue();

        TenantContext.clear();

        assertThat(TenantContext.hasTenant()).isFalse();
        assertThat(TenantContext.getCurrentTenantOrNull()).isNull();
    }

    @Test
    @DisplayName("clear: after clear, getCurrentTenant() throws")
    void clear_getCurrentTenant_throws() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        TenantContext.clear();

        assertThatThrownBy(TenantContext::getCurrentTenant)
                .isInstanceOf(TenantNotSetException.class);
    }
}
