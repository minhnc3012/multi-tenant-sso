package com.identityplatform.authserver.service;

import com.identityplatform.authserver.domain.ClientType;
import com.identityplatform.authserver.domain.RegisteredClientMetadata;
import com.identityplatform.authserver.repository.RegisteredClientMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisteredClientMetadataService {

    private final RegisteredClientMetadataRepository repository;

    /** Idempotent: creates metadata only if it doesn't exist yet. */
    @Transactional
    public RegisteredClientMetadata ensure(String registeredClientId, UUID organizationId,
                                           ClientType clientType, String description) {
        return repository.findByRegisteredClientId(registeredClientId)
                .orElseGet(() -> repository.save(RegisteredClientMetadata.builder()
                        .registeredClientId(registeredClientId)
                        .organizationId(organizationId)
                        .clientType(clientType)
                        .description(description)
                        .build()));
    }

    public Optional<RegisteredClientMetadata> findByRegisteredClientId(String registeredClientId) {
        return repository.findByRegisteredClientId(registeredClientId);
    }

    public List<RegisteredClientMetadata> findByOrganizationId(UUID organizationId) {
        return repository.findByOrganizationId(organizationId);
    }

    public List<RegisteredClientMetadata> findAll() {
        return repository.findAll();
    }
}
