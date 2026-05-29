package com.enterprise.techdebt.security;

import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.AppUser;
import com.enterprise.techdebt.repo.AppUserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);
    private static final String DEV_UPN = "dev@local";

    private final AppUserRepository userRepository;
    private final boolean devBypass;
    private final TransactionTemplate newTxTemplate;

    public CurrentUserService(AppUserRepository userRepository,
                              PlatformTransactionManager txManager,
                              @Value("${techdebt.security.dev-bypass:false}") boolean devBypass) {
        this.userRepository = userRepository;
        this.devBypass = devBypass;
        this.newTxTemplate = new TransactionTemplate(txManager);
        this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Pre-seed the dev@local user when dev-bypass is enabled so the first wave
     * of parallel requests from the SPA don't race each other into the same
     * insert. Runs once at startup, single-threaded, so no race here.
     */
    @PostConstruct
    void prewarmDevUser() {
        if (!devBypass) return;
        if (userRepository.findByUpnIgnoreCase(DEV_UPN).isPresent()) return;
        try {
            newTxTemplate.execute(status -> userRepository.saveAndFlush(AppUser.builder()
                    .upn(DEV_UPN)
                    .email(DEV_UPN)
                    .displayName("Local Dev")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build()));
            log.info("Pre-seeded dev-bypass user {}", DEV_UPN);
        } catch (DataIntegrityViolationException ignored) {
            // Another instance (or a previous run on the same shared DB) already
            // created it — harmless.
        }
    }

    public CurrentUser require() {
        if (devBypass) {
            return upsertAndDecorate(DEV_UPN, DEV_UPN, "Local Dev");
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
                .orElseGet(() -> createOrRefindAfterRace(upn, email, displayName));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        MDC.put(MdcConstants.USER_ID, String.valueOf(user.getId()));
        MDC.put(MdcConstants.USER_UPN, user.getUpn());
        return new CurrentUser(user.getId(), user.getUpn(), user.getEmail(), user.getDisplayName());
    }

    /**
     * Insert the user in a fresh transaction so that a concurrent insert of the
     * same UPN (multiple parallel requests on first login — common with an SPA
     * that fires several queries in parallel) doesn't poison the caller's flow.
     * On unique-constraint violation we lost the race; re-query to pick up the
     * row the other thread committed.
     *
     * <p>Uses {@link TransactionTemplate} rather than {@code @Transactional}
     * because this is called from a sibling method on the same bean — Spring's
     * AOP proxy would not intercept the self-invocation, so the propagation
     * attribute would be silently ignored.
     */
    private AppUser createOrRefindAfterRace(String upn, String email, String displayName) {
        try {
            return newTxTemplate.execute(status -> userRepository.saveAndFlush(AppUser.builder()
                    .upn(upn)
                    .email(email)
                    .displayName(displayName)
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build()));
        } catch (DataIntegrityViolationException race) {
            // Lost the race — that transaction rolled back; re-query in a fresh one.
            return newTxTemplate.execute(status -> userRepository.findByUpnIgnoreCase(upn)
                    .orElseThrow(() -> race));
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "unknown";
    }
}
