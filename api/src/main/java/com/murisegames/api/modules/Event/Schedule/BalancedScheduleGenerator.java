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
        long seed = requestedSeed != null ? requestedSeed : System.currentTimeMillis();
        Random rng = new Random(seed);

        Map<UUID, Map<UUID, Integer>> matrix = new LinkedHashMap<>();
        for (UUID a : teamIds) {
            Map<UUID, Integer> row = new LinkedHashMap<>();
            for (UUID b : teamIds) {
                row.put(b, 0);
            }
            matrix.put(a, row);
        }

        List<PlannedSlot> slots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Instant cursor = startAt;

        for (GameInput game : games) {
            String mode = game.participationMode();

            if ("all_teams".equals(mode)) {
                Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
                slots.add(new PlannedSlot(
                        game.eventGameId(), 1, cursor, end, List.copyOf(teamIds), false
                ));
                cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
                continue;
            }

            if ("team_solo".equals(mode)) {
                int round = 1;
                for (UUID teamId : teamIds) {
                    Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
                    slots.add(new PlannedSlot(
                            game.eventGameId(), round++, cursor, end, List.of(teamId), false
                    ));
                    cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
                }
                continue;
            }

            if ("head_to_head".equals(mode)) {
                if (teamIds.size() < 2) {
                    warnings.add(game.name() + ": needs at least 2 teams for head-to-head.");
                    continue;
                }

                List<UUID> pool = new ArrayList<>(teamIds);
                Collections.shuffle(pool, rng);

                Instant end = cursor.plus(slotMinutes, ChronoUnit.MINUTES);
                int round = 1;

                while (pool.size() >= 2) {
                    UUID first = pool.removeFirst();
                    UUID bestOpponent = pool.stream()
                            .min(Comparator.comparingInt(candidate -> matrix.get(first).get(candidate)))
                            .orElseThrow();
                    pool.remove(bestOpponent);

                    matrix.get(first).merge(bestOpponent, 1, Integer::sum);
                    matrix.get(bestOpponent).merge(first, 1, Integer::sum);

                    slots.add(new PlannedSlot(
                            game.eventGameId(),
                            round++,
                            cursor,
                            end,
                            List.of(first, bestOpponent),
                            false
                    ));
                }

                if (pool.size() == 1) {
                    UUID byeTeam = pool.removeFirst();
                    slots.add(new PlannedSlot(
                            game.eventGameId(), round, cursor, end, List.of(byeTeam), true
                    ));
                    warnings.add(game.name() + ": odd team count produced 1 explicit bye.");
                }

                cursor = end.plus(breakMinutes, ChronoUnit.MINUTES);
            }
        }

        // Check opponent-count spread for transparency
        int minOpp = Integer.MAX_VALUE;
        int maxOpp = Integer.MIN_VALUE;
        for (int i = 0; i < teamIds.size(); i++) {
            for (int j = i + 1; j < teamIds.size(); j++) {
                int count = matrix.get(teamIds.get(i)).get(teamIds.get(j));
                minOpp = Math.min(minOpp, count);
                maxOpp = Math.max(maxOpp, count);
            }
        }
        if (teamIds.size() >= 2 && (maxOpp - minOpp) > 1) {
            warnings.add("Unavoidable opponent-count spread: pairs meet between "
                    + minOpp + " and " + maxOpp + " times.");
        }

        return new GenerationResult(seed, List.copyOf(slots), matrix, List.copyOf(warnings));
    }
}