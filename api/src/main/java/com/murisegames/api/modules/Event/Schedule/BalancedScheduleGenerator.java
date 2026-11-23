package com.murisegames.api.modules.Event.Schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class BalancedScheduleGenerator {

    private BalancedScheduleGenerator() {
    }

    public record GameInput(
            UUID eventGameId,
            String name,
            String participationMode
    ) {
    }

    public record PlannedSlot(
            UUID eventGameId,
            int roundNumber,
            Instant startAt,
            Instant endAt,
            List<UUID> teamIds,
            boolean bye
    ) {
    }

    public record GenerationResult(
            long seed,
            List<PlannedSlot> slots,
            Map<UUID, Map<UUID, Integer>> opponentMatrix,
            List<String> warnings
    ) {
    }

    public static GenerationResult generate(
            List<UUID> teamIds,
            List<GameInput> games,
            Instant startAt,
            int slotMinutes,
            int breakMinutes,
            Long requestedSeed
    ) {
        return generate(teamIds, games, startAt, slotMinutes, breakMinutes, requestedSeed, null);
    }

    public static GenerationResult generate(
            List<UUID> teamIds,
            List<GameInput> games,
            Instant startAt,
            int slotMinutes,
            int breakMinutes,
            Long requestedSeed,
            Integer maxConcurrentStations
    ) {
        long seed = requestedSeed != null ? requestedSeed : System.currentTimeMillis();
        Random rng = new Random(seed);

        Map<UUID, Map<UUID, Integer>> matrix = new LinkedHashMap<>();
        Map<UUID, Integer> consecutiveRest = new LinkedHashMap<>();
        Map<UUID, Integer> totalRest = new LinkedHashMap<>();
        Map<UUID, Integer> peakConsecutiveRest = new LinkedHashMap<>();
        for (UUID a : teamIds) {
            Map<UUID, Integer> row = new LinkedHashMap<>();
            for (UUID b : teamIds) {
                row.put(b, 0);
            }
            matrix.put(a, row);
            consecutiveRest.put(a, 0);
            totalRest.put(a, 0);
            peakConsecutiveRest.put(a, 0);
        }

        List<PlannedSlot> slots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (teamIds.isEmpty() || games.isEmpty()) {
            return new GenerationResult(seed, List.of(), matrix, List.of());
        }

        // Preserve leading all_teams games as openers, merge all station games
        // (head_to_head + team_solo) into one concurrent rotation pool, and place
        // remaining all_teams games (e.g., Final Rehearsal) at the end.
        List<GameInput> openingAllTeams = new ArrayList<>();
        List<GameInput> stationGames = new ArrayList<>();
        List<GameInput> closingAllTeams = new ArrayList<>();

        boolean seenStation = false;
        for (GameInput game : games) {
            if ("all_teams".equals(game.participationMode())) {
                if (!seenStation) {
                    openingAllTeams.add(game);
                } else {
                    closingAllTeams.add(game);
                }
            } else {
                seenStation = true;
                stationGames.add(game);
            }
        }

        Instant cursor = startAt;

        for (GameInput game : openingAllTeams) {
            Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
            slots.add(new PlannedSlot(game.eventGameId(), 1, cursor, end, List.copyOf(teamIds), false));
            cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
        }

        int stationCap = maxConcurrentStations != null
                ? Math.max(1, maxConcurrentStations)
                : Integer.MAX_VALUE;

        cursor = scheduleStationPool(
                stationGames,
                teamIds,
                stationCap,
                matrix,
                consecutiveRest,
                totalRest,
                peakConsecutiveRest,
                cursor,
                slotMinutes,
                breakMinutes,
                rng,
                slots,
                warnings
        );

        for (GameInput game : closingAllTeams) {
            Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
            slots.add(new PlannedSlot(game.eventGameId(), 1, cursor, end, List.copyOf(teamIds), false));
            cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
        }

        int minOpp = Integer.MAX_VALUE;
        int maxOpp = Integer.MIN_VALUE;
        for (int a = 0; a < teamIds.size(); a++) {
            for (int b = a + 1; b < teamIds.size(); b++) {
                int count = matrix.get(teamIds.get(a)).get(teamIds.get(b));
                minOpp = Math.min(minOpp, count);
                maxOpp = Math.max(maxOpp, count);
            }
        }
        if (teamIds.size() >= 2 && (maxOpp - minOpp) > 1) {
            warnings.add("Unavoidable opponent-count spread: pairs meet between "
                    + minOpp + " and " + maxOpp + " times.");
        }

        int worstStreak = peakConsecutiveRest.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (worstStreak > 1) {
            warnings.add("Station capacity or team count required a team to rest "
                    + worstStreak + " consecutive windows. Add rooms or adjust manually if needed.");
        }

        return new GenerationResult(seed, List.copyOf(slots), matrix, List.copyOf(warnings));
    }

    private static Instant scheduleStationPool(
            List<GameInput> stationGames,
            List<UUID> teamIds,
            int stationCap,
            Map<UUID, Map<UUID, Integer>> matrix,
            Map<UUID, Integer> consecutiveRest,
            Map<UUID, Integer> totalRest,
            Map<UUID, Integer> peakConsecutiveRest,
            Instant startCursor,
            int slotMinutes,
            int breakMinutes,
            Random rng,
            List<PlannedSlot> slots,
            List<String> warnings
    ) {
        List<GameInput> soloGames = new ArrayList<>();
        List<GameInput> h2hGames = new ArrayList<>();
        for (GameInput game : stationGames) {
            if ("team_solo".equals(game.participationMode())) {
                soloGames.add(game);
            } else if ("head_to_head".equals(game.participationMode())) {
                if (teamIds.size() < 2) {
                    warnings.add(game.name() + ": needs at least 2 teams for head-to-head.");
                } else {
                    h2hGames.add(game);
                }
            }
        }

        if (soloGames.isEmpty() && h2hGames.isEmpty()) {
            return startCursor;
        }
        Map<UUID, Map<UUID, UUID>> opponentsByGame =
                planOpponents(h2hGames, teamIds, rng);
        Map<UUID, Set<UUID>> remainingByGame = new LinkedHashMap<>();
        Map<UUID, Integer> roundCounterByGame = new LinkedHashMap<>();
        for (GameInput g : soloGames) {
            remainingByGame.put(g.eventGameId(), new LinkedHashSet<>(teamIds));
            roundCounterByGame.put(g.eventGameId(), 1);
        }
        for (GameInput g : h2hGames) {
            remainingByGame.put(g.eventGameId(), new LinkedHashSet<>(teamIds));
            roundCounterByGame.put(g.eventGameId(), 1);
        }

        Instant cursor = startCursor;
        int windowIndex = 0;

        while (remainingByGame.values().stream().anyMatch(set -> !set.isEmpty())) {
            Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
            Set<UUID> busyInWindow = new LinkedHashSet<>();
            List<PlannedSlot> windowSlots = new ArrayList<>();
            int stationsUsed = 0;

            // 1. Critical-path solo games: sort by most remaining teams first, rotating ties.
            List<GameInput> orderedSolo = orderByRemainingDesc(soloGames, remainingByGame, windowIndex);
            for (GameInput game : orderedSolo) {
                if (stationsUsed >= stationCap) break;
                UUID gameId = game.eventGameId();
                Set<UUID> remaining = remainingByGame.get(gameId);
                List<UUID> available = remaining.stream()
                        .filter(id -> !busyInWindow.contains(id))
                        .toList();
                if (!available.isEmpty()) {
                    UUID chosen = pickPriorityTeam(available, consecutiveRest, totalRest, rng);
                    busyInWindow.add(chosen);
                    remaining.remove(chosen);
                    int round = roundCounterByGame.merge(gameId, 1, Integer::sum) - 1;
                    windowSlots.add(new PlannedSlot(
                            gameId, round, cursor, end, List.of(chosen), false
                    ));
                    stationsUsed++;
                }
            }

            // 2. Diverse head-to-head games: at most 1 match per distinct game first.
            List<GameInput> orderedH2h = orderByRemainingDesc(h2hGames, remainingByGame, windowIndex);
            for (GameInput game : orderedH2h) {
                if (stationsUsed >= stationCap) break;
                if (scheduleOnePair(
                        game,
                        opponentsByGame,
                        remainingByGame,
                        roundCounterByGame,
                        busyInWindow,
                        matrix,
                        consecutiveRest,
                        totalRest,
                        rng,
                        cursor,
                        end,
                        windowSlots
                )) {
                    stationsUsed++;
                }
            }

            // 3. Overflow pass: if teams AND stations are still free, run additional
            // parallel matches on head-to-head games so fewer teams sit idle.
            boolean addedExtra;
            do {
                addedExtra = false;
                for (GameInput game : orderedH2h) {
                    if (stationsUsed >= stationCap) break;
                    if (scheduleOnePair(
                            game,
                            opponentsByGame,
                            remainingByGame,
                            roundCounterByGame,
                            busyInWindow,
                            matrix,
                            consecutiveRest,
                            totalRest,
                            rng,
                            cursor,
                            end,
                            windowSlots
                    )) {
                        stationsUsed++;
                        addedExtra = true;
                    }
                }
            } while (addedExtra && stationsUsed < stationCap);

            // 4. Fold genuine odd-team H2H byes directly into that team's free window
            // instead of creating empty trailing windows.
            for (GameInput game : orderedH2h) {
                UUID gameId = game.eventGameId();
                Set<UUID> remaining = remainingByGame.get(gameId);
                if (remaining.size() == 1) {
                    UUID loneTeam = remaining.iterator().next();
                    if (!busyInWindow.contains(loneTeam)) {
                        remaining.remove(loneTeam);
                        int round = roundCounterByGame.merge(gameId, 1, Integer::sum) - 1;
                        windowSlots.add(new PlannedSlot(
                                gameId, round, cursor, end, List.of(loneTeam), true
                        ));
                        warnings.add(game.name() + ": odd team count produced 1 explicit bye.");
                    }
                }
            }

            // 5. Update rest streaks for teams sitting out this window.
            for (UUID teamId : teamIds) {
                if (busyInWindow.contains(teamId)) {
                    consecutiveRest.put(teamId, 0);
                } else {
                    int streak = consecutiveRest.merge(teamId, 1, Integer::sum);
                    totalRest.merge(teamId, 1, Integer::sum);
                    peakConsecutiveRest.merge(teamId, streak, Math::max);
                }
            }

            if (!windowSlots.isEmpty()) {
                slots.addAll(windowSlots);
                cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
            }
            windowIndex++;
        }

        return cursor;
    }

    private static List<GameInput> orderByRemainingDesc(
            List<GameInput> games,
            Map<UUID, Set<UUID>> remainingByGame,
            int windowIndex
    ) {
        if (games.isEmpty()) return List.of();
        List<GameInput> rotated = new ArrayList<>(games);
        Collections.rotate(rotated, -(windowIndex % rotated.size()));
        rotated.sort(Comparator.comparingInt(
                (GameInput g) -> remainingByGame.get(g.eventGameId()).size()
        ).reversed());
        return rotated;
    }

    private static boolean scheduleOnePair(
            GameInput game,
            Map<UUID, Map<UUID, UUID>> opponentsByGame,
            Map<UUID, Set<UUID>> remainingByGame,
            Map<UUID, Integer> roundCounterByGame,
            Set<UUID> busyInWindow,
            Map<UUID, Map<UUID, Integer>> matrix,
            Map<UUID, Integer> consecutiveRest,
            Map<UUID, Integer> totalRest,
            Random rng,
            Instant cursor,
            Instant end,
            List<PlannedSlot> windowSlots
    ) {
        UUID gameId = game.eventGameId();
        Set<UUID> remaining = remainingByGame.get(gameId);
        Map<UUID, UUID> opponents = opponentsByGame.get(gameId);

        List<UUID> candidates = remaining.stream()
                .filter(teamId -> {
                    UUID opponent = opponents.get(teamId);

                    return opponent != null
                            && remaining.contains(opponent)
                            && !busyInWindow.contains(teamId)
                            && !busyInWindow.contains(opponent);
                })
                .toList();

        if (candidates.isEmpty()) {
            return false;
        }

        // Preserve rest priority, but only among complete available pairs.
        UUID first = pickPriorityTeam(
                candidates,
                consecutiveRest,
                totalRest,
                rng
        );

        UUID opponent = opponents.get(first);

        busyInWindow.add(first);
        busyInWindow.add(opponent);

        remaining.remove(first);
        remaining.remove(opponent);

        // Count only matches actually placed into the schedule.
        matrix.get(first).merge(opponent, 1, Integer::sum);
        matrix.get(opponent).merge(first, 1, Integer::sum);

        int round = roundCounterByGame.merge(
                gameId,
                1,
                Integer::sum
        ) - 1;

        windowSlots.add(new PlannedSlot(
                gameId,
                round,
                cursor,
                end,
                List.of(first, opponent),
                false
        ));

        return true;
    }

    private static UUID pickPriorityTeam(
            List<UUID> candidates,
            Map<UUID, Integer> consecutiveRest,
            Map<UUID, Integer> totalRest,
            Random rng
    ) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, rng);
        return shuffled.stream()
                .max(Comparator
                        .comparingInt((UUID id) -> consecutiveRest.getOrDefault(id, 0))
                        .thenComparingInt(id -> totalRest.getOrDefault(id, 0)))
                .orElseThrow();
    }

    private static UUID pickBestOpponent(
            UUID first,
            List<UUID> candidates,
            Map<UUID, Map<UUID, Integer>> matrix,
            Map<UUID, Integer> consecutiveRest,
            Map<UUID, Integer> totalRest,
            Random rng
    ) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, rng);
        return shuffled.stream()
                .min(Comparator
                        .comparingInt((UUID cand) -> matrix.get(first).getOrDefault(cand, 0))
                        .thenComparingInt(cand -> -consecutiveRest.getOrDefault(cand, 0))
                        .thenComparingInt(cand -> -totalRest.getOrDefault(cand, 0)))
                .orElseThrow();
    }

    private static Map<UUID, Map<UUID, UUID>> planOpponents(
            List<GameInput> headToHeadGames,
            List<UUID> teamIds,
            Random rng
    ) {
        if (headToHeadGames.isEmpty()) {
            return Map.of();
        }

        List<UUID> rotation = new ArrayList<>(teamIds);
        Collections.shuffle(rotation, rng);

        // A null position represents a bye.
        if (rotation.size() % 2 != 0) {
            rotation.add(null);
        }

        Map<UUID, Map<UUID, UUID>> opponentsByGame = new LinkedHashMap<>();

        for (GameInput game : headToHeadGames) {
            Map<UUID, UUID> opponents = new LinkedHashMap<>();

            for (int index = 0; index < rotation.size() / 2; index++) {
                UUID first = rotation.get(index);
                UUID second = rotation.get(rotation.size() - 1 - index);

                if (first != null) {
                    opponents.put(first, second);
                }
                if (second != null) {
                    opponents.put(second, first);
                }
            }

            // A null opponent deliberately identifies this game's bye team.
            // Do not use Map.copyOf here: it rejects null values.
            opponentsByGame.put(game.eventGameId(), opponents);

            // Keep the first position fixed and rotate all other positions.
            rotation.add(1, rotation.removeLast());
        }

        return opponentsByGame;
    }
}