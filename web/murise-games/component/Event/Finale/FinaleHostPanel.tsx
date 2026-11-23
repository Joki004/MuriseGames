"use client";

import { useEffect, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import { RevealCard, stageLabel } from "@/component/Event/Finale/RevealBoard";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Dialog,
} from "@/component/ui";
import {
  FinaleController,
  type ReviewBoardResponse,
  type ReviewItem,
  type ReviewVerdict,
  type RevealView,
} from "@/modules/Event/Finale";
import {
  TitleController,
  type TeamTitleResponse,
} from "@/modules/Event/Title";

/**
 * GM-016 + GM-018 host tools:
 * 1. Review guesses that don't exactly match a title or approved alias.
 * 2. Reveal each team's title once guessing is closed and nothing is pending.
 */
export function FinaleHostPanel() {
  const { event, canManage } = useEventDetail();

  const [reviews, setReviews] = useState<ReviewBoardResponse | null>(null);
  const [reveals, setReveals] = useState<RevealView[]>([]);
  const [titles, setTitles] = useState<TeamTitleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const [showAll, setShowAll] = useState(false);

  const [confirmTeam, setConfirmTeam] = useState<TeamTitleResponse | null>(
    null,
  );
  const [leakTeam, setLeakTeam] = useState<TeamTitleResponse | null>(null);
  // Keep the same requestId for a retried reveal so it can't double-apply.
  const [revealRequestIds, setRevealRequestIds] = useState<
    Record<string, string>
  >({});

  useEffect(() => {
    if (!canManage) return;
    let active = true;

    void Promise.all([
      FinaleController.getReviews(event.id),
      FinaleController.getReveals(event.id),
      TitleController.board(event.id),
    ])
      .then(([reviewBoard, revealList, titleBoard]) => {
        if (!active) return;
        setReviews(reviewBoard);
        setReveals(revealList.reveals);
        setTitles(titleBoard.teams);
        setError(null);
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load the finale.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [event.id, canManage, tick]);

  if (!canManage) return null;

  async function decide(item: ReviewItem, verdict: ReviewVerdict) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await FinaleController.decide(
        event.id,
        item.guessId,
        verdict,
      );
      setReviews(updated);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not save the review.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function executeLeak(team: TeamTitleResponse) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await FinaleController.recordLeak(event.id, team.teamId);
      setReviews(updated);
      setLeakTeam(null);
      setNotice(
        `Leak recorded: every team except ${team.teamName} got +2. Announce it to everyone.`,
      );
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not record the leak.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function executeReveal(team: TeamTitleResponse) {
    setBusy(true);
    setError(null);
    setNotice(null);

    const requestId = revealRequestIds[team.teamId] ?? crypto.randomUUID();
    setRevealRequestIds((ids) => ({ ...ids, [team.teamId]: requestId }));

    try {
      const result = await FinaleController.reveal(event.id, {
        requestId,
        teamId: team.teamId,
      });
      setReveals((list) => [
        ...list.filter((r) => r.sceneId !== result.sceneId),
        result,
      ]);
      setRevealRequestIds((ids) => {
        const next = { ...ids };
        delete next[team.teamId];
        return next;
      });
      setConfirmTeam(null);
      setNotice(`${team.teamName}'s title is revealed on every screen.`);
      // Reviews for this scene are now locked; refresh their state.
      setReviews(await FinaleController.getReviews(event.id));
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not reveal this title.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-muted">Loading finale…</p>;
  }

  if (!reviews) {
    return (
      <Card>
        <Alert tone="error">{error ?? "Could not load the finale."}</Alert>
      </Card>
    );
  }

  const isClosed = reviews.guessingStatus === "closed";
  const revealedTeamIds = new Set(reveals.map((r) => r.teamId));
  const leakedTeamIds = new Set(reviews.leakedTeamIds);
  const pendingByTeam = reviews.items.reduce<Record<string, number>>(
    (acc, item) => {
      if (item.verdict === "pending" && item.targetTeamId) {
        acc[item.targetTeamId] = (acc[item.targetTeamId] ?? 0) + 1;
      }
      return acc;
    },
    {},
  );
  const visibleItems = showAll
    ? reviews.items
    : reviews.items.filter((i) => i.verdict === "pending");

  return (
    <Card className="space-y-6">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Finale · guess reviews &amp; title reveals</CardTitle>
            <CardDescription>
              Exact titles and approved aliases are marked correct
              automatically. Decide everything else, then reveal each title.
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone={reviews.pendingCount > 0 ? "warning" : "success"}>
              {reviews.pendingCount} to review
            </Badge>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => {
                setLoading(true);
                setTick((n) => n + 1);
              }}
            >
              Refresh
            </Button>
          </div>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {/* ---------------- 1. Review queue ---------------- */}
      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">
            {showAll ? "All guesses" : "Guesses waiting for your decision"}
          </h3>
          <Button variant="secondary" onClick={() => setShowAll((v) => !v)}>
            {showAll ? "Show only pending" : `Show all (${reviews.items.length})`}
          </Button>
        </div>

        {visibleItems.length === 0 ? (
          <p className="text-sm text-muted">
            {showAll
              ? "No guesses have been submitted yet."
              : "Nothing to review right now."}
          </p>
        ) : (
          <ul className="space-y-2">
            {visibleItems.map((item) => (
              <li
                key={item.guessId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
              >
                <div className="min-w-0 space-y-1">
                  <p>
                    <span className="font-semibold">{item.guessingTeamName}</span>{" "}
                    <span className="text-muted">
                      guessed {stageLabel(item.stage)} for
                    </span>{" "}
                    <span className="font-semibold">{item.targetTeamName}</span>
                  </p>
                  <p className="text-base">&ldquo;{item.rawGuess}&rdquo;</p>
                  <p className="text-xs text-muted">
                    Registered title: {item.targetTitle ?? "not registered"}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {item.autoMatched && !item.hostDecision && (
                    <Badge tone="success">Auto-match</Badge>
                  )}
                  {item.hostDecision && (
                    <Badge
                      tone={item.hostDecision === "correct" ? "success" : "danger"}
                    >
                      Host: {item.hostDecision}
                    </Badge>
                  )}
                  {item.verdict === "pending" && (
                    <Badge tone="warning">Pending</Badge>
                  )}
                  {item.sceneRevealed ? (
                    <Badge>Revealed</Badge>
                  ) : (
                    <>
                      <Button
                        variant={
                          item.verdict === "correct" ? "primary" : "secondary"
                        }
                        disabled={busy}
                        onClick={() => void decide(item, "correct")}
                      >
                        Correct
                      </Button>
                      <Button
                        variant={
                          item.verdict === "wrong" ? "danger" : "secondary"
                        }
                        disabled={busy}
                        onClick={() => void decide(item, "wrong")}
                      >
                        Wrong
                      </Button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ---------------- 2. Reveals ---------------- */}
      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Reveal titles</h3>

        {!isClosed && (
          <Alert tone="info">
            Close all guessing in the clue release panel before the first
            reveal. You can already review guesses.
          </Alert>
        )}

        <ul className="space-y-3">
          {titles.map((team) => {
            const reveal = reveals.find((r) => r.teamId === team.teamId);
            if (reveal) {
              return (
                <li key={team.teamId}>
                  <RevealCard reveal={reveal} />
                </li>
              );
            }

            const pending = pendingByTeam[team.teamId] ?? 0;
            const reason = !team.title
              ? "No title registered"
              : !isClosed
                ? "Guessing still open"
                : pending > 0
                  ? `${pending} guess${pending === 1 ? "" : "es"} to review first`
                  : null;

            return (
              <li
                key={team.teamId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
              >
                <div className="flex items-center gap-2">
                  <span
                    className="h-3 w-3 rounded-full"
                    style={{ backgroundColor: team.teamColor }}
                  />
                  <span className="font-semibold">{team.teamName}</span>
                  <span className="text-muted">
                    {team.title ? `· ${team.title}` : ""}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {leakedTeamIds.has(team.teamId) ? (
                    <Badge tone="warning">Leak recorded (+2 to others)</Badge>
                  ) : (
                    team.title && (
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => setLeakTeam(team)}
                      >
                        Record leak
                      </Button>
                    )
                  )}
                  {reason && <span className="text-xs text-muted">{reason}</span>}
                  <Button
                    disabled={busy || reason !== null || revealedTeamIds.has(team.teamId)}
                    onClick={() => setConfirmTeam(team)}
                  >
                    {revealRequestIds[team.teamId] ? "Retry reveal" : "Reveal title"}
                  </Button>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      <Dialog
        open={leakTeam !== null}
        onOpenChange={(open) => {
          if (!open) setLeakTeam(null);
        }}
        busy={busy}
        title={`Record a leak of ${leakTeam?.teamName ?? "this team"}'s title?`}
        description="Use this only if the exact title became known before guesses locked. Every other team gets +2, added on top of their guessing points. Announce it to everyone. This cannot be undone."
      >
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            variant="danger"
            loading={busy}
            onClick={() => {
              if (leakTeam) void executeLeak(leakTeam);
            }}
          >
            Confirm leak (+2 to every other team)
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => setLeakTeam(null)}
          >
            Cancel
          </Button>
        </div>
      </Dialog>

      <Dialog
        open={confirmTeam !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmTeam(null);
        }}
        busy={busy}
        title={`Reveal ${confirmTeam?.teamName ?? "this team"}'s title?`}
        description="The title, the teams that guessed it and their points appear on every screen, and the points are added to the scoreboard. This cannot be undone."
      >
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            loading={busy}
            onClick={() => {
              if (confirmTeam) void executeReveal(confirmTeam);
            }}
          >
            Confirm reveal
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => setConfirmTeam(null)}
          >
            Cancel
          </Button>
        </div>
      </Dialog>
    </Card>
  );
}
