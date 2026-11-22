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
  Input,
  LoadingState,
  Select,
  Textarea,
} from "@/component/ui";
import {
  ANSWER_SCOPES,
  MAX_ALIASES,
  TITLE_MAX_LENGTH,
  TitleController,
  parseAliases,
  type AnswerScope,
  type TeamTitleResponse,
  type TitleBoardResponse,
} from "@/modules/Event/Title";

export function TitlePanel() {
  const { event, canManage } = useEventDetail();
  const [board, setBoard] = useState<TitleBoardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!canManage) return;
    let cancelled = false;

    TitleController.board(event.id)
      .then((data) => {
        if (!cancelled) setBoard(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Could not load titles.",
          );
        }
      });

    return () => {
      cancelled = true;
    };
  }, [canManage, event.id]);

  // The clue panel below already explains the organizer-only rule.
  if (!canManage) return null;
  if (error && !board) return <Alert tone="error">{error}</Alert>;
  if (!board) return <LoadingState label="Loading titles…" />;
  if (board.teams.length === 0) return null;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Secret titles</CardTitle>
          <CardDescription>
            Only organizers can see these. Players and the TV never receive a
            title before it is revealed.
          </CardDescription>
        </CardHeader>
      </Card>

      {board.teams.map((team) => (
        <TeamTitleEditor
          key={team.teamId}
          eventId={event.id}
          team={team}
          onSaved={setBoard}
        />
      ))}
    </div>
  );
}

function TeamTitleEditor({
  eventId,
  team,
  onSaved,
}: {
  eventId: string;
  team: TeamTitleResponse;
  onSaved: (board: TitleBoardResponse) => void;
}) {
  const [title, setTitle] = useState(team.title ?? "");
  const [scope, setScope] = useState<AnswerScope | "">(team.answerScope ?? "");
  const [aliasText, setAliasText] = useState(team.aliases.join("\n"));
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{
    tone: "success" | "error";
    text: string;
  } | null>(null);

  const dirty =
    title !== (team.title ?? "") ||
    scope !== (team.answerScope ?? "") ||
    aliasText !== team.aliases.join("\n");

  async function save() {
    if (scope === "") {
      setMessage({ tone: "error", text: "Choose an answer scope." });
      return;
    }

    setBusy(true);
    setMessage(null);
    try {
      const updated = await TitleController.save(eventId, team.teamId, {
        title,
        answerScope: scope,
        aliases: parseAliases(aliasText),
      });
      const saved = updated.teams.find((t) => t.teamId === team.teamId);
      if (saved) {
        setTitle(saved.title ?? "");
        setScope(saved.answerScope ?? "");
        setAliasText(saved.aliases.join("\n"));
      }
      onSaved(updated);
      setMessage({ tone: "success", text: "Saved." });
    } catch (err: unknown) {
      setMessage({
        tone: "error",
        text: err instanceof Error ? err.message : "Could not save the title.",
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
          <div className="flex items-center gap-2">
            {dirty && <Badge tone="warning">Unsaved changes</Badge>}
            <Badge tone={team.title ? "success" : "warning"}>
              {team.title ? "Registered" : "Not registered"}
            </Badge>
          </div>
        </div>
      </CardHeader>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-4">
          <Input
            label="Secret title"
            maxLength={TITLE_MAX_LENGTH}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <Select
            label="Answer scope"
            hint="What counts as a correct guess."
            value={scope}
            onChange={(e) => setScope(e.target.value as AnswerScope | "")}
          >
            <option value="">Choose…</option>
            {ANSWER_SCOPES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>

        <Textarea
          label="Accepted aliases and translations"
          hint={`One per line, up to ${MAX_ALIASES}. Case, accents and punctuation are ignored.`}
          rows={5}
          value={aliasText}
          onChange={(e) => setAliasText(e.target.value)}
        />
      </div>

      <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
        <Button loading={busy} disabled={!dirty} onClick={() => void save()}>
          Save title
        </Button>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
      </div>
    </Card>
  );
}