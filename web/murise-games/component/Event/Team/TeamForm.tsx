"use client";

import { useRef, useState, type FormEvent } from "react";
import { TeamController } from "@/modules/Event/Team/team.controller";
import type { TeamResponse } from "@/modules/Event/Team/team.type";

interface TeamFormProps {
  eventId: string;
  team?: TeamResponse;
  onSaved: (team: TeamResponse) => void;
  onCancel: () => void;
}

const inputClass =
  "mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-950 " +
  "px-3 py-2 text-sm text-zinc-100 outline-none focus:border-emerald-500";

export function TeamForm({
  eventId,
  team,
  onSaved,
  onCancel,
}: TeamFormProps) {
  const busy = useRef(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;

    const form = event.currentTarget;
    const fields = new FormData(form);

    busy.current = true;
    setSaving(true);
    setError(null);

    try {
      const saved = await TeamController.save(
        eventId,
        {
          name: String(fields.get("name") ?? ""),
          color: String(fields.get("color") ?? ""),
          sortOrder: Number(fields.get("sortOrder")),
        },
        team?.id,
      );

      onSaved(saved);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save team.");
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  return (
    <form
      onSubmit={submit}
      className="mt-4 rounded-lg border border-zinc-700 p-4"
    >
      <h4 className="font-semibold">{team ? "Edit team" : "New team"}</h4>

      <fieldset disabled={saving} className="mt-3 space-y-3">
        <label className="block text-sm text-zinc-300">
          Team name
          <input
            name="name"
            required
            maxLength={80}
            defaultValue={team?.name ?? ""}
            className={inputClass}
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm text-zinc-300">
            Color
            <input
              name="color"
              type="color"
              defaultValue={team?.color ?? "#10b981"}
              className="mt-1 block h-10 w-full rounded border
                border-zinc-700 bg-zinc-950"
            />
          </label>

          <label className="block text-sm text-zinc-300">
            Display order
            <input
              name="sortOrder"
              type="number"
              required
              min={0}
              max={2147483647}
              step={1}
              defaultValue={team?.sortOrder ?? 0}
              className={inputClass}
            />
          </label>
        </div>

        <div className="flex gap-2">
          <button
            type="submit"
            className="rounded-lg bg-emerald-600 px-3 py-2 text-sm
              font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
          >
            {saving ? "Saving…" : team ? "Save changes" : "Create team"}
          </button>

          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-zinc-700 px-3 py-2 text-sm"
          >
            Cancel
          </button>
        </div>
      </fieldset>

      {error && (
        <div role="alert" className="mt-3 text-sm text-rose-300">
          <p>{error}</p>
          <p className="mt-1 text-xs">
            If the connection failed, reload teams before retrying.
          </p>
        </div>
      )}
    </form>
  );
}