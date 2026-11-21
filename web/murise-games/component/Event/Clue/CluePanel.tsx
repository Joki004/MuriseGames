"use client";

import { useEffect, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  EmptyState,
  LoadingState,
  Textarea,
} from "@/component/ui";
import {
  CLUE_MAX_LENGTH,
  ClueController,
  type ClueBoardResponse,
  type ClueSaveItem,
  type TeamClueSetResponse,
} from "@/modules/Event/Clue";

export function CluePanel() {
  const { event, canManage } = useEventDetail();
  const [board, setBoard] = useState<ClueBoardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canManage) return;
    let cancelled = false;

    ClueController.board(event.id)
      .then((data) => {
        if (!cancelled) setBoard(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Could not load clues.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [canManage, event.id]);

  if (!canManage) {
    return <Alert tone="error">Only organizers can write clues.</Alert>;
  }
  if (error && !board) return <Alert tone="error">{error}</Alert>;
  if (!board) return <LoadingState label="Loading clues…" />;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Release readiness</CardTitle>
          <CardDescription>
            A clue can only be released when every team has an approved clue for
            that stage.
          </CardDescription>
        </CardHeader>
        <ul className="grid gap-3 sm:grid-cols-3">
          {board.stages.map((stage) => (
            <li
              key={stage.stage}
              className="flex items-center justify-between gap-3 rounded-control border border-border p-3"
            >
              <span className="font-medium">Clue {stage.stage}</span>
              <span className="flex items-center gap-2 text-sm text-muted">
                {stage.approvedTeams}/{stage.totalTeams}
                <Badge tone={stage.canRelease ? "success" : "warning"}>
                  {stage.canRelease ? "Ready" : "Blocked"}
                </Badge>
              </span>
            </li>
          ))}
        </ul>
      </Card>

      {board.teams.length === 0 ? (
        <EmptyState
          title="No teams yet"
          description="Create teams first, then write their clues."
        />
      ) : (
        board.teams.map((team) => (
          <TeamClueEditor
            key={team.teamId}
            eventId={event.id}
            team={team}
            onSaved={setBoard}
          />
        ))
      )}
    </div>
  );
}

function toDrafts(team: TeamClueSetResponse): ClueSaveItem[] {
  return team.clues.map(({ body, approved }) => ({ body, approved }));
}

function TeamClueEditor({
  eventId,
  team,
  onSaved,
}: {
  eventId: string;
  team: TeamClueSetResponse;
  onSaved: (board: ClueBoardResponse) => void;
}) {
  const [drafts, setDrafts] = useState<ClueSaveItem[]>(() => toDrafts(team));
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{
    tone: "success" | "error";
    text: string;
  } | null>(null);

  const dirty = JSON.stringify(drafts) !== JSON.stringify(toDrafts(team));

  // Editing text clears approval: the host approves what they previewed.
  function setBody(index: number, body: string) {
    setDrafts((current) =>
      current.map((draft, i) => (i === index ? { body, approved: false } : draft)),
    );
  }

  function toggleApproved(index: number) {
    setDrafts((current) =>
      current.map((draft, i) =>
        i === index
          ? { ...draft, approved: !draft.approved && draft.body.trim() !== "" }
          : draft,
      ),
    );
  }

  // Moving a clue changes when it is shown, so both clues need approving again.
  function move(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= drafts.length) return;

    setDrafts((current) => {
      const next = current.map((draft) => ({ ...draft }));
      [next[index], next[target]] = [next[target], next[index]];
      next[index].approved = false;
      next[target].approved = false;
      return next;
    });
  }

  async function save() {
    setBusy(true);
    setMessage(null);
    try {
      const updated = await ClueController.save(eventId, team.teamId, {
        clues: drafts,
      });
      const saved = updated.teams.find((t) => t.teamId === team.teamId);
      if (saved) setDrafts(toDrafts(saved));
      onSaved(updated);
      setMessage({ tone: "success", text: "Saved." });
    } catch (err: unknown) {
      setMessage({
        tone: "error",
        text: err instanceof Error ? err.message : "Could not save the clues.",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="space-y-5">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span
              className="h-4 w-4 rounded-full"
              style={{ backgroundColor: team.teamColor }}
            />
            <CardTitle>{team.teamName}</CardTitle>
          </div>
          {dirty && <Badge tone="warning">Unsaved changes</Badge>}
        </div>
        <CardDescription>
          Clues are released in order: 1, then 2, then 3. Drafts stay private
          until you release them.
        </CardDescription>
      </CardHeader>

      <ol className="grid gap-6">
        {drafts.map((draft, index) => (
          <li key={index} className="grid gap-4 lg:grid-cols-2">
            <div className="space-y-3">
              <Textarea
                label={`Clue ${index + 1}`}
                rows={3}
                maxLength={CLUE_MAX_LENGTH}
                value={draft.body}
                hint={`${draft.body.length}/${CLUE_MAX_LENGTH}`}
                onChange={(e) => setBody(index, e.target.value)}
              />
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  Move up
                </Button>
                <Button
                  variant="secondary"
                  disabled={index === drafts.length - 1}
                  onClick={() => move(index, 1)}
                >
                  Move down
                </Button>
                <Button
                  variant={draft.approved ? "secondary" : "primary"}
                  disabled={!draft.approved && draft.body.trim() === ""}
                  onClick={() => toggleApproved(index)}
                >
                  {draft.approved ? "Remove approval" : "Mark approved"}
                </Button>
              </div>
            </div>

            <CluePreview
              stage={index + 1}
              body={draft.body}
              approved={draft.approved}
            />
          </li>
        ))}
      </ol>

      <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
        <Button loading={busy} disabled={!dirty} onClick={() => void save()}>
          Save clues
        </Button>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
      </div>
    </Card>
  );
}

// Host preview: same tile as the TV clue card, plus a Draft/Approved badge.
function CluePreview({
  stage,
  body,
  approved,
}: {
  stage: number;
  body: string;
  approved: boolean;
}) {
  const text = body.trim();

  if (!text) {
    return (
      <div className="rounded-control border border-dashed border-control-border p-5 text-muted">
        <Badge>Clue {stage}</Badge>
        <p className="mt-3 text-xl leading-snug">Not written yet</p>
      </div>
    );
  }

  return (
    <div className="rounded-control border border-highlight/40 bg-[var(--highlight-soft)] p-5">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="accent">Clue {stage}</Badge>
        <Badge tone={approved ? "success" : "warning"}>
          {approved ? "Approved" : "Draft"}
        </Badge>
      </div>
      <p className="mt-3 break-words text-xl leading-snug">{text}</p>
    </div>
  );
}