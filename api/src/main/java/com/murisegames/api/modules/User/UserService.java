package com.murisegames.api.modules.User;

import com.murisegames.api.modules.User.dto.UserUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 80;

    private final UserRepository userRepository;
    private final PlatformRoleGrantRepository platformRoleGrantRepository;
    private final EntityManager entityManager;

    /**
     * Resolves the authenticated Supabase user from the verified JWT subject.
     * Creates a missing murise.profiles row idempotently without granting any roles.
     */
    @Transactional
    public User resolveOrProvision(Jwt jwt) {
        UUID userId = extractUserId(jwt);

        return userRepository.findById(userId).orElseGet(() -> {
            String initialDisplayName = deriveInitialDisplayName(jwt, userId);
            userRepository.insertIfAbsent(userId, initialDisplayName);

            return userRepository.findById(userId)
                    .orElseThrow(() -> new UserException(
                            "Unable to resolve user profile for authenticated account."));
        });
    }

    @Transactional(readOnly = true)
    public User getRequiredUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User profile not found."));
    }

    @Transactional(readOnly = true)
    public List<PlatformRole> getActivePlatformRoles(UUID userId) {
        return platformRoleGrantRepository.findByUserIdAndRevokedAtIsNull(userId)
                .stream()
                .map(PlatformRoleGrant::getRole)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isPlatformAdmin(UUID userId) {
        return platformRoleGrantRepository.existsByUserIdAndRoleAndRevokedAtIsNull(
                userId,
                PlatformRole.ADMIN
        );
    }

    @Transactional
    public UserResponse getProfile(Jwt jwt) {
        User user = resolveOrProvision(jwt);
        return UserResponse.from(user, getActivePlatformRoles(user.getId()));
    }

    @Transactional
    public UserResponse updateDisplayName(Jwt jwt, UserUpdateRequest request) {
        User user = resolveOrProvision(jwt);
        String normalized = normalizeDisplayName(request.displayName());

        if (normalized.isEmpty()) {
            throw new UserException("Display name cannot be blank.");
        }

        user.setDisplayName(normalized);
        userRepository.saveAndFlush(user);
        entityManager.refresh(user);

        return UserResponse.from(user, getActivePlatformRoles(user.getId()));
    }

    public UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IllegalArgumentException("Authenticated JWT subject is missing.");
        }
        return UUID.fromString(jwt.getSubject());
    }

    private String deriveInitialDisplayName(Jwt jwt, UUID userId) {
        // Supabase user_metadata is client-writable: use only as an initial display-name hint, never for roles.
        Object rawMetadata = jwt.getClaim("user_metadata");
        if (rawMetadata instanceof Map<?, ?> metadata) {
            for (String key : List.of("display_name", "full_name", "name")) {
                Object candidate = metadata.get(key);
                if (candidate instanceof String value) {
                    String normalized = normalizeDisplayName(value);
                    if (!normalized.isEmpty()) {
                        return normalized;
                    }
                }
            }
        }

        String email = jwt.getClaimAsString("email");
        if (email != null) {
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                String localPart = normalizeDisplayName(email.substring(0, atIndex));
                if (!localPart.isEmpty()) {
                    return localPart;
                }
            }
        }

        return "Player-" + userId.toString().substring(0, 8);
    }

    private String normalizeDisplayName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_DISPLAY_NAME_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DISPLAY_NAME_LENGTH).trim();
    }
}