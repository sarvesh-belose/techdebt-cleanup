package com.enterprise.techdebt.security;

import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.AppUser;
import com.enterprise.techdebt.repo.AppUserRepository;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;
    private final boolean devBypass;

    public CurrentUserService(AppUserRepository userRepository,
                              @Value("${techdebt.security.dev-bypass:false}") boolean devBypass) {
        this.userRepository = userRepository;
        this.devBypass = devBypass;
    }

    @Transactional
    public CurrentUser require() {
        if (devBypass) {
            return upsertAndDecorate("dev@local", "dev@local", "Local Dev");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated principal on SecurityContext");
        }
        String upn = firstNonBlank(jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("upn"),
                jwt.getClaimAsString("email"),
                jwt.getSubject());
        String email = firstNonBlank(jwt.getClaimAsString("email"), upn);
        String name = firstNonBlank(jwt.getClaimAsString("name"), upn);
        return upsertAndDecorate(upn, email, name);
    }

    private CurrentUser upsertAndDecorate(String upn, String email, String displayName) {
        AppUser user = userRepository.findByUpnIgnoreCase(upn)
                .orElseGet(() -> userRepository.save(AppUser.builder()
                        .upn(upn)
                        .email(email)
                        .displayName(displayName)
                        .enabled(true)
                        .createdAt(Instant.now())
                        .build()));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        MDC.put(MdcConstants.USER_ID, String.valueOf(user.getId()));
        MDC.put(MdcConstants.USER_UPN, user.getUpn());
        return new CurrentUser(user.getId(), user.getUpn(), user.getEmail(), user.getDisplayName());
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "unknown";
    }
}
