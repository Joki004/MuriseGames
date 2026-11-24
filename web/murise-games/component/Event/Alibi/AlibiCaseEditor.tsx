"use client";

import { useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  CardTitle,
  Dialog,
  Input,
  LoadingState,
  Textarea,
} from "@/component/ui";
import {
  ALIBI_LABEL_MAX,
  ALIBI_MAX_FIXED_FACTS,
  ALIBI_QUESTION_COUNT,
  AlibiController,
  splitLines,
  type AlibiCaseContent,
  type AlibiCaseDetail,
} from "@/modules/Event/Alibi";
import { AlibiCasePreview } from "./AlibiCasePreview";

interface QuestionDraft {
  prompt: string;
  equivalents: string;
  judgeNote: string;
}

interface Draft {
  label: string;
  brief: string;
  facts: string;
  questions: QuestionDraft[];
  rubric: string;
}

function toDraft(detail: AlibiCaseDetail): Draft {
  const questions: QuestionDraft[] = detail.content.questions.map((q) => ({
    prompt: q.prompt,
    equivalents: q.validEquivalents.join("\n"),
    judgeNote: q.judgeNote,
  }));
  while (questions.length < ALIBI_QUESTION_COUNT) {
    questions.push({ prompt: "", equivalents: "", judgeNote: "" });
  }
  return {
    label: detail.label,
    brief: detail.content.brief,
    facts: detail.content.fixedFacts.join("\n"),
    questions,
    rubric: detail.content.judgeRubric,
  };
}

function toContent(draft: Draft): AlibiCaseContent {
  return {
    brief: draft.brief,
    fixedFacts: splitLines(draft.facts),
    questions: draft.questions.map((q) => ({
      prompt: q.prompt,
      validEquivalents: splitLines(q.equivalents),
      judgeNote: q.judgeNote,
    })),
    judgeRubric: draft.rubric,
  };
}

function sameDraft(a: Draft, b: Draft): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

type Tab = "edit" | "preview" | "history";

interface Loaded {
  detail: AlibiCaseDetail | null;
  error: string | null;
}

export function AlibiCaseEditor({
  eventId,
  caseId,
  onClose,
  onChanged,
}: {
  eventId: string;
  caseId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);

  useEffect(() => {
    let cancelled = false;
    AlibiController.get(eventId, caseId)
      .then((detail) => {
        if (!cancelled) setLoaded({ detail, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLoaded({
            detail: null,
            error: err instanceof Error ? err.message : "Could not open the case.",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, caseId]);

  if (!loaded) return <LoadingState label="Opening case…" />;
  if (!loaded.detail) {
    return (
      <Card className="space-y-3">
        <Alert tone="error">{loaded.error}</Alert>
        <Button variant="secondary" onClick={onClose}>
          Back to the pool
        </Button>
      </Card>
    );
  }

  return (
    <CaseForm
      key={loaded.detail.id}
      eventId={eventId}
      initial={loaded.detail}
      onSaved={onChanged}
      onClose={onClose}
      onArchived={() => {
        onChanged();
        onClose();
      }}
    />
  );
}

function CaseForm({
  eventId,
  initial,
  onSaved,
  onClose,
  onArchived,
}: {
  eventId: string;
  initial: AlibiCaseDetail;
  onSaved: () => void;
  onClose: () => void;
  onArchived: () => void;
}) {
  const [detail, setDetail] = useState(initial);
  const [draft, setDraft] = useState<Draft>(() => toDraft(initial));
  const [tab, setTab] = useState<Tab>("edit");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{
    tone: "success" | "error";
    text: string;
  } | null>(null);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [removing, setRemoving] = useState(false);

  const dirty = !sameDraft(draft, toDraft(detail));
  const factCount = splitLines(draft.facts).length;
  const questionCount = draft.questions.filter((q) => q.prompt.trim()).length;

  function updateQuestion(index: number, patch: Partial<QuestionDraft>) {
    setDraft((current) => ({
      ...current,
      questions: current.questions.map((q, i) =>
        i === index ? { ...q, ...patch } : q,
      ),
    }));
  }

  async function save() {
    setSaving(true);
    setMessage(null);
    try {
      const saved = await AlibiController.save(
        eventId,
        detail.id,
        draft.label,
        detail.currentVersion,
        toContent(draft),
      );
      const changedVersion = saved.currentVersion !== detail.currentVersion;
      setDetail(saved);
      setDraft(toDraft(saved));
      setMessage({
        tone: "success",
        text: changedVersion
          ? `Saved as version ${saved.currentVersion}.`
          : "Saved. The content did not change, so no new version was needed.",
      });
      onSaved();
    } catch (err: unknown) {
      setMessage({
        tone: "error",
        text: err instanceof Error ? err.message : "Could not save the case.",
      });
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    setRemoving(true);
    try {
      await AlibiController.archive(eventId, detail.id);
      setConfirmRemove(false);
      onArchived();
    } catch (err: unknown) {
      setConfirmRemove(false);
      setMessage({
        tone: "error",
        text: err instanceof Error ? err.message : "Could not remove the case.",
      });
    } finally {
      setRemoving(false);
    }
  }

  return (
    <Card className="space-y-5">
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            <CardTitle>{detail.label}</CardTitle>
            <div className="flex flex-wrap gap-1.5">
              <Badge tone={detail.ready ? "success" : "warning"}>
                {detail.ready ? "Ready to play" : "Draft"}
              </Badge>
              <Badge>
                {detail.currentVersion === 0
                  ? "Not saved yet"
                  : `Version ${detail.currentVersion}`}
              </Badge>
              {dirty && <Badge tone="warning">Unsaved changes</Badge>}
            </div>
          </div>
          <Button
            variant="secondary"
            onClick={() => {
              if (
                !dirty ||
                window.confirm("Leave without saving your changes?")
              ) {
                onClose();
              }
            }}
          >
            Back to the pool
          </Button>
        </div>
      </CardHeader>

      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Case sections">
        {(
          [
            ["edit", "Edit"],
            ["preview", "Preview by role"],
            ["history", `History (${detail.versions.length})`],
          ] as const
        ).map(([value, label]) => (
          <Button
            key={value}
            role="tab"
            aria-selected={tab === value}
            variant={tab === value ? "primary" : "secondary"}
            onClick={() => setTab(value)}
          >
            {label}
          </Button>
        ))}
      </div>

      {tab === "edit" && (
        <div className="space-y-6">
          <Input
            label="Case name (only organizers see it)"
            maxLength={ALIBI_LABEL_MAX}
            value={draft.label}
            onChange={(e) => setDraft({ ...draft, label: e.target.value })}
          />

          <div className="grid gap-4 lg:grid-cols-2">
            <Textarea
              label="Brief"
              hint="The situation the pair reads together. The TV shows it too."
              rows={6}
              value={draft.brief}
              onChange={(e) => setDraft({ ...draft, brief: e.target.value })}
            />
            <Textarea
              label={`Fixed facts (${factCount}/${ALIBI_MAX_FIXED_FACTS})`}
              hint="One per line. Shown to the pair while preparing; contradicting one scores 0."
              rows={6}
              value={draft.facts}
              onChange={(e) => setDraft({ ...draft, facts: e.target.value })}
            />
          </div>

          <div className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="font-semibold">
                Questions ({questionCount}/{ALIBI_QUESTION_COUNT})
              </h4>
              <p className="text-sm text-muted">
                Asked in this order, one at a time, to both partners.
              </p>
            </div>
            <ol className="space-y-3">
              {draft.questions.map((q, index) => (
                <li
                  key={index}
                  className="space-y-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3"
                >
                  <Input
                    label={`Question ${index + 1}`}
                    maxLength={500}
                    value={q.prompt}
                    onChange={(e) =>
                      updateQuestion(index, { prompt: e.target.value })
                    }
                  />
                  <div className="grid gap-3 md:grid-cols-2">
                    <Textarea
                      label="Valid equivalents (optional)"
                      hint="For a question about a fixed fact: answers that count as correct, one per line."
                      rows={2}
                      value={q.equivalents}
                      onChange={(e) =>
                        updateQuestion(index, { equivalents: e.target.value })
                      }
                    />
                    <Textarea
                      label="Judge note (optional)"
                      hint="How to decide a close call on this question."
                      rows={2}
                      maxLength={1000}
                      value={q.judgeNote}
                      onChange={(e) =>
                        updateQuestion(index, { judgeNote: e.target.value })
                      }
                    />
                  </div>
                </li>
              ))}
            </ol>
          </div>

          <Textarea
            label="Judging rubric (optional)"
            hint="General guidance for the judges, for the whole case."
            rows={4}
            value={draft.rubric}
            onChange={(e) => setDraft({ ...draft, rubric: e.target.value })}
          />

          {detail.missing.length > 0 && (
            <Alert tone="info">
              <p className="font-semibold">Before this case can be played:</p>
              <ul className="mt-1 list-disc pl-5">
                {detail.missing.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </Alert>
          )}

          <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
            <Button
              loading={saving}
              loadingText="Saving…"
              disabled={!dirty}
              onClick={() => void save()}
            >
              Save
            </Button>
            <Button
              variant="danger"
              disabled={saving}
              onClick={() => setConfirmRemove(true)}
            >
              Remove from pool
            </Button>
            {message && <Alert tone={message.tone}>{message.text}</Alert>}
          </div>
        </div>
      )}

      {tab === "preview" && (
        <AlibiCasePreview
          key={detail.currentVersion}
          eventId={eventId}
          caseId={detail.id}
          versions={detail.versions}
          currentVersion={detail.currentVersion}
        />
      )}

      {tab === "history" && (
        <div className="space-y-4">
          {detail.versions.length === 0 ? (
            <p className="text-sm text-muted">Nothing saved yet.</p>
          ) : (
            <ul className="space-y-2">
              {detail.versions.map((v) => (
                <li
                  key={v.versionNumber}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-3 py-2 text-sm"
                >
                  <span className="font-medium">
                    Version {v.versionNumber}
                    {v.versionNumber === detail.currentVersion && " (current)"}
                  </span>
                  <span className="text-muted">
                    {v.createdBy} · {formatWhen(v.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-sm text-muted">
            Saved versions never change. When a round starts it keeps the
            version it was given, so later edits never affect a round in play.
          </p>
          <div>
            <p className="text-sm font-semibold">Knows this case</p>
            <p className="mt-1 text-sm text-muted">
              {detail.seenBy.join(", ") || "Nobody yet"}. Their teams will not
              be given this case.
            </p>
          </div>
        </div>
      )}

      <Dialog
        open={confirmRemove}
        onOpenChange={(next) => {
          if (!removing) setConfirmRemove(next);
        }}
        busy={removing}
        title="Remove this case from the pool?"
        description="It can no longer be picked for a round. Saved versions are kept for history."
      >
        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button
            variant="secondary"
            disabled={removing}
            onClick={() => setConfirmRemove(false)}
          >
            Keep it
          </Button>
          <Button
            variant="danger"
            loading={removing}
            loadingText="Removing…"
            onClick={() => void remove()}
          >
            Remove
          </Button>
        </div>
      </Dialog>
    </Card>
  );
}
