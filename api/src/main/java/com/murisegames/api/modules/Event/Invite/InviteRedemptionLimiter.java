package com.murisegames.api.modules.Event.Invite;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteRedemptionLimiter {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean allow(UUID userId) {
        Integer attempts = jdbcTemplate.queryForObject("""
                INSERT INTO murise.invite_redemption_limits AS limits (
                    user_id, window_started_at, attempts
                )
                VALUES (?, now(), 1)
                ON CONFLICT (user_id) DO UPDATE
                SET
                    attempts = CASE
                        WHEN limits.window_started_at
                             <= now() - interval '1 minute'
                        THEN 1
                        ELSE LEAST(limits.attempts + 1, 11)
                    END,
                    window_started_at = CASE
                        WHEN limits.window_started_at
                             <= now() - interval '1 minute'
                        THEN now()
                        ELSE limits.window_started_at
                    END
                RETURNING attempts
                """, Integer.class, userId);

        return attempts != null && attempts <= 10;
    }
}