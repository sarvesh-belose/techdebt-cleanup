package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUpnIgnoreCase(String upn);

    boolean existsByUpnIgnoreCase(String upn);
}
