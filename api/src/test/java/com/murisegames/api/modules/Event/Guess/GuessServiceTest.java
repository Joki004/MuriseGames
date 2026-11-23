package com.murisegames.api.modules.Event.Guess;

import com.murisegames.api.modules.Event.Clue.ClueReleaseService;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GuessServiceTest {

    @Test
    void normalizeGuessStripsAccentsPunctuationAndExtraWhitespace() {
        assertThat(GuessService.normalizeGuess("  Le Fabuleux Destin d'Amélie Poulain! "))
                .isEqualTo("le fabuleux destin d amelie poulain");
        assertThat(GuessService.normalizeGuess("Spider-Man: No   Way Home"))
                .isEqualTo("spider man no way home");
    }

    @Test
    void nonCaptainParticipantIsRejectedWith403Forbidden() {
        ClueReleaseService releaseService = mock(ClueReleaseService.class);
        EventMembershipService membershipService = mock(EventMembershipService.class);
        EventParticipantRepository participantRepo = mock(EventParticipantRepository.class);
        TeamRepository teamRepo = mock(TeamRepository.class);
        UserService userService = mock(UserService.class);
        EntityManager em = mock(EntityManager.class);

        GuessService service = new GuessService(
                releaseService, membershipService, participantRepo, teamRepo, userService, em
        );

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);

        when(userService.extractUserId(jwt)).thenReturn(userId);
        when(releaseService.lockAndValidateForGuess(eventId, 1, 1L))
                .thenReturn(new ClueReleaseService.StateRow(1, "open", 1L));

        EventParticipant member = new EventParticipant();
        member.setEventId(eventId);
        member.setUserId(userId);
        member.setTeamId(teamId);
        member.setStatus("active");
        member.setCaptain(false);

        when(participantRepo.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(member));

        var request = new GuessTypes.SubmitGuessRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, 1L, "Inception"
        );

        assertThatThrownBy(() -> service.submitGuess(jwt, eventId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void selfGuessIsRejectedWith400BadRequest() {
        ClueReleaseService releaseService = mock(ClueReleaseService.class);
        EventMembershipService membershipService = mock(EventMembershipService.class);
        EventParticipantRepository participantRepo = mock(EventParticipantRepository.class);
        TeamRepository teamRepo = mock(TeamRepository.class);
        UserService userService = mock(UserService.class);
        EntityManager em = mock(EntityManager.class);

        GuessService service = new GuessService(
                releaseService, membershipService, participantRepo, teamRepo, userService, em
        );

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);

        when(userService.extractUserId(jwt)).thenReturn(userId);
        when(releaseService.lockAndValidateForGuess(eventId, 0, 0L))
                .thenReturn(new ClueReleaseService.StateRow(0, "open", 0L));

        EventParticipant captain = new EventParticipant();
        captain.setEventId(eventId);
        captain.setUserId(userId);
        captain.setTeamId(teamId);
        captain.setStatus("active");
        captain.setCaptain(true);

        Team team = new Team();
        team.setId(teamId);
        team.setEventId(eventId);
        team.setName("Aka");
        team.setActive(true);

        when(participantRepo.findByEventIdAndUserId(eventId, userId))
                .thenReturn(Optional.of(captain));
        when(teamRepo.findByIdAndEventId(teamId, eventId))
                .thenReturn(Optional.of(team));

        var request = new GuessTypes.SubmitGuessRequest(
                UUID.randomUUID(), teamId, 0, 0L, "Our Own Movie"
        );

        assertThatThrownBy(() -> service.submitGuess(jwt, eventId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}