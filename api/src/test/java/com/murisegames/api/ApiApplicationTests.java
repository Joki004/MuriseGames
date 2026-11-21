package com.murisegames.api;

import com.murisegames.api.modules.Catalog.GameDefinitionRepository;
import com.murisegames.api.modules.Catalog.GameVersionRepository;
import com.murisegames.api.modules.Event.EventGame.EventGameRepository;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Invite.EventInviteRepository;
import com.murisegames.api.modules.Event.Invite.InviteRedemptionLimiter;
import com.murisegames.api.modules.Event.JoinRequest.JoinRequestRepository;
import com.murisegames.api.modules.Event.Live.EventStateRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipRepository;
import com.murisegames.api.modules.Event.Membership.EventRoleGrantRepository;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Scoring.ScoreEntryRepository;
import com.murisegames.api.modules.Event.Scoring.ScoreboardSnapshotRepository;
import com.murisegames.api.modules.Event.Scoring.ScoreboardSnapshotRowRepository;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.PlatformRoleGrantRepository;
import com.murisegames.api.modules.User.UserRepository;
import com.murisegames.api.modules.Audit.AuditEventRepository;
import com.murisegames.api.modules.Picture.MediaAssetRepository;
import com.murisegames.api.modules.Picture.PictureRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@ActiveProfiles("test")
class ApiApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlatformRoleGrantRepository platformRoleGrantRepository;

    @MockitoBean
    private EntityManager entityManager;

    @MockitoBean
    private EventRepository eventRepository;

    @MockitoBean
    private EventMembershipRepository eventMembershipRepository;

    @MockitoBean
    private EventRoleGrantRepository eventRoleGrantRepository;

    @MockitoBean
    private TeamRepository teamRepository;

    @MockitoBean
    private EventInviteRepository eventInviteRepository;

    @MockitoBean
    private JoinRequestRepository joinRequestRepository;

    @MockitoBean
    private EventParticipantRepository eventParticipantRepository;

    @MockitoBean
    private InviteRedemptionLimiter inviteRedemptionLimiter;

    @MockitoBean
    private GameDefinitionRepository gameDefinitionRepository;

    @MockitoBean
    private GameVersionRepository gameVersionRepository;

    @MockitoBean
    private EventGameRepository eventGameRepository;

    @MockitoBean
    private ScoreEntryRepository scoreEntryRepository;

    @MockitoBean
    private ScoreboardSnapshotRepository scoreboardSnapshotRepository;

    @MockitoBean
    private ScoreboardSnapshotRowRepository scoreboardSnapshotRowRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private PictureRepository pictureRepository;

    @MockitoBean
    private MediaAssetRepository mediaAssetRepository;

    @MockitoBean
    private EventStateRepository eventStateRepository;

    @Test
    void contextLoads() {
    }
}