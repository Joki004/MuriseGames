"use client";

import { useEffect, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import { FixtureResultButton } from "@/component/Event/Scoring/FixtureResultButton";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Select,
} from "@/component/ui";
import {
  ScheduleController,
  type ResourceView,
  type ScheduleDetail,
  type ScheduleSummary,
} from "@/modules/Event/Schedule";


import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import {
  TeamController,
  type TeamResponse,
} from "@/modules/Event/Team";

function toLocalDateTimeInput(iso?: string): string {
  const d = iso ? new Date(iso) : new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function SchedulePanel() {
  const { event, canManage } = useEventDetail();

  const [schedules, setSchedules] = useState<ScheduleSummary[]>([]);
  const [activeId, setActiveId] = useState<string>("");
  const [detail, setDetail] = useState<ScheduleDetail | null>(null);
const [games, setGames] = useState<EventGameResponse[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [resources, setResources] = useState<ResourceView[]>([]);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Balanced generator state
  const [genStartAt, setGenStartAt] = useState(() =>
    toLocalDateTimeInput(event.startsAt),
  );
  const [slotMinutes, setSlotMinutes] = useState("20");
  const [breakMinutes, setBreakMinutes] = useState("5");
  const [selectedGames, setSelectedGames] = useState<string[]>([]);

  // Manual fixture form state
  const [fixGameId, setFixGameId] = useState("");
  const [fixRound, setFixRound] = useState("1");
  const [fixStart, setFixStart] = useState(() =>
    toLocalDateTimeInput(event.startsAt),
  );
  const [fixEnd, setFixEnd] = useState(() =>
    toLocalDateTimeInput(
      new Date(new Date(event.startsAt).getTime() + 20 * 60_000).toISOString(),
    ),
  );
  const [fixTeamIds, setFixTeamIds] = useState<string[]>([]);
  const [fixBye, setFixBye] = useState(false);
  const [fixResourceId, setFixResourceId] = useState("");

  // Resource creation state
  const [resType, setResType] = useState<"room" | "table" | "station">("room");
  const [resName, setResName] = useState("");
  const [resCap, setResCap] = useState("2");

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      try {
        const [schedList, gameList, teamList, resList] = await Promise.all([
          ScheduleController.listSchedules(event.id),
          EventGameController.list(event.id),
          TeamController.list(event.id),
          ScheduleController.listResources(event.id),
        ]);

        if (!active) return;
        setSchedules(schedList);
        setGames(gameList);
        setTeams(teamList.teams.filter((t) => t.active));
        setResources(resList);
        setSelectedGames(gameList.map((g) => g.id));
        if (gameList[0]) setFixGameId(gameList[0].id);

        const first = schedList[0];
        if (first) {
          setActiveId(first.id);
          const loaded = await ScheduleController.getSchedule(
            event.id,
            first.id,
          );
          if (active) setDetail(loaded);
        }
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load schedule.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, [event.id]);

  async function selectSchedule(id: string) {
    setActiveId(id);
    setBusy(true);
    setError(null);
    try {
      const loaded = await ScheduleController.getSchedule(event.id, id);
      setDetail(loaded);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not load schedule.");
    } finally {
      setBusy(false);
    }
  }

  async function refreshListAndSelect(created: ScheduleDetail) {
    const list = await ScheduleController.listSchedules(event.id);
    setSchedules(list);
    setActiveId(created.id);
    setDetail(created);
  }

  async function handleGenerateBalanced() {
    if (selectedGames.length === 0) {
      setError("Select at least one game to generate a schedule.");
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const created = await ScheduleController.generateBalancedDraft(event.id, {
        startAt: new Date(genStartAt).toISOString(),
        slotMinutes: Number(slotMinutes) || 20,
        breakMinutes: Number(breakMinutes) || 0,
        eventGameIds: selectedGames,
      });
      await refreshListAndSelect(created);
      setNotice(`Generated Balanced draft v${created.version}.`);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not generate schedule.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateManual() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const created = await ScheduleController.createManualDraft(event.id);
      await refreshListAndSelect(created);
      setNotice(`Created empty manual draft v${created.version}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not create draft.");
    } finally {
      setBusy(false);
    }
  }

  async function handleAddFixture() {
    if (!detail) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await ScheduleController.addFixture(event.id, detail.id, {
        eventGameId: fixGameId,
        roundNumber: Number(fixRound) || 1,
        startAt: new Date(fixStart).toISOString(),
        endAt: new Date(fixEnd).toISOString(),
        teamIds: fixTeamIds,
        bye: fixBye,
        resourceId: fixResourceId || null,
      });
      setDetail(updated);
      setFixTeamIds([]);
      setFixBye(false);
      setNotice("Fixture added.");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not add fixture.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteFixture(fixtureId: string) {
    if (!detail) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await ScheduleController.deleteFixture(
        event.id,
        detail.id,
        fixtureId,
      );
      setDetail(updated);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not remove fixture.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateResource() {
    if (!resName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const created = await ScheduleController.createResource(event.id, {
        type: resType,
        name: resName.trim(),
        capacity: Number(resCap) || 1,
      });
      setResources((prev) => [...prev, created]);
      setResName("");
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not create resource.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-muted">Loading schedule…</p>;
  }

  const isDraft = detail?.status === "draft";
  const teamNameById = new Map(teams.map((t) => [t.id, t.name]));

  return (
    <div className="space-y-6">
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {canManage && (
        <Card className="space-y-4">
          <CardHeader>
            <CardTitle>Generate or start a draft schedule</CardTitle>
            <CardDescription>
              Create a Balanced Game Night draft or an empty manual draft. If
              teams or games change before play, generate a new version.
            </CardDescription>
          </CardHeader>

          <div className="grid gap-4 sm:grid-cols-3">
            <Input
              label="Start time"
              type="datetime-local"
              value={genStartAt}
              onChange={(e) => setGenStartAt(e.target.value)}
            />
            <Input
              label="Slot minutes"
              type="number"
              min={5}
              max={120}
              value={slotMinutes}
              onChange={(e) => setSlotMinutes(e.target.value)}
            />
            <Input
              label="Break minutes"
              type="number"
              min={0}
              max={60}
              value={breakMinutes}
              onChange={(e) => setBreakMinutes(e.target.value)}
            />
          </div>

          <div>
            <p className="mb-2 text-sm font-medium">Activities to schedule</p>
            <div className="flex flex-wrap gap-2">
              {games.map((g) => {
                const checked = selectedGames.includes(g.id);
                return (
                  <label
                    key={g.id}
                    className="flex cursor-pointer items-center gap-2 rounded-[var(--ui-radius-control)] border border-border px-3 py-1.5 text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() =>
                        setSelectedGames((prev) =>
                          checked
                            ? prev.filter((id) => id !== g.id)
                            : [...prev, g.id],
                        )
                      }
                    />
                    {g.name}
                  </label>
                );
              })}
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button
              loading={busy}
              onClick={() => void handleGenerateBalanced()}
            >
              Generate Balanced Draft
            </Button>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void handleCreateManual()}
            >
              Create Empty Manual Draft
            </Button>
          </div>
        </Card>
      )}

      <Card className="space-y-4">
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle>Running order</CardTitle>
              <CardDescription>
                {schedules.length === 0
                  ? "No schedule versions yet."
                  : "Review fixtures, timings, and room assignments."}
              </CardDescription>
            </div>
            {schedules.length > 0 && (
              <Select
                label="Version"
                value={activeId}
                onChange={(e) => void selectSchedule(e.target.value)}
              >
                {schedules.map((s) => (
                  <option key={s.id} value={s.id}>
                    v{s.version} · {s.mode} ({s.status})
                  </option>
                ))}
              </Select>
            )}
          </div>
        </CardHeader>

        {detail && detail.warnings.length > 0 && (
          <Alert tone="info">
            <ul className="list-disc pl-5 text-sm">
              {detail.warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          </Alert>
        )}

        {detail && detail.fixtures.length === 0 && (
          <p className="text-sm text-muted">
            No fixtures in this version yet.
          </p>
        )}

        {detail && detail.fixtures.length > 0 && (
          <ul className="space-y-2">
            {detail.fixtures.map((f) => (
              <li
                key={f.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
              >
                <div>
                  <p className="font-semibold">
                    {f.gameName} · Round {f.roundNumber}
                  </p>
                  <p className="text-xs text-muted">
                    {new Date(f.startAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}{" "}
                    –{" "}
                    {new Date(f.endAt).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                    {f.resourceName ? ` · ${f.resourceName}` : ""}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {f.teams.map((t) => (
                    <Badge
                      key={t.teamId}
                      tone={t.side === "bye" ? "warning" : "neutral"}
                    >
                      {t.teamName}
                      {t.side === "bye" ? " (bye)" : ""}
                    </Badge>
                  ))}
                  {canManage && isDraft && (
                    <Button
                      variant="secondary"
                      disabled={busy}
                      onClick={() => void handleDeleteFixture(f.id)}
                    >
                      Remove
                    </Button>
                  )}
                  {canManage && !isDraft && detail && (
                    <FixtureResultButton
                      eventId={event.id}
                      fixture={{ ...f, fixtureId: f.id }}
                      onChanged={() => void selectSchedule(detail.id)}
                    />
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {canManage && detail && isDraft && (
        <Card className="space-y-4">
          <CardHeader>
            <CardTitle>Add manual fixture to v{detail.version}</CardTitle>
            <CardDescription>
              Team and room overlaps are checked automatically.
            </CardDescription>
          </CardHeader>

          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label="Activity"
              value={fixGameId}
              onChange={(e) => setFixGameId(e.target.value)}
            >
              {games.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </Select>
            <Input
              label="Round number"
              type="number"
              min={1}
              value={fixRound}
              onChange={(e) => setFixRound(e.target.value)}
            />
            <Input
              label="Start"
              type="datetime-local"
              value={fixStart}
              onChange={(e) => setFixStart(e.target.value)}
            />
            <Input
              label="End"
              type="datetime-local"
              value={fixEnd}
              onChange={(e) => setFixEnd(e.target.value)}
            />
            <Select
              label="Room / Station (optional)"
              value={fixResourceId}
              onChange={(e) => setFixResourceId(e.target.value)}
            >
              <option value="">None</option>
              {resources.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name} ({r.type})
                </option>
              ))}
            </Select>
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input
                type="checkbox"
                checked={fixBye}
                onChange={(e) => setFixBye(e.target.checked)}
              />
              Explicit bye (single team)
            </label>
          </div>

          <div>
            <p className="mb-2 text-sm font-medium">Teams</p>
            <div className="flex flex-wrap gap-2">
              {teams.map((t) => {
                const checked = fixTeamIds.includes(t.id);
                return (
                  <label
                    key={t.id}
                    className="flex cursor-pointer items-center gap-2 rounded-[var(--ui-radius-control)] border border-border px-3 py-1.5 text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() =>
                        setFixTeamIds((prev) =>
                          checked
                            ? prev.filter((id) => id !== t.id)
                            : [...prev, t.id],
                        )
                      }
                    />
                    {t.name}
                  </label>
                );
              })}
            </div>
          </div>

          <Button
            disabled={busy || !fixGameId || fixTeamIds.length === 0}
            onClick={() => void handleAddFixture()}
          >
            Add fixture
          </Button>
        </Card>
      )}

      {canManage && detail && teams.length >= 2 && (
        <Card className="space-y-3">
          <CardHeader>
            <CardTitle>Opponent-count matrix</CardTitle>
            <CardDescription>
              Head-to-head encounters across this schedule version.
            </CardDescription>
          </CardHeader>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-border">
                  <th className="py-2 pr-3 font-medium">Team</th>
                  {teams.map((t) => (
                    <th key={t.id} className="px-2 py-2 font-medium">
                      {t.name}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {teams.map((row) => (
                  <tr key={row.id} className="border-b border-border/50">
                    <td className="py-2 pr-3 font-medium">
                      {teamNameById.get(row.id)}
                    </td>
                    {teams.map((col) => (
                      <td key={col.id} className="px-2 py-2 font-mono">
                        {row.id === col.id
                          ? "—"
                          : (detail.opponentMatrix[row.id]?.[col.id] ?? 0)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {canManage && (
        <Card className="space-y-4">
          <CardHeader>
            <CardTitle>Rooms & stations</CardTitle>
            <CardDescription>
              Define play areas so fixtures cannot double-book the same space.
            </CardDescription>
          </CardHeader>

          <div className="grid gap-3 sm:grid-cols-3">
            <Select
              label="Type"
              value={resType}
              onChange={(e) =>
                setResType(e.target.value as "room" | "table" | "station")
              }
            >
              <option value="room">Room</option>
              <option value="table">Table</option>
              <option value="station">Station</option>
            </Select>
            <Input
              label="Name"
              placeholder="Living Room / Table A"
              value={resName}
              onChange={(e) => setResName(e.target.value)}
            />
            <Input
              label="Capacity"
              type="number"
              min={1}
              value={resCap}
              onChange={(e) => setResCap(e.target.value)}
            />
          </div>

          <Button
            variant="secondary"
            disabled={busy || !resName.trim()}
            onClick={() => void handleCreateResource()}
          >
            Add resource
          </Button>
        </Card>
      )}
    </div>
  );
}