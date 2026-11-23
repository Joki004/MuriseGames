package com.murisegames.api.modules.Event.Scoring.Adapter;

import com.murisegames.api.modules.Catalog.GameDefinitionRepository;
import com.murisegames.api.modules.Catalog.GameVersionRepository;
import com.murisegames.api.modules.Event.EventGame.EventGame;
import com.murisegames.api.modules.Event.EventGame.EventGameRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScorePreviewService {

    private final EventGameRepository eventGameRepository;
    private final GameDefinitionRepository definitionRepository;
    private final GameVersionRepository versionRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;

    public enum ResultState {
        NOT_PLAYED,
        CANCELLED,
        CONFIRMED
    }

    public record GameOption(
            UUID eventGameId,
            UUID gameVersionId,
            String name,
            int ruleVersion,
            boolean headToHead,
            List<String> fields,
            int maximumPerField,
            String inputHint,
            String decisionNote,
            String unavailableReason
    ) {
    }

    public record FixtureInput(
            ResultState state,
            List<Integer> team,
            List<Integer> opponent
    ) {
    }

    public record PreviewRequest(
            UUID eventGameId,
            List<FixtureInput> fixtures
    ) {
    }

    public record FixturePreview(
            int number,
            ResultState state,
            Integer rawScore,
            int rawMaximum
    ) {
    }

    public record PreviewResponse(
            String gameName,
            int ruleVersion,
            int confirmedFixtures,
            int expectedFixtures,
            int cancelledFixtures,
            int rawScore,
            int rawMaximum,
            String exactNumerator,
            String exactDenominator,
            String displayPoints,
            String settlementAmount,
            boolean complete,
            String message,
            List<FixturePreview> fixtures
    ) {
    }

    @Transactional(readOnly = true)
    public List<GameOption> options(Jwt jwt, UUID eventId) {
        requireOrganizer(jwt, eventId);

        return eventGameRepository
                .findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId)
                .stream()
                .map(this::describe)
                .toList();
    }

    /**
     * Scoring definitions for specific games, WITHOUT a permission check.
     * Only call this after the caller has authorized the user (e.g. an assigned referee).
     */
    @Transactional(readOnly = true)
    public List<GameOption> optionsForGames(UUID eventId, java.util.Collection<UUID> eventGameIds) {
        return eventGameRepository
                .findByEventIdOrderBySortOrderAscCreatedAtAsc(eventId)
                .stream()
                .filter(game -> eventGameIds.contains(game.getId()))
                .map(this::describe)
                .toList();
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(
            Jwt jwt,
            UUID eventId,
            PreviewRequest request
    ) {
        requireOrganizer(jwt, eventId);

        if (request == null || request.eventGameId() == null) {
            throw badRequest("Choose a game to preview.");
        }

        EventGame eventGame = eventGameRepository
                .findByIdAndEventId(request.eventGameId(), eventId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Event game not found.")
                );

        GameOption option = describe(eventGame);

        if (option.unavailableReason() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    option.unavailableReason()
            );
        }

        List<FixtureInput> fixtures = request.fixtures();

        if (fixtures == null || fixtures.isEmpty() || fixtures.size() > 31) {
            throw badRequest("Add between 1 and 31 planned fixtures.");
        }

        // These seeded solo activities have one complete team appearance.
        // An alternative repetition policy requires another approved contract.
        if (!option.headToHead() && fixtures.size() != 1) {
            throw badRequest(
                    "This rule version has one complete appearance per team."
            );
        }

        int maximumPerFixture =
                option.fields().size() * option.maximumPerField();

        // Includes every expected fixture, including cancelled or unplayed ones.
        // Never improve a score by silently shrinking its denominator.
        int rawMaximum = maximumPerFixture * fixtures.size();
        int rawScore = 0;
        int confirmed = 0;
        int cancelled = 0;

        List<FixturePreview> fixturePreviews = new ArrayList<>();

        for (int index = 0; index < fixtures.size(); index++) {
            FixtureInput fixture = fixtures.get(index);
            int number = index + 1;

            if (fixture == null || fixture.state() == null) {
                throw badRequest(
                        "Choose a result status for fixture " + number + "."
                );
            }

            if (fixture.state() != ResultState.CONFIRMED) {
                if (!empty(fixture.team()) || !empty(fixture.opponent())) {
                    throw badRequest(
                            "Fixture " + number
                                    + " has no confirmed result. "
                                    + "Remove its entered scores first."
                    );
                }

                if (fixture.state() == ResultState.CANCELLED) {
                    cancelled++;
                }

                fixturePreviews.add(new FixturePreview(
                        number,
                        fixture.state(),
                        null,
                        maximumPerFixture
                ));

                continue;
            }

            validateValues(
                    fixture.team(),
                    option,
                    "Your team",
                    number
            );

            if (option.headToHead()) {
                validateValues(
                        fixture.opponent(),
                        option,
                        "Opponent",
                        number
                );

                for (int part = 0; part < option.fields().size(); part++) {
                    int total = fixture.team().get(part)
                            + fixture.opponent().get(part);

                    if (total != option.maximumPerField()) {
                        throw badRequest(
                                "Fixture " + number + ", "
                                        + option.fields().get(part)
                                        + ": the two teams' values must total "
                                        + option.maximumPerField() + "."
                        );
                    }
                }
            } else if (!empty(fixture.opponent())) {
                throw badRequest(
                        "This game does not use an opposing-team result."
                );
            }

            int fixtureRaw = fixture.team()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum();

            rawScore += fixtureRaw;
            confirmed++;

            fixturePreviews.add(new FixturePreview(
                    number,
                    ResultState.CONFIRMED,
                    fixtureRaw,
                    maximumPerFixture
            ));
        }

        boolean complete = confirmed == fixtures.size();

        ExactScore score = ExactScore.of(
                5L * rawScore,
                rawMaximum
        );

        String message;

        if (cancelled > 0) {
            message = "A planned fixture was cancelled. Its opportunity "
                    + "remains in the denominator. Final scoring stays "
                    + "blocked until the schedule issue is resolved.";
        } else if (!complete) {
            message = "Results are still missing. This is a provisional "
                    + "calculation, not a final game score.";
        } else {
            message = "All results in this example are complete. "
                    + "This preview has not saved or published any points.";
        }

        return new PreviewResponse(
                option.name(),
                option.ruleVersion(),
                confirmed,
                fixtures.size(),
                cancelled,
                rawScore,
                rawMaximum,
                score.numerator().toString(),
                score.denominator().toString(),
                score.display(),
                score.settlementAmount().toPlainString(),
                complete,
                message,
                List.copyOf(fixturePreviews)
        );
    }

    private GameOption describe(EventGame eventGame) {
        var definition = definitionRepository
                .findById(eventGame.getGameDefinitionId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Game definition not found.")
                );

        var version = versionRepository
                .findById(eventGame.getGameVersionId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Pinned game rules not found.")
                );

        var adapter = GameScoreAdapters.find(version.getScoringHandler());

        String unavailable = adapter == null
                ? GameScoreAdapters.unavailableReason(
                version.getScoringHandler()
        )
                : adapter.configurationProblem(version);

        if ("voided".equals(eventGame.getStatus())) {
            unavailable = "This activity has been removed from play.";
        } else if (!"published".equals(version.getStatus())
                && !"archived".equals(version.getStatus())) {
            unavailable = "Publish the game rules before previewing scoring.";
        } else if (eventGame.getSettingsSnapshot() != null
                && !eventGame.getSettingsSnapshot().isEmpty()) {
            unavailable = "This event uses custom settings. Review their "
                    + "scoring effect before enabling this adapter.";
        } else if (eventGame.getHouseRulesNote() != null
                && !eventGame.getHouseRulesNote().isBlank()) {
            unavailable = "This event has house rules. Confirm their scoring "
                    + "effect before enabling this adapter.";
        }

        return new GameOption(
                eventGame.getId(),
                version.getId(),
                definition.getName(),
                version.getVersion(),
                adapter != null && adapter.headToHead(),
                adapter == null ? List.of() : adapter.fields(),
                adapter == null ? 0 : adapter.maximumPerField(),
                adapter == null ? "" : adapter.inputHint(),
                adapter == null ? "" : adapter.decisionNote(),
                unavailable
        );
    }

    private void validateValues(
            List<Integer> values,
            GameOption option,
            String side,
            int fixtureNumber
    ) {
        if (values == null || values.size() != option.fields().size()) {
            throw badRequest(
                    "Complete every " + side.toLowerCase()
                            + " field in fixture " + fixtureNumber + "."
            );
        }

        for (int index = 0; index < values.size(); index++) {
            Integer value = values.get(index);

            if (value == null
                    || value < 0
                    || value > option.maximumPerField()) {
                throw badRequest(
                        side + ", " + option.fields().get(index)
                                + ": enter a whole number from 0 to "
                                + option.maximumPerField() + "."
                );
            }
        }
    }

    private void requireOrganizer(Jwt jwt, UUID eventId) {
        membershipService.requireOrganizer(
                eventId,
                userService.extractUserId(jwt)
        );
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}