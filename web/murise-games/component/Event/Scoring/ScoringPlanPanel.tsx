"use client";

import { useEffect, useRef, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import { Rules } from "@/component/Games/Rules";
import { Button } from "@/component/ui/Button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/component/ui/Card";
import { Select, Textarea } from "@/component/ui/Fields";
import { Dialog } from "@/component/ui/Dialog";
import {
  ScoringPlanApi,
  type PlanReview,
  type PlanSetup,
  type PublishPlanRequest,
} from "@/modules/Event/Scoring/scoring-plan";

export function ScoringPlanPanel() {
  const { event, canManage } = useEventDetail();

  if (!canManage) {
    return (
      <Card>
        <CardTitle>Your organizer manages scoring</CardTitle>
        <CardDescription>
          Your organizer reviews the schedule and approves the scoring rules.
        </CardDescription>
      </Card>
    );
  }

  return <OrganizerPlan key={event.id} eventId={event.id} />;
}

function OrganizerPlan({ eventId }: { eventId: string }) {
  const alive = useRef(true);
  const inFlight = useRef(false);

  const [setup, setSetup] = useState<PlanSetup | null>(null);
  const [scheduleId, setScheduleId] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [review, setReview] = useState<PlanReview | null>(null);
  const [approved, setApproved] = useState<string[]>([]);
  const [policyApproved, setPolicyApproved] = useState(false);
  const [note, setNote] = useState("");
  const [pending, setPending] = useState<PublishPlanRequest | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    alive.current = true;
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);

      try {
        const result = await ScoringPlanApi.setup(eventId);
        if (!active) return;

        setSetup(result);
        setScheduleId(result.drafts[0]?.id ?? "");
        setSelected([]);
        setReview(null);
        setApproved([]);
        setPolicyApproved(false);
        setNote("");
        setPending(null);
        setDialogOpen(false);
      } catch (cause) {
        if (active) {
          setError(
            cause instanceof Error ? cause.message : "Could not load scoring.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();

    return () => {
      active = false;
      alive.current = false;
    };
  }, [eventId, reload]);

  function clearReview() {
    setReview(null);
    setApproved([]);
    setPolicyApproved(false);
    setNote("");
    setError(null);
  }

  function toggleGame(id: string) {
    setSelected((current) =>
      current.includes(id)
        ? current.filter((value) => value !== id)
        : [...current, id],
    );
    clearReview();
  }

  async function runReview() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    setReview(null);
    setApproved([]);
    setPolicyApproved(false);
    setNote("");

    try {
      const result = await ScoringPlanApi.review(eventId, {
        scheduleId,
        eventGameIds: selected,
      });
      if (alive.current) setReview(result);
    } catch (cause) {
      if (alive.current) {
        setError(
          cause instanceof Error ? cause.message : "Could not review this plan.",
        );
      }
    } finally {
      inFlight.current = false;
      if (alive.current) setBusy(false);
    }
  }

  async function publish() {
    if (!review || inFlight.current) return;

    inFlight.current = true;
    setBusy(true);
    setError(null);

    // Preserve the exact command after an uncertain network response.
    // Retrying must not create a new publication attempt.
    const command: PublishPlanRequest = pending ?? {
      requestId: crypto.randomUUID(),
      selection: { scheduleId, eventGameIds: [...selected] },
      reviewHash: review.reviewHash,
      approvedVersionIds: [...approved],
      approvePolicy: policyApproved,
      decisionNote: note.trim(),
    };

    setPending(command);

    try {
      const published = await ScoringPlanApi.publish(eventId, command);

      if (alive.current) {
        setSetup((current) =>
          current ? { ...current, published } : current,
        );
        setPending(null);
        setDialogOpen(false);
      }
    } catch (cause) {
      if (alive.current) {
        setError(
          cause instanceof Error
            ? cause.message
            : "Publication was not confirmed.",
        );
      }
    } finally {
      inFlight.current = false;
      if (alive.current) setBusy(false);
    }
  }

  if (loading) {
    return <p role="status" className="text-muted">Opening scoring plan…</p>;
  }

  if (!setup) {
    return (
      <Card>
        <p role="alert">{error ?? "Could not load the scoring plan."}</p>
        <Button className="mt-4" onClick={() => setReload((value) => value + 1)}>
          Try again
        </Button>
      </Card>
    );
  }

  if (setup.published) {
    const published = setup.published;

    return (
      <div className="space-y-5">
        <Card>
          <CardHeader>
            <p className="font-medium text-highlight">Plan published</p>
            <CardTitle>Everyone’s scoring opportunities are locked</CardTitle>
            <CardDescription>
              Teams, included rules and scoring denominators are saved.
              Publishing this plan has not awarded any points.
            </CardDescription>
          </CardHeader>

          <p className="text-sm text-muted">
            Published {new Date(published.publishedAt).toLocaleString()}
          </p>

          <details className="mt-4">
            <summary className="cursor-pointer font-medium">
              Recorded approval
            </summary>
            <p className="mt-3 whitespace-pre-wrap text-sm text-muted">
              {published.decisionNote}
            </p>
          </details>
        </Card>

        <ReviewDetails review={published.snapshot} />
      </div>
    );
  }

  const locked = busy || pending !== null;
  const allRulesApproved =
    review !== null &&
    review.games.length > 0 &&
    review.games.every((game) => approved.includes(game.gameVersionId));

  const canPublish =
    review !== null &&
    review.blockers.length === 0 &&
    allRulesApproved &&
    policyApproved &&
    note.trim().length >= 20;

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <p className="font-medium text-highlight">Scoring · Policy A</p>
          <CardTitle>Review before the first game</CardTitle>
          <CardDescription>
            Give every team the same scored games and equal opportunities.
            Each included game is worth up to five points. Premiere bonuses
            remain separate.
          </CardDescription>
        </CardHeader>

        {setup.drafts.length === 0 ? (
          <div className="rounded-control border border-border p-4">
            <h3 className="font-semibold">Create a draft schedule first</h3>
            <p className="mt-2 text-sm text-muted">
              The plan needs saved teams and matchups so it can check everyone’s
              opportunities. Return here when your draft schedule is ready.
            </p>
          </div>
        ) : (
          <fieldset disabled={locked} className="space-y-5">
            <Select
              label="Schedule to review"
              value={scheduleId}
              onChange={(event) => {
                setScheduleId(event.target.value);
                clearReview();
              }}
            >
              {setup.drafts.map((draft) => (
                <option key={draft.id} value={draft.id}>
                  Schedule {draft.version} · {draft.mode}
                </option>
              ))}
            </Select>

            <div>
              <h3 className="font-semibold">Choose the games that count</h3>
              <p className="mt-1 text-sm text-muted">
                These selections apply to every team. Activities left out do
                not contribute to this game-points total.
              </p>

              <div className="mt-3 space-y-3">
                {setup.games.map((game) => (
                  <label
                    key={game.eventGameId}
                    className="flex gap-3 rounded-control border border-border p-4"
                  >
                    <input
                      type="checkbox"
                      className="mt-1 h-4 w-4 accent-[var(--color-primary)]"
                      checked={selected.includes(game.eventGameId)}
                      disabled={Boolean(game.unavailableReason)}
                      onChange={() => toggleGame(game.eventGameId)}
                    />
                    <span>
                      <span className="block font-medium">{game.name}</span>
                      <span className="mt-1 block text-sm text-muted">
                        {game.unavailableReason ??
                          `Rules version ${game.ruleVersion} · up to 5 points`}
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </div>

            <p className="text-sm text-muted">
              Proposed maximum:{" "}
              <strong className="text-foreground">
                {selected.length * 5} game points
              </strong>
              . Bonuses are separate.
            </p>
          </fieldset>
        )}

        <div className="mt-5 flex flex-wrap gap-3">
          {setup.drafts.length > 0 && (
            <Button
              loading={busy && !dialogOpen}
              loadingText="Checking opportunities…"
              disabled={!scheduleId || selected.length === 0 || locked}
              onClick={() => void runReview()}
            >
              Review scoring plan
            </Button>
          )}

          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => setReload((value) => value + 1)}
          >
            {pending ? "Check publication status" : "Refresh setup"}
          </Button>
        </div>
      </Card>

      {error && (
        <Card>
          <p role="alert" className="ui-field-error">{error}</p>
          {pending && (
            <p className="mt-2 text-sm text-muted">
              Your publication request is retained. Retry it or check the
              publication status before making changes.
            </p>
          )}
        </Card>
      )}

      {review && (
        <>
          <ReviewDetails review={review} />

          {review.blockers.length > 0 ? (
            <Card>
              <CardTitle>A few things need attention</CardTitle>
              <ul className="mt-4 list-disc space-y-2 pl-5 text-sm">
                {review.blockers.map((blocker) => (
                  <li key={blocker}>{blocker}</li>
                ))}
              </ul>
              <p className="mt-4 text-sm text-muted">
                Correct the draft, then review it again. Nothing has been
                published.
              </p>
            </Card>
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>Approve the rules you will use</CardTitle>
                <CardDescription>
                  Review each pinned version. Record decisions for any rule
                  conflicts before approving it for this event.
                </CardDescription>
              </CardHeader>

              <fieldset disabled={locked} className="space-y-5">
                {review.games.map((game) => (
                  <div
                    key={game.eventGameId}
                    className="rounded-control border border-border p-4"
                  >
                    <details>
                      <summary className="cursor-pointer font-medium">
                        {game.name} · rules version {game.ruleVersion}
                      </summary>
                      <div className="mt-4">
                        <Rules text={game.rulesMarkdown} />
                        {game.sourceRef && (
                          <p className="mt-3 text-xs text-muted">
                            Source: {game.sourceRef}
                          </p>
                        )}
                      </div>
                    </details>

                    <p className="mt-3 text-sm text-muted">
                      {game.decisionNote}
                    </p>

                    <label className="mt-4 flex items-start gap-3 text-sm">
                      <input
                        type="checkbox"
                        className="mt-1 h-4 w-4"
                        checked={approved.includes(game.gameVersionId)}
                        onChange={(event) => {
                          const checked = event.target.checked;
                          setApproved((current) =>
                            checked
                              ? [...current, game.gameVersionId]
                              : current.filter(
                                  (id) => id !== game.gameVersionId,
                                ),
                          );
                        }}
                      />
                      I approve this pinned rule version for this event.
                    </label>
                  </div>
                ))}

                <Textarea
                  label="Approval and rule decisions"
                  hint="Record the chosen variants and how any rule conflicts were resolved. Minimum 20 characters."
                  value={note}
                  minLength={20}
                  maxLength={2000}
                  onChange={(event) => setNote(event.target.value)}
                />

                <label className="flex items-start gap-3 text-sm">
                  <input
                    type="checkbox"
                    className="mt-1 h-4 w-4"
                    checked={policyApproved}
                    onChange={(event) => setPolicyApproved(event.target.checked)}
                  />
                  <span>
                    I approve Policy A and the precision policy below. I have
                    checked the schedule’s timings and venue arrangements.
                    <span className="mt-2 block text-muted">
                      {review.precision}
                    </span>
                  </span>
                </label>
              </fieldset>

              <Button
                className="mt-5"
                disabled={busy || (!pending && !canPublish)}
                onClick={() => setDialogOpen(true)}
              >
                {pending ? "Retry publication" : "Review publication"}
              </Button>
            </Card>
          )}
        </>
      )}

      <Dialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        busy={busy}
        title="Publish and lock this plan?"
        description="The schedule, eligible teams, pinned rules and scoring opportunities will be frozen."
      >
        {review && (
          <>
            <p>
              {review.teams.length} teams · {review.games.length} scored games ·{" "}
              <strong>{review.maximumGamePoints} maximum game points</strong>
            </p>
            <p className="mt-3 text-sm text-muted">
              Later cancellations will not silently reduce denominators.
              This action does not award points or publish final standings.
            </p>

            {error && (
              <p role="alert" className="mt-4 ui-field-error">{error}</p>
            )}

            <div className="mt-6 flex flex-wrap gap-3">
              <Button
                loading={busy}
                loadingText="Publishing…"
                disabled={!pending && !canPublish}
                onClick={() => void publish()}
              >
                {pending ? "Retry same publication" : "Publish and lock plan"}
              </Button>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() => setDialogOpen(false)}
              >
                Back to review
              </Button>
            </div>
          </>
        )}
      </Dialog>
    </div>
  );
}

function ReviewDetails({ review }: { review: PlanReview }) {
  const names = new Map(review.teams.map((team) => [team.id, team.name]));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Scoring opportunities</CardTitle>
        <CardDescription>
          Schedule {review.scheduleVersion} · {review.teams.length} teams ·{" "}
          {review.maximumGamePoints} maximum game points
        </CardDescription>
      </CardHeader>

      <div className="space-y-4">
        {review.games.map((game) => (
          <section
            key={game.eventGameId}
            className="rounded-control border border-border p-4"
          >
            <h3 className="font-semibold">{game.name}</h3>
            <p className="mt-1 text-sm text-muted">
              Up to 5 points · rules version {game.ruleVersion}
            </p>

            <ul className="mt-3 space-y-2 text-sm">
              {game.opportunities.map((opportunity) => (
                <li
                  key={opportunity.teamId}
                  className="flex flex-wrap justify-between gap-2"
                >
                  <span>{names.get(opportunity.teamId) ?? "Team"}</span>
                  <span className="text-muted">
                    {opportunity.appearances} appearance(s) ·{" "}
                    {opportunity.rawDenominator} possible raw points
                  </span>
                </li>
              ))}
            </ul>

            <p className="mt-3 text-sm text-muted">
              Contribution = confirmed raw points ÷ frozen raw denominator × 5.
            </p>
          </section>
        ))}
      </div>

      {review.byeCount > 0 && (
        <p className="mt-4 text-sm text-muted">
          {review.byeCount} explicit bye(s) excluded from scoring opportunities.
          No points are awarded for them.
        </p>
      )}

      {review.excludedGames.length > 0 && (
        <p className="mt-4 text-sm text-muted">
          Outside this game-points total: {review.excludedGames.join(", ")}.
          Premiere bonuses remain a separate category.
        </p>
      )}
    </Card>
  );
}