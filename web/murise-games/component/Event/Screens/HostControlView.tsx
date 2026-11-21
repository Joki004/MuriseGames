"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { PageShell } from "@/component/Layout/PageShell";
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
import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { EventGameController, type EventGameResponse } from "@/modules/Event/EventGame";
import { EventLiveController, useEventSnapshot } from "@/modules/Event/Live";
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

export function HostControlView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs, error, applySnapshot, refetch } =
    useEventSnapshot(eventId);

  const [games, setGames] = useState<EventGameResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [selectedGameId, setSelectedGameId] = useState("");
  const [phase, setPhase] = useState("active_round");
  const [seconds, setSeconds] = useState("60");
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmReset, setConfirmReset] = useState(false);

useEffect(() => {
    void Promise.all([
      EventGameController.list(eventId),
      TeamController.list(eventId),
    ]).then(([g, t]) => {
      setGames(g);
      setTeams(t.teams);
    });
  }, [eventId]);

  useEffect(() => {
    if (!snapshot?.deadlineAt || snapshot.paused) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [snapshot?.deadlineAt, snapshot?.paused]);

  const remainingMs = (() => {
    if (!snapshot) return null;
    if (snapshot.paused && snapshot.pausedRemainingMs != null) {
      return snapshot.pausedRemainingMs;
    }
    if (snapshot.deadlineAt) {
      return Math.max(0, Date.parse(snapshot.deadlineAt) - (nowMs + clockOffsetMs));
    }
    return null;
  })();

  const activeGame = games.find((g) => g.id === snapshot?.currentEventGameId);
  const readySet = new Set(snapshot?.readyTeamIds ?? []);

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
        title="Host control deck"
        description="Select the active game, drive the server timer, and monitor team readiness."
        action={
          <div className="flex flex-wrap gap-2">
            <Link href={`/events/${eventId}/tv`} className="ui-button ui-button--secondary">
              Open TV screen
            </Link>
            <Link href={`/events/${eventId}`} className="ui-button ui-button--secondary">
              Back to event
            </Link>
          </div>
        }
      >
        {(error || actionError) && (
          <Alert tone="error">{actionError ?? error}</Alert>
        )}

        <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
          <Card className="space-y-5">
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <div>
                  <CardTitle>Current live state</CardTitle>
                  <CardDescription>
                    {activeGame ? activeGame.name : "No game selected"}
                  </CardDescription>
                </div>
                <div className="flex gap-2">
                  <Badge tone={snapshot?.paused ? "warning" : "success"}>
                    {snapshot?.paused ? "Paused" : snapshot?.phase ?? "lobby"}
                  </Badge>
                  <Badge>v{snapshot?.version ?? 0}</Badge>
                </div>
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
                        applySnapshot(await EventLiveController.pause(eventId))
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
                        applySnapshot(await EventLiveController.resume(eventId))
                      )
                    }
                  >
                    Resume
                  </Button>
                )}
                <Button variant="secondary" disabled={busy} onClick={() => void refetch()}>
                  Sync
                </Button>
              </div>
            </div>

            <div className="space-y-3 border-t border-border pt-4">
              <Select
                label="Active game"
                value={selectedGameId}
                onChange={(e) => setSelectedGameId(e.target.value)}
              >
                <option value="">— No game (Lobby / Intermission) —</option>
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
                      const updated = await EventLiveController.updateState(eventId, {
                        currentEventGameId: selectedGameId || null,
                        phase: phase.trim(),
                        durationSeconds: Number.isNaN(duration) ? null : duration,
                      });
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
      </PageShell>
    </RequireAccount>
  );
}