"use client";

import { useEffect, useState } from "react";
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
import {
  createUuid,
  EventOperationsController,
  loadOfflineQueue,
  normalizeSheetKey,
  saveOfflineQueue,
  type OfflineQueuedRow,
  type OfflineScoreCategory,
  type PrintPacketResponse,
} from "@/modules/Event/Operations/operations";
import type { TeamResponse } from "@/modules/Event/Team";

interface HostOperationsPanelProps {
  eventId: string;
  teams: TeamResponse[];
  onSynced?: () => void;
}

export function HostOperationsPanel({
  eventId,
  teams,
  onSynced,
}: HostOperationsPanelProps) {
  const [queue, setQueue] = useState<OfflineQueuedRow[]>([]);
  const [sheetKey, setSheetKey] = useState("table-sheet-1");
  const [rowNumber, setRowNumber] = useState("1");
  const [teamId, setTeamId] = useState("");
  const [category, setCategory] = useState<OfflineScoreCategory>("game_points");
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState("");
  const [syncing, setSyncing] = useState(false);
  const [syncFeedback, setSyncFeedback] = useState<string | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  const [printScope, setPrintScope] = useState<"public" | "host">("public");
  const [includeSecrets, setIncludeSecrets] = useState(false);
  const [packet, setPacket] = useState<PrintPacketResponse | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [pendingPrint, setPendingPrint] = useState(false);
  const [loadingPacket, setLoadingPacket] = useState(false);
  const [printError, setPrintError] = useState<string | null>(null);

  useEffect(() => {
    setQueue(loadOfflineQueue(eventId));
  }, [eventId]);

  useEffect(() => {
    if (!teamId && teams.length > 0) {
      setTeamId(teams[0].id);
    }
  }, [teams, teamId]);

  // Trigger window.print() only after packet state has committed and preview dialog is closed.
  useEffect(() => {
    if (!pendingPrint || !packet || previewOpen) return;
    let raf1 = 0;
    let raf2 = 0;
    raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        setPendingPrint(false);
        window.print();
      });
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  }, [pendingPrint, packet, previewOpen]);

  function persistQueue(next: OfflineQueuedRow[]) {
    setQueue(next);
    saveOfflineQueue(eventId, next);
  }

  function handleEnqueueRow() {
    setSyncError(null);
    setSyncFeedback(null);

    const parsedRowNum = Number.parseInt(rowNumber, 10);
    const parsedDelta = Number.parseFloat(delta);
    const normalizedSheet = normalizeSheetKey(sheetKey);
    const trimmedReason = reason.trim();
    const team = teams.find((t) => t.id === teamId);

    if (!normalizedSheet || normalizedSheet.length > 60) {
      setSyncError("Sheet key must be 1–60 characters.");
      return;
    }
    if (!Number.isInteger(parsedRowNum) || parsedRowNum < 1) {
      setSyncError("Row number must be 1 or greater.");
      return;
    }
    if (!team) {
      setSyncError("Select a valid team.");
      return;
    }
    if (Number.isNaN(parsedDelta) || parsedDelta === 0) {
      setSyncError("Points delta must be a non-zero number.");
      return;
    }
    if (!trimmedReason || trimmedReason.length > 220) {
      setSyncError("Reason is required (max 220 characters).");
      return;
    }

    const duplicate = queue.some(
      (r) =>
        normalizeSheetKey(r.sheetKey) === normalizedSheet &&
        r.rowNumber === parsedRowNum,
    );
    if (duplicate) {
      setSyncError(
        `Row #${parsedRowNum} on "${normalizedSheet}" is already in the offline queue.`,
      );
      return;
    }

    const nextRow: OfflineQueuedRow = {
      id: createUuid(),
      sheetKey: normalizedSheet,
      rowNumber: parsedRowNum,
      teamId: team.id,
      teamName: team.name,
      category,
      delta: parsedDelta,
      reason: trimmedReason,
      queuedAt: new Date().toISOString(),
    };

    persistQueue([...queue, nextRow]);
    setRowNumber(String(parsedRowNum + 1));
    setDelta("");
    setReason("");
  }

  async function handleSyncNow() {
    if (queue.length === 0) return;
    setSyncing(true);
    setSyncError(null);
    setSyncFeedback(null);
    try {
      const res = await EventOperationsController.syncOfflineRows(
        eventId,
        queue,
      );

      const resultByKey = new Map(
        res.results.map((r) => [
          `${normalizeSheetKey(r.sheetKey)}#${r.rowNumber}`,
          r,
        ]),
      );

      // Keep only rows that reported a conflict, annotated with the mismatch reason.
      const remainingConflicts: OfflineQueuedRow[] = queue.flatMap((q) => {
        const outcome = resultByKey.get(
          `${normalizeSheetKey(q.sheetKey)}#${q.rowNumber}`,
        );
        if (outcome?.status === "conflict") {
          return [{ ...q, conflictDetail: outcome.conflictDetail }];
        }
        return [];
      });

      persistQueue(remainingConflicts);

      if (res.conflictCount > 0) {
        setSyncError(
          `${res.conflictCount} row(s) conflicted with previously synced entries and were kept in the queue for review.`,
        );
      }

      if (res.newlyAppliedCount > 0 || res.duplicateSkippedCount > 0) {
        setSyncFeedback(
          `Applied ${res.newlyAppliedCount} new row(s)` +
            (res.duplicateSkippedCount > 0
              ? ` (${res.duplicateSkippedCount} identical row(s) already synced).`
              : "."),
        );
      }

      if (res.newlyAppliedCount > 0) {
        onSynced?.();
      }
    } catch (err: unknown) {
      setSyncError(
        err instanceof Error ? err.message : "Offline sync failed.",
      );
    } finally {
      setSyncing(false);
    }
  }

  async function handlePreparePacket(autoPrint: boolean) {
    setLoadingPacket(true);
    setPrintError(null);
    try {
      const data = await EventOperationsController.fetchPrintPacket(
        eventId,
        printScope,
        printScope === "host" && includeSecrets,
      );
      setPacket(data);
      if (autoPrint) {
        setPreviewOpen(false);
        setPendingPrint(true);
      } else {
        setPreviewOpen(true);
      }
    } catch (err: unknown) {
      setPrintError(
        err instanceof Error ? err.message : "Could not load print packet.",
      );
    } finally {
      setLoadingPacket(false);
    }
  }

  return (
    <>
      <div className="mt-6 grid gap-6 lg:grid-cols-2" data-no-print="true">
        <Card className="space-y-4">
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <div>
                <CardTitle>Offline paper score sync</CardTitle>
                <CardDescription>
                  Queue manual score sheet rows offline; each (sheet, row) is
                  posted idempotently and checked for conflicts.
                </CardDescription>
              </div>
              <Badge tone={queue.length > 0 ? "warning" : "neutral"}>
                {queue.length} queued
              </Badge>
            </div>
          </CardHeader>

          {syncError && <Alert tone="error">{syncError}</Alert>}
          {syncFeedback && <Alert tone="success">{syncFeedback}</Alert>}

          <div className="grid gap-3 sm:grid-cols-[1fr_6.5rem]">
            <Input
              label="Sheet key"
              maxLength={60}
              value={sheetKey}
              onChange={(e) => setSheetKey(e.target.value)}
              placeholder="table-sheet-1"
            />
            <Input
              label="Row #"
              type="number"
              min={1}
              value={rowNumber}
              onChange={(e) => setRowNumber(e.target.value)}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-3">
            <Select
              label="Team"
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
            >
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>

            <Select
              label="Category"
              value={category}
              onChange={(e) =>
                setCategory(e.target.value as OfflineScoreCategory)
              }
            >
              <option value="game_points">Game points</option>
              <option value="bonus">Bonus</option>
              <option value="penalty">Penalty</option>
              <option value="adjustment">Adjustment</option>
            </Select>

            <Input
              label="Points delta"
              type="number"
              step="0.5"
              value={delta}
              onChange={(e) => setDelta(e.target.value)}
              placeholder="10"
            />
          </div>

          <Input
            label="Reason / Round reference"
            maxLength={220}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Round 2 table sheet verified"
          />

          <div className="flex flex-wrap items-center justify-between gap-2">
            <Button
              variant="secondary"
              type="button"
              onClick={handleEnqueueRow}
            >
              Add row to offline queue
            </Button>

            <Button
              type="button"
              disabled={syncing || queue.length === 0}
              onClick={() => void handleSyncNow()}
            >
              {syncing ? "Syncing…" : `Sync ${queue.length} row(s) now`}
            </Button>
          </div>

          {queue.length > 0 && (
            <ul className="space-y-2 border-t border-border pt-3 text-sm">
              {queue.map((item) => (
                <li
                  key={item.id}
                  className="flex items-start justify-between gap-2 rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="font-medium">
                      {item.sheetKey} #{item.rowNumber} · {item.teamName} (
                      {item.delta > 0 ? `+${item.delta}` : item.delta})
                    </p>
                    <p className="truncate text-xs text-muted">
                      {item.category} — {item.reason}
                    </p>
                    {item.conflictDetail && (
                      <p className="mt-1 text-xs font-medium text-danger">
                        Conflict: {item.conflictDetail}
                      </p>
                    )}
                  </div>
                  <Button
                    variant="secondary"
                    type="button"
                    onClick={() =>
                      persistQueue(queue.filter((q) => q.id !== item.id))
                    }
                  >
                    Remove
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="space-y-4">
          <CardHeader>
            <CardTitle>Printable backup packet</CardTitle>
            <CardDescription>
              Generate clean black-on-white schedule, blank score sheets, and
              approved clues for offline fallback.
            </CardDescription>
          </CardHeader>

          {printError && <Alert tone="error">{printError}</Alert>}

          <Select
            label="Packet privacy scope"
            value={printScope}
            onChange={(e) => {
              const next = e.target.value as "public" | "host";
              setPrintScope(next);
              if (next === "public") setIncludeSecrets(false);
            }}
          >
            <option value="public">
              Public / Player-safe (released clues only, no secret titles)
            </option>
            <option value="host">
              Host binder (all approved clues &amp; house rules)
            </option>
          </Select>

          {printScope === "host" && (
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input
                type="checkbox"
                checked={includeSecrets}
                onChange={(e) => setIncludeSecrets(e.target.checked)}
              />
              Include secret Scene titles &amp; aliases (sealed host envelope
              only)
            </label>
          )}

          <p className="text-xs text-muted">
            Note: Alibi &amp; Social Case prompts are physical-only and are not
            stored in the event database.
          </p>

          <div className="flex flex-wrap gap-2 pt-2">
            <Button
              variant="secondary"
              type="button"
              disabled={loadingPacket}
              onClick={() => void handlePreparePacket(false)}
            >
              {loadingPacket ? "Preparing…" : "Preview packet"}
            </Button>
            <Button
              type="button"
              disabled={loadingPacket}
              onClick={() => void handlePreparePacket(true)}
            >
              Print packet
            </Button>
          </div>
        </Card>
      </div>

      <Dialog
        open={previewOpen && packet != null}
        onOpenChange={setPreviewOpen}
        title={packet ? `${packet.eventName} — Print Packet` : "Print Packet"}
        description={
          packet
            ? `Scope: ${packet.scope.toUpperCase()} · ${packet.teams.length} teams · ${packet.fixtures.length} fixtures`
            : undefined
        }
      >
        {packet && (
          <div className="space-y-4 text-sm">
            <div className="flex flex-wrap gap-2">
              <Badge
                tone={packet.secretTitles.length > 0 ? "danger" : "success"}
              >
                {packet.secretTitles.length > 0
                  ? "CONTAINS SECRET TITLES"
                  : "PUBLIC SAFE"}
              </Badge>
              <Badge>{packet.fixtures.length} fixtures</Badge>
              <Badge>{packet.clues.length} clues included</Badge>
            </div>

            <ul className="list-disc space-y-1 pl-5 text-muted">
              {packet.operationalNotes.map((note, idx) => (
                <li key={idx}>{note}</li>
              ))}
            </ul>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="secondary"
                type="button"
                onClick={() => setPreviewOpen(false)}
              >
                Close
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setPreviewOpen(false);
                  setPendingPrint(true);
                }}
              >
                Print now
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {packet && (
        <section aria-hidden="true" className="print-packet-sheet hidden">
          <h1 className="text-2xl font-bold">{packet.eventName}</h1>
          <p className="text-sm">
            Scope: {packet.scope.toUpperCase()} · Venue:{" "}
            {packet.venue ?? "TBD"} · Generated:{" "}
            {new Date(packet.generatedAt).toLocaleString()}
          </p>

          <h2 className="mt-4 text-lg font-bold">
            1. Teams &amp; Manual Tally Sheet
          </h2>
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Team</th>
                <th>Round 1</th>
                <th>Round 2</th>
                <th>Round 3</th>
                <th>Bonus / Penalty</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {packet.teams.map((t, i) => (
                <tr key={t.id}>
                  <td>{i + 1}</td>
                  <td>{t.name}</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
              ))}
            </tbody>
          </table>

          {packet.fixtures.length > 0 && (
            <>
              <h2 className="mt-4 text-lg font-bold">2. Match Schedule</h2>
              <table>
                <thead>
                  <tr>
                    <th>Round</th>
                    <th>Time</th>
                    <th>Game</th>
                    <th>Station</th>
                    <th>Matchup</th>
                  </tr>
                </thead>
                <tbody>
                  {packet.fixtures.map((f, idx) => (
                    <tr key={idx}>
                      <td>{f.roundNumber}</td>
                      <td>
                        {new Date(f.startAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </td>
                      <td>{f.gameName}</td>
                      <td>{f.resourceName ?? "—"}</td>
                      <td>
                        {f.teams.map((ft) => ft.teamName).join(" vs ")}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {packet.clues.length > 0 && (
            <div className="print-page-break">
              <h2 className="mt-4 text-lg font-bold">
                3.{" "}
                {packet.scope === "host"
                  ? "Approved Scene Clues (All Stages)"
                  : "Released Scene Clues"}
              </h2>
              <table>
                <thead>
                  <tr>
                    <th>Team</th>
                    <th>Stage</th>
                    <th>Clue</th>
                  </tr>
                </thead>
                <tbody>
                  {packet.clues.map((c, idx) => (
                    <tr key={idx}>
                      <td>{c.teamName}</td>
                      <td>Stage {c.stage}</td>
                      <td>{c.body}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {packet.secretTitles.length > 0 && (
            <div className="print-page-break">
              <h2 className="mt-4 text-lg font-bold">
                4. CONFIDENTIAL — Host Secret Scene Titles
              </h2>
              <table>
                <thead>
                  <tr>
                    <th>Team</th>
                    <th>Secret Title</th>
                    <th>Scope</th>
                    <th>Accepted Aliases</th>
                  </tr>
                </thead>
                <tbody>
                  {packet.secretTitles.map((s) => (
                    <tr key={s.teamId}>
                      <td>{s.teamName}</td>
                      <td>{s.title}</td>
                      <td>{s.answerScope}</td>
                      <td>{s.aliases.join(", ") || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </>
  );
}