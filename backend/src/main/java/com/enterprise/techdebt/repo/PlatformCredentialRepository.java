package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.CredentialKind;
import com.enterprise.techdebt.domain.PlatformCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformCredentialRepository extends JpaRepository<PlatformCredential, Long> {
    Optional<PlatformCredential> findByKind(CredentialKind kind);
}
