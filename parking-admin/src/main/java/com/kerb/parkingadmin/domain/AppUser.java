package com.kerb.parkingadmin.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** UUID assigned by the IDP after successful sync. Null if sync is still pending. */
    @Column(name = "idp_user_id", unique = true)
    private UUID idpUserId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    /** Parking-specific: user's vehicle plate for permit lookup. */
    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppUserStatus status = AppUserStatus.ACTIVE;

    /** Tracks whether this user has been successfully synced to the IDP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "idp_sync_status", nullable = false)
    @Builder.Default
    private IdpSyncStatus idpSyncStatus = IdpSyncStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public enum AppUserStatus { ACTIVE, SUSPENDED, DEACTIVATED }

    public enum IdpSyncStatus {
        PENDING,  // Not yet synced (IDP call failed or not attempted)
        SYNCED,   // Successfully synced — idpUserId is set
        FAILED    // Last sync attempt failed — retry needed
    }
}
