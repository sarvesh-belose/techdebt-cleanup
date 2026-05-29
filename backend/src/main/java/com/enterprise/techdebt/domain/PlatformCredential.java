package com.enterprise.techdebt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "platform_credential")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, unique = true)
    private CredentialKind kind;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Column(name = "secret_key_vault_ref", nullable = false, length = 500)
    private String secretKeyVaultRef;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "last_validated_ok")
    private Boolean lastValidatedOk;

    @Column(name = "last_validated_message", columnDefinition = "NVARCHAR(MAX)")
    private String lastValidatedMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
