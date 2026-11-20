package com.murisegames.api.modules.User;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformRoleGrantRepository platformRoleGrantRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserService userService;

    @Test
    void provisionsProfileIdempotentlyAndIgnoresUntrustedRoleClaims() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .claim("email", "player@murisegames.com")
                .claim("user_metadata", Map.of(
                        "display_name", "Joram",
                        "role", "ADMIN"
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        User provisioned = User.builder()
                .id(userId)
                .displayName("Joram")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(provisioned));
        when(platformRoleGrantRepository.findByUserIdAndRevokedAtIsNull(userId))
                .thenReturn(List.of());

        UserResponse response = userService.getProfile(jwt);

        verify(userRepository).insertIfAbsent(userId, "Joram");
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.displayName()).isEqualTo("Joram");
        assertThat(response.platformRoles()).isEmpty();
    }

    @Test
    void returnsExistingProfileWithoutReinserting() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        User existing = User.builder()
                .id(userId)
                .displayName("Existing Player")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        User resolved = userService.resolveOrProvision(jwt);

        verify(userRepository, never()).insertIfAbsent(userId, "Existing Player");
        assertThat(resolved.getDisplayName()).isEqualTo("Existing Player");
    }
}