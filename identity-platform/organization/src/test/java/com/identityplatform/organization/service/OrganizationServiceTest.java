package com.identityplatform.organization.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.domain.OrganizationStatus;
import com.identityplatform.organization.dto.OrganizationDto;
import com.identityplatform.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService tests")
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private OrganizationService organizationService;

    private OrganizationDto.CreateRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new OrganizationDto.CreateRequest(
                "Acme Corp",
                "acme-corp",
                "acme.com",
                "admin@acme.com",
                "Acme",
                "Admin",
                false,
                false
        );
    }

    @Test
    @DisplayName("createOrganization: succeeds with valid data")
    void createOrganization_success() {
        when(organizationRepository.existsBySlugAndDeletedFalse("acme-corp")).thenReturn(false);
        when(organizationRepository.existsByPrimaryDomainAndDeletedFalse("acme.com")).thenReturn(false);

        Organization savedOrg = Organization.builder()
                .name("Acme Corp")
                .slug("acme-corp")
                .primaryDomain("acme.com")
                .status(OrganizationStatus.PENDING_SETUP)
                .build();
        when(organizationRepository.save(any())).thenReturn(savedOrg);

        Organization result = organizationService.createOrganization(validRequest);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getSlug()).isEqualTo("acme-corp");
        assertThat(result.getStatus()).isEqualTo(OrganizationStatus.PENDING_SETUP);
        verify(organizationRepository).save(any());
    }

    @Test
    @DisplayName("createOrganization: throws DuplicateResourceException when slug already exists")
    void createOrganization_duplicateSlug_throwsException() {
        when(organizationRepository.existsBySlugAndDeletedFalse("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("acme-corp");

        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrganization: throws DuplicateResourceException when domain already exists")
    void createOrganization_duplicateDomain_throwsException() {
        when(organizationRepository.existsBySlugAndDeletedFalse("acme-corp")).thenReturn(false);
        when(organizationRepository.existsByPrimaryDomainAndDeletedFalse("acme.com")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("acme.com");
    }

    @Test
    @DisplayName("activateOrganization: transitions status to ACTIVE")
    void activateOrganization_success() {
        UUID orgId = UUID.randomUUID();
        Organization org = Organization.builder()
                .status(OrganizationStatus.PENDING_SETUP)
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);

        organizationService.activateOrganization(orgId);

        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        verify(organizationRepository).save(org);
    }

    @Test
    @DisplayName("findByEmailDomain: extracts domain from email and finds the org")
    void findByEmailDomain_success() {
        Organization org = Organization.builder()
                .primaryDomain("company.com")
                .build();

        when(organizationRepository.findByPrimaryDomainAndDeletedFalse("company.com"))
                .thenReturn(Optional.of(org));

        Organization result = organizationService.findByEmailDomain("user@company.com");

        assertThat(result.getPrimaryDomain()).isEqualTo("company.com");
    }
}
