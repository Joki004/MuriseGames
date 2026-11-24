"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, LoadingState, Select } from "@/component/ui";
import {
  AlibiController,
  type AlibiPreviewRole,
  type AlibiRolePreview,
  type AlibiVersionInfo,
} from "@/modules/Event/Alibi";

const ROLES: { value: AlibiPreviewRole; label: string; hint: string }[] = [
  {
    value: "audience",
    label: "TV / audience",
    hint: "What the room sees on the TV while the pair plays.",
  },
  {
    value: "pair",
    label: "Pair (phones)",
    hint: "What the two partners see. Equivalents and judge notes never reach them.",
  },
  {
    value: "judge",
    label: "Judge",
    hint: "Everything, for whoever compares the answers.",
  },
];

interface LoadedPreview {
  key: string;
  preview: AlibiRolePreview | null;
  error: string | null;
}

export function AlibiCasePreview({
  eventId,
  caseId,
  versions,
  currentVersion,
}: {
  eventId: string;
  caseId: string;
  versions: AlibiVersionInfo[];
  currentVersion: number;
}) {
  const [role, setRole] = useState<AlibiPreviewRole>("pair");
  const [version, setVersion] = useState(currentVersion);
  const [loaded, setLoaded] = useState<LoadedPreview | null>(null);

  const key = `${role}:${version}`;

  useEffect(() => {
    let cancelled = false;
    AlibiController.preview(eventId, caseId, role, version || undefined)
      .then((preview) => {
        if (!cancelled) setLoaded({ key, preview, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLoaded({
            key,
            preview: null,
            error: err instanceof Error ? err.message : "Could not load the preview.",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, caseId, role, version, key]);

  const current = loaded?.key === key ? loaded : null;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-wrap gap-2" role="group" aria-label="Preview as">
          {ROLES.map((option) => (
            <Button
              key={option.value}
              variant={role === option.value ? "primary" : "secondary"}
              onClick={() => setRole(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
        {versions.length > 1 && (
          <div className="min-w-[12rem]">
            <Select
              label="Version"
              value={String(version)}
              onChange={(e) => setVersion(Number(e.target.value))}
            >
              {versions.map((v) => (
                <option key={v.versionNumber} value={v.versionNumber}>
                  v{v.versionNumber}
                  {v.versionNumber === currentVersion ? " (current)" : ""}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>

      <p className="text-sm text-muted">
        {ROLES.find((r) => r.value === role)?.hint} The preview shows the last
        saved version, not unsaved changes.
      </p>

      {!current && <LoadingState label="Loading preview…" />}
      {current?.error && <Alert tone="error">{current.error}</Alert>}
      {current?.preview && <PreviewBody preview={current.preview} />}
    </div>
  );
}

function PreviewBody({ preview }: { preview: AlibiRolePreview }) {
  if (preview.versionNumber === 0) {
    return (
      <Alert tone="info">Nothing saved yet. Save the case to preview it.</Alert>
    );
  }

  if (preview.role === "AUDIENCE") {
    return (
      <div className="rounded-[var(--ui-radius-card)] border border-border bg-background/60 p-6 text-center">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted">
          The alibi
        </p>
        <p className="mt-3 whitespace-pre-line text-xl font-semibold">
          {preview.brief || "—"}
        </p>
      </div>
    );
  }

  const judge = preview.role === "JUDGE";

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section className="space-y-3 rounded-[var(--ui-radius-card)] border border-border bg-background/60 p-4">
        <div className="flex items-center gap-2">
          <h4 className="font-semibold">
            {judge ? "Case" : "While preparing (90 s)"}
          </h4>
          {!judge && <Badge tone="accent">Both partners together</Badge>}
        </div>
        <p className="whitespace-pre-line">{preview.brief || "—"}</p>
        {preview.fixedFacts && preview.fixedFacts.length > 0 && (
          <div>
            <p className="text-sm font-semibold">Fixed facts</p>
            <ul className="mt-1 list-disc space-y-1 pl-5 text-sm">
              {preview.fixedFacts.map((fact, i) => (
                <li key={i}>{fact}</li>
              ))}
            </ul>
          </div>
        )}
        {judge && preview.judgeRubric && (
          <div>
            <p className="text-sm font-semibold">Judging rubric</p>
            <p className="mt-1 whitespace-pre-line text-sm text-muted">
              {preview.judgeRubric}
            </p>
          </div>
        )}
      </section>

      <section className="space-y-3 rounded-[var(--ui-radius-card)] border border-border bg-background/60 p-4">
        <div className="flex items-center gap-2">
          <h4 className="font-semibold">
            {judge ? "Questions" : "During questions (30 s each)"}
          </h4>
          {!judge && <Badge tone="warning">Separated · one at a time</Badge>}
        </div>
        <ol className="space-y-3">
          {(preview.questions ?? []).map((q) => (
            <li key={q.number} className="text-sm">
              <p>
                <span className="font-semibold">{q.number}.</span> {q.prompt}
              </p>
              {judge && q.validEquivalents && q.validEquivalents.length > 0 && (
                <p className="mt-1 text-muted">
                  Accepted: {q.validEquivalents.join(" · ")}
                </p>
              )}
              {judge && q.judgeNote && (
                <p className="mt-1 text-muted">Note: {q.judgeNote}</p>
              )}
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
