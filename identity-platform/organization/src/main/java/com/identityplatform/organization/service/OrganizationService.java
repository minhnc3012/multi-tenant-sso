package com.identityplatform.organization.service;

import com.identityplatform.core.exception.DuplicateResourceException;
import com.identityplatform.core.exception.ResourceNotFoundException;
import com.identityplatform.organization.domain.Organization;
import com.identityplatform.organization.domain.OrganizationStatus;
import com.identityplatform.organization.dto.OrganizationDto;
import com.identityplatform.organization.repository.OrganizationRepository;
import com.identityplatform.organization.events.OrgCreatedEvent;
import com.identityplatform.organization.events.OrgSuspendedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher events;

    /**
     * Creates a new Organization when onboarding a tenant.
     * Typically called automatically when a customer signs up or signs a contract.
     */
    @Transactional
    public Organization createOrganization(OrganizationDto.CreateRequest request) {
        if (organizationRepository.existsBySlugAndDeletedFalse(request.slug())) {
            throw new DuplicateResourceException("Slug '" + request.slug() + "' is already in use");
        }

        if (request.primaryDomain() != null &&
                organizationRepository.existsByPrimaryDomainAndDeletedFalse(request.primaryDomain())) {
            throw new DuplicateResourceException("Domain '" + request.primaryDomain() + "' is already registered");
        }

        Organization org = Organization.builder()
                .name(request.name())
                .slug(request.slug())
                .primaryDomain(request.primaryDomain())
                .mfaRequired(request.mfaRequired())
                .selfRegistrationAllowed(request.selfRegistrationAllowed())
                .status(OrganizationStatus.PENDING_SETUP)
                .build();

        org = organizationRepository.save(org);
        events.publishEvent(new OrgCreatedEvent(
                org.getId(), org.getSlug(),
                request.adminEmail(), request.adminFirstName(), request.adminLastName()));
        log.info("Organization created: id={}, slug={}", org.getId(), org.getSlug());
        return org;
    }

    @Transactional
    public Organization updateOrganization(UUID id, OrganizationDto.UpdateRequest request) {
        Organization org = findById(id);

        if (request.name() != null) org.setName(request.name());
        if (request.primaryDomain() != null) org.setPrimaryDomain(request.primaryDomain());
        if (request.mfaRequired() != null) org.setMfaRequired(request.mfaRequired());
        if (request.selfRegistrationAllowed() != null) org.setSelfRegistrationAllowed(request.selfRegistrationAllowed());
        if (request.logoUrl() != null) org.setLogoUrl(request.logoUrl());
        if (request.primaryColor() != null) org.setPrimaryColor(request.primaryColor());

        return organizationRepository.save(org);
    }

    @Transactional
    public void activateOrganization(UUID id) {
        Organization org = findById(id);
        org.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.save(org);
        log.info("Organization activated: id={}", id);
    }

    @Transactional
    public void suspendOrganization(UUID id) {
        Organization org = findById(id);
        org.setStatus(OrganizationStatus.SUSPENDED);
        organizationRepository.save(org);
        events.publishEvent(new OrgSuspendedEvent(org.getId(), org.getSlug()));
        log.warn("Organization suspended: id={}", id);
    }

    @Transactional(readOnly = true)
    public Page<Organization> findAll(Pageable pageable) {
        return organizationRepository.findByDeletedFalse(pageable);
    }

    @Transactional(readOnly = true)
    public Organization findById(UUID id) {
        return organizationRepository.findById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    }

    @Transactional(readOnly = true)
    public Organization findBySlug(String slug) {
        return organizationRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with slug: " + slug));
    }

    /**
     * Used to auto-detect the tenant from the email domain when a user logs in.
     * Example: user@company.com → finds the org whose primaryDomain = "company.com"
     */
    @Transactional(readOnly = true)
    public Organization findByEmailDomain(String email) {
        String domain = email.substring(email.indexOf('@') + 1);
        return organizationRepository.findByPrimaryDomainAndDeletedFalse(domain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No organization found for email domain: " + domain));
    }
}
