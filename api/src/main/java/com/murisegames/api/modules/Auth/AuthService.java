package com.murisegames.api.modules.Auth;

import com.murisegames.api.modules.User.PlatformRole;
import com.murisegames.api.modules.User.User;
import com.murisegames.api.modules.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;

    @Transactional
    public AuthResponse resolveAuthenticatedAccount(Jwt jwt) {
        User user = userService.resolveOrProvision(jwt);
        List<PlatformRole> platformRoles = userService.getActivePlatformRoles(user.getId());

        return new AuthResponse(
                user.getId(),
                jwt.getClaimAsString("email"),
                user.getDisplayName(),
                platformRoles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}