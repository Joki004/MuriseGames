"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { HostOperationsPanel } from "@/component/Event/Operations/HostOperationsPanel";
import { ClueReleasePanel } from "@/component/Event/Clue/ClueReleasePanel";
import { FinaleHostPanel } from "@/component/Event/Finale/FinaleHostPanel";
import { ScoreboardPanel } from "@/component/Event/Scoring/ScoreboardPanel";
import {
  PREMIERE_GAME_CODE,
  REHEARSAL_GAME_CODE,
} from "@/component/Event/Finale/PerformanceOrder";
import { PageShell } from "@/component/Layout/PageShell";
import { FixtureResultButton } from "@/component/Event/Scoring/FixtureResultButton";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Dialog,
  Input,
  Select,
} from "@/component/ui";
import { ApiService, extractApiErrorMessage } from "@/modules/Api/api.service";
import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import { EventLiveController, useEventSnapshot } from "@/modules/Event/Live";
import type { ScheduleDetail, ScheduleSummary } from "@/modules/Event/Schedule";
import { TeamController, type TeamResponse } from "@/modules/Event/Team";

const PRESETS = [
  { label: "Lobby (no timer)", phase: "lobby", seconds: 0 },
  { label: "Briefing (60s)", phase: "briefing", seconds: 60 },
  { label: "Sensory Chain Turn (45s)", phase: "active_round", seconds: 45 },
  { label: "Unified Alibi Prep (90s)", phase: "prep", seconds: 90 },
  { label: "Final Rehearsal (20m)", phase: "rehearsal", seconds: 1200 },
];

function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

function formatSlotTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function HostControlView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs, error, applySnapshot, refetch } =
    useEventSnapshot(eventId);

  const [games, setGames] = useState<EventGameResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [schedule, setSchedule] = useState<ScheduleDetail | null>(null);
  const [selectedWindowStartAt, setSelectedWindowStartAt] = useState("");
  const [selectedGameId, setSelectedGameId] = useState("");
  const [phase, setPhase] = useState("active_round");
  const [seconds, setSeconds] = useState("60");
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);

  const liveVersion = snapshot?.version ?? 0;
  const [resultsTick, setResultsTick] = useState(0);

  useEffect(() => {
    void Promise.all([
      EventGameController.list(eventId),
      TeamController.list(eventId),
      ApiService.get<ScheduleSummary[]>(
        `/api/v1/events/${encodeURIComponent(eventId)}/schedules`,
      ),
    ]).then(async ([g, t, schedRes]) => {
      setGames(g);
      setTeams(t.teams);
      const list = schedRes.data ?? [];
      const chosen =
        list.find((s) => s.status === "published") ?? list[0] ?? null;
      if (chosen) {
        const detailRes = await ApiService.get<ScheduleDetail>(
          `/api/v1/events/${encodeURIComponent(eventId)}/schedules/${encodeURIComponent(chosen.id)}`,
        );
        setSchedule(detailRes.data ?? null);
      }
    }).catch(() => {
      // Keep what is shown; the next change retries.
    });
    // Reload when the live state changes so finished matches show as done.
  }, [eventId, liveVersion, resultsTick]);

  useEffect(() => {
    if (!snapshot?.deadlineAt || snapshot.paused) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [snapshot?.deadlineAt, snapshot?.paused]);

  const timeWindows = useMemo(() => {
    if (!schedule) return [];
    const map = new Map<
      string,
      {
        startAt: string;
        endAt: string;
        fixtures: ScheduleDetail["fixtures"];
      }
    >();
    for (const f of schedule.fixtures) {
      const existing = map.get(f.startAt);
      if (existing) {
        existing.fixtures.push(f);
      } else {
        map.set(f.startAt, {
          startAt: f.startAt,
          endAt: f.endAt,
          fixtures: [f],
        });
      }
    }
    return Array.from(map.values()).sort(
      (a, b) => Date.parse(a.startAt) - Date.parse(b.startAt),
    );
  }, [schedule]);

  const remainingMs = (() => {
    if (!snapshot) return null;
    if (snapshot.paused && snapshot.pausedRemainingMs != null) {
      return snapshot.pausedRemainingMs;
    }
    if (snapshot.deadlineAt) {
      return Math.max(
        0,
        Date.parse(snapshot.deadlineAt) - (nowMs + clockOffsetMs),
      );
    }
    return null;
  })();

  const activeGame = games.find((g) => g.id === snapshot?.currentEventGameId);
  const readySet = new Set(snapshot?.readyTeamIds ?? []);
  const codeOf = (eventGameId: string) =>
    games.find((g) => g.id === eventGameId)?.code ?? null;

  const windowDone = (w: (typeof timeWindows)[number]) =>
    w.fixtures.every(
      (f) =>
        f.status === "completed" ||
        f.status === "voided" ||
        f.teams.some((t) => t.side === "bye") ||
        // Unscored activities (rehearsal, premiere) never get a result.
        codeOf(f.eventGameId) === REHEARSAL_GAME_CODE ||
        codeOf(f.eventGameId) === PREMIERE_GAME_CODE,
    );
  const currentWindowIndex = timeWindows.findIndex(
    (w) => w.startAt === snapshot?.currentWindowStartAt,
  );
  const nextWindowIndex = timeWindows.findIndex(
    (w, i) => i > currentWindowIndex && !windowDone(w),
  );

  /** One tap: show this time slot's games on every screen and start its timer. */
  function startWindow(w: (typeof timeWindows)[number]) {
    const gameIds = Array.from(new Set(w.fixtures.map((f) => f.eventGameId)));
    const codes = gameIds.map(codeOf);
    const phaseFor = codes.includes(REHEARSAL_GAME_CODE)
      ? "rehearsal"
      : codes.includes(PREMIERE_GAME_CODE)
        ? "finale"
        : "active_round";
    const duration = Math.max(
      0,
      Math.round((Date.parse(w.endAt) - Date.parse(w.startAt)) / 1000),
    );
    void run(async () => {
      applySnapshot(
        await EventLiveController.updateState(eventId, {
          currentEventGameId: gameIds.length === 1 ? gameIds[0] : null,
          windowStartAt: w.startAt,
          phase: phaseFor,
          durationSeconds: duration,
        }),
      );
    });
  }

  function backToLobby() {
    void run(async () => {
      applySnapshot(
        await EventLiveController.updateState(eventId, {
          currentEventGameId: null,
          windowStartAt: null,
          phase: "lobby",
          durationSeconds: 0,
        }),
      );
    });
  }

  function handleSelectWindow(startAt: string) {
    setSelectedWindowStartAt(startAt);
    if (!startAt) return;
    const win = timeWindows.find((w) => w.startAt === startAt);
    if (!win) return;

    const distinctGameIds = Array.from(
      new Set(win.fixtures.map((f) => f.eventGameId)),
    );
    setSelectedGameId(distinctGameIds.length === 1 ? distinctGameIds[0] : "");
    setPhase("active_round");

    const durSec = Math.max(
      0,
      Math.round((Date.parse(win.endAt) - Date.parse(win.startAt)) / 1000),
    );
    if (durSec > 0) {
      setSeconds(String(durSec));
    }
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
    } catch (err: unknown) {
      setActionError(extractApiErrorMessage(err, "Host command failed."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <RequireAccount>
      <PageShell
        title="Host"
        description="Run the night from this page: start each activity, watch the clock, record results, release clues and reveal the titles."
        action={
          <div className="flex flex-wrap gap-2">
            <Link
              href={`/events/${eventId}/tv`}
              target="_blank"
              className="ui-button ui-button--secondary"
            >
              Open TV screen ↗
            </Link>
            <Link
              href={`/events/${eventId}/alibi-game`}
              className="ui-button ui-button--secondary"
            >
              Alibi game
            </Link>
            <Link
              href={`/events/${eventId}`}
              className="ui-button ui-button--secondary"
            >
              Back to event
            </Link>
          </div>
        }
      >
        {(error || actionError) && (
          <Alert tone="error">{actionError ?? error}</Alert>
        )}

        <div
          data-no-print="true"
          className="grid gap-6 lg:grid-cols-[1.2fr_1fr]"
        >
          <Card className="space-y-5">
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <div>
                  <CardTitle>Now on every screen</CardTitle>
                  <CardDescription>
                    {snapshot?.currentWindowStartAt
                      ? `Time slot ${formatSlotTime(snapshot.currentWindowStartAt)}${activeGame ? ` · ${activeGame.name}` : ""}`
                      : activeGame
                        ? activeGame.name
                        : "Lobby: nothing running"}
                  </CardDescription>
                </div>
                <Badge tone={snapshot?.paused ? "warning" : "success"}>
                  {snapshot?.paused
                    ? "Paused"
                    : (snapshot?.phase ?? "lobby").replaceAll("_", " ")}
                </Badge>
              </div>
            </CardHeader>

            <div className="flex items-center justify-between rounded-[var(--ui-radius-control)] border border-border bg-background/60 p-4">
              <span className="font-mono text-4xl font-bold">
                {remainingMs != null ? formatMs(remainingMs) : "--:--"}
              </span>
              <div className="flex flex-wrap gap-2">
                {snapshot?.deadlineAt && !snapshot.paused && (
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      void run(async () =>
                        applySnapshot(await EventLiveController.pause(eventId)),
                      )
                    }
                  >
                    Pause
                  </Button>
                )}
                {snapshot?.paused && (
                  <Button
                    disabled={busy}
                    onClick={() =>
                      void run(async () =>
                        applySnapshot(
                          await EventLiveController.resume(eventId),
                        ),
                      )
                    }
                  >
                    Resume
                  </Button>
                )}
                <Button variant="secondary" disabled={busy} onClick={backToLobby}>
                  Back to lobby
                </Button>
              </div>
            </div>

            {(snapshot?.activeFixtures?.length ?? 0) > 0 && (
              <div className="space-y-2 border-t border-border pt-3">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted">
                  Matches now ({snapshot!.activeFixtures!.length}) · record each result here
                </p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {snapshot!.activeFixtures!.map((f) => {
                    const isBye = f.teams.some((t) => t.side === "bye");
                    return (
                      <div
                        key={f.fixtureId}
                        className="rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-3 py-2 text-sm"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <Badge>{f.gameName}</Badge>
                          {f.resourceName && (
                            <span className="text-xs text-muted">
                              {f.resourceName}
                            </span>
                          )}
                        </div>
                        <p className="mt-1 font-semibold">
                          {isBye
                            ? `${f.teams[0]?.teamName ?? "Team"} (Resting / Bye)`
                            : f.teams.map((t) => t.teamName).join(" vs ")}
                        </p>
                        {!isBye && (
                          <div className="mt-2">
                            <FixtureResultButton
                              eventId={eventId}
                              fixture={f}
                              onChanged={() => {
                                void refetch();
                                setResultsTick((n) => n + 1);
                              }}
                            />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            <details className="border-t border-border pt-4">
              <summary className="cursor-pointer text-sm font-semibold text-muted">
                Manual control (only if the run of show doesn&apos;t fit)
              </summary>
            <div className="mt-3 space-y-3">
              {timeWindows.length > 0 && (
                <Select
                  label="Scheduled time window (launches concurrent games together)"
                  value={selectedWindowStartAt}
                  onChange={(e) => handleSelectWindow(e.target.value)}
                >
                  <option value="">— Manual single-game mode —</option>
                  {timeWindows.map((w) => {
                    const summary = w.fixtures
                      .map((f) => {
                        const isBye = f.teams.some((t) => t.side === "bye");
                        const label = isBye
                          ? `${f.teams[0]?.teamName ?? "Team"} (Bye)`
                          : f.teams.map((t) => t.teamName).join(" vs ");
                        return `${f.gameName}: ${label}`;
                      })
                      .join(" | ");
                    return (
                      <option key={w.startAt} value={w.startAt}>
                        {formatSlotTime(w.startAt)}–{formatSlotTime(w.endAt)} ·{" "}
                        {summary}
                      </option>
                    );
                  })}
                </Select>
              )}

              <Select
                label="Active game (single-game override)"
                value={selectedGameId}
                onChange={(e) => {
                  setSelectedWindowStartAt("");
                  setSelectedGameId(e.target.value);
                }}
              >
                <option value="">
                  — No single game (Lobby / Multi-game window) —
                </option>
                {games.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name} ({g.status})
                  </option>
                ))}
              </Select>

              <div className="flex flex-wrap gap-2">
                {PRESETS.map((p) => (
                  <Button
                    key={p.label}
                    variant="secondary"
                    onClick={() => {
                      setPhase(p.phase);
                      setSeconds(String(p.seconds));
                    }}
                  >
                    {p.label}
                  </Button>
                ))}
              </div>

              <div className="grid gap-3 sm:grid-cols-[1fr_8rem]">
                <Input
                  label="Phase label"
                  value={phase}
                  onChange={(e) => setPhase(e.target.value)}
                />
                <Input
                  label="Duration (s)"
                  type="number"
                  min={0}
                  value={seconds}
                  onChange={(e) => setSeconds(e.target.value)}
                />
              </div>

              <div className="flex flex-wrap justify-between gap-2 pt-2">
                <Button
                  disabled={busy || !phase.trim()}
                  onClick={() =>
                    void run(async () => {
                      const duration = Number.parseInt(seconds, 10);
                      const updated = await EventLiveController.updateState(
                        eventId,
                        {
                          currentEventGameId: selectedGameId || null,
                          windowStartAt: selectedWindowStartAt || null,
                          phase: phase.trim(),
                          durationSeconds: Number.isNaN(duration)
                            ? null
                            : duration,
                        },
                      );
                      applySnapshot(updated);
                    })
                  }
                >
                  Push to all screens
                </Button>

                <Button
                  variant="danger"
                  disabled={busy}
                  onClick={() => setConfirmReset(true)}
                >
                  Reset state
                </Button>
              </div>
            </div>
            </details>
          </Card>

          <div className="space-y-6">
          <Card className="space-y-3">
            <CardHeader>
              <CardTitle>Run of show</CardTitle>
              <CardDescription>
                Tap ▶ Start when the room is ready: every screen switches and the
                timer starts.
              </CardDescription>
            </CardHeader>

            {timeWindows.length === 0 ? (
              <p className="text-sm text-muted">
                No published schedule yet. Generate and publish it on the Schedule
                page.
              </p>
            ) : (
              <ol className="space-y-2">
                {timeWindows.map((w, i) => {
                  const isNow = i === currentWindowIndex;
                  const isNext = i === nextWindowIndex;
                  const done = !isNow && windowDone(w);
                  const gameNames = Array.from(new Set(w.fixtures.map((f) => f.gameName)));
                  return (
                    <li
                      key={w.startAt}
                      className={[
                        "flex items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border p-3 text-sm",
                        isNow ? "border-accent" : "border-border",
                        done ? "opacity-60" : "",
                      ].join(" ")}
                    >
                      <div className="min-w-0">
                        <p className="font-semibold">
                          {formatSlotTime(w.startAt)} · {gameNames.join(" + ")}
                        </p>
                        <p className="truncate text-xs text-muted">
                          {w.fixtures
                            .filter((f) => !f.teams.some((t) => t.side === "bye"))
                            .map((f) => f.teams.map((t) => t.teamName).join(" vs "))
                            .join(" · ")}
                        </p>
                      </div>
                      {isNow ? (
                        <Badge tone="accent">Now</Badge>
                      ) : (
                        <div className="flex items-center gap-2">
                          {done && <Badge tone="success">Done</Badge>}
                          <Button
                            variant={isNext ? "primary" : "secondary"}
                            disabled={busy}
                            onClick={() => startWindow(w)}
                          >
                            ▶ Start
                          </Button>
                        </div>
                      )}
                    </li>
                  );
                })}
              </ol>
            )}
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>
                Team readiness ({readySet.size}/{teams.length})
              </CardTitle>
              <CardDescription>
                Teams mark themselves ready from their phone screen.
              </CardDescription>
            </CardHeader>

            <ul className="space-y-2">
              {teams.map((team) => {
                const isReady = readySet.has(team.id);
                return (
                  <li
                    key={team.id}
                    className="flex items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3"
                  >
                    <div className="flex items-center gap-2">
                      <span
                        className="h-3 w-3 rounded-full"
                        style={{ backgroundColor: team.color }}
                      />
                      <span className="font-medium">{team.name}</span>
                    </div>
                    <Badge tone={isReady ? "success" : undefined}>
                      {isReady ? "Ready" : "Waiting"}
                    </Badge>
                  </li>
                );
              })}
            </ul>
          </Card>
          </div>
        </div>

        <section aria-label="Secret Scene" className="space-y-6">
          <ClueReleasePanel />
          <FinaleHostPanel />
        </section>

        <ScoreboardPanel eventId={eventId} />

        <Dialog
          open={confirmReset}
          onOpenChange={setConfirmReset}
          title="Reset live event state?"
          description="This clears the active game, stops the timer, returns all screens to lobby, and resets every team's readiness."
        >
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setConfirmReset(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  const updated = await EventLiveController.reset(eventId);
                  applySnapshot(updated);
                  setConfirmReset(false);
                })
              }
            >
              Confirm reset
            </Button>
          </div>
        </Dialog>

        <details className="rounded-card border border-border bg-surface p-5">
          <summary className="cursor-pointer font-semibold">
            Backup tools: paper scores and printable packet
          </summary>
          <HostOperationsPanel
            eventId={eventId}
            teams={teams}
            onSynced={() => void refetch()}
          />
        </details>
      </PageShell>
    </RequireAccount>
  );
}