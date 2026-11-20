"use client";

import { useEffect, useId, useState } from "react";
import { TeamController } from "@/modules/Event/Team/team.controller";
import type {
  TeamListResponse,
  TeamResponse,
} from "@/modules/Event/Team/team.type";
import { TeamForm } from "./TeamForm";
import { InvitePanel } from "@/component/Event/Invite/InvitePanel";

const buttonClass =
  "rounded-lg border border-zinc-700 px-3 py-1.5 text-sm " +
  "text-zinc-200 hover:bg-zinc-800 disabled:opacity-40";

export function TeamPanel({ eventId }: { eventId: string }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();

  return (
    <div className="mt-4 border-t border-zinc-800 pt-3">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
        className={buttonClass}
      >
        {open ? "Hide teams" : "Teams"}
      </button>

      <div id={panelId}>
        {open && <TeamContent key={eventId} eventId={eventId} />}
      </div>
    </div>
  );
}

function TeamContent({ eventId }: { eventId: string }) {
  const [result, setResult] = useState<TeamListResponse | null>(null);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [editor, setEditor] = useState<TeamResponse | "new" | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const response = await TeamController.list(eventId);
        if (active) setResult(response);
      } catch (err: unknown) {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load teams.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, [eventId, revision]);

  function reload() {
    setEditor(null);
    setResult(null);
    setError(null);
    setLoading(true);
    setRevision((value) => value + 1);
  }

  function saved(team: TeamResponse) {
    setMessage(`Saved ${team.name}.`);
    reload();
  }

  return (
    <section className="mt-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="font-semibold">Event teams</h4>

        <div className="flex gap-2">
          {result?.canManage && (
            <button
              type="button"
              onClick={() => setEditor("new")}
              className={buttonClass}
            >
              Add team
            </button>
          )}

          <button
            type="button"
            disabled={loading}
            onClick={reload}
            className={buttonClass}
          >
            Refresh teams
          </button>
        </div>
      </div>

      {message && (
        <p role="status" className="mt-3 text-sm text-emerald-300">
          {message}
        </p>
      )}

      {loading && (
        <p role="status" className="mt-3 text-sm text-zinc-400">
          Loading teams…
        </p>
      )}

      {error && (
        <p role="alert" className="mt-3 text-sm text-rose-300">
          {error}
        </p>
      )}

      {!loading && result && (
        <>
          {result.canManage && editor !== null && (
            <TeamForm
              key={editor === "new" ? "new" : editor.id}
              eventId={eventId}
              team={editor === "new" ? undefined : editor}
              onSaved={saved}
              onCancel={() => setEditor(null)}
            />
          )}

          {result.teams.length === 0 ? (
            <p className="mt-4 text-sm text-zinc-400">No teams yet.</p>
          ) : (
            <ul className="mt-4 space-y-2">
              {result.teams.map((team) => (
                <li
                  key={team.id}
                  className="flex items-center justify-between gap-3
                    rounded-lg border border-zinc-800 p-3"
                >
                  <div className="flex items-center gap-3">
                    <span
                      aria-hidden="true"
                      className="h-4 w-4 shrink-0 rounded-full
                        border border-white/20"
                      style={{ backgroundColor: team.color }}
                    />
                    <div>
                      <p className="text-sm font-medium">{team.name}</p>
                      <p className="text-xs text-zinc-400">
                        Display order: {team.sortOrder}
                      </p>
                    </div>
                  </div>

                  {result.canManage && (
                    <button
                      type="button"
                      onClick={() => setEditor(team)}
                      className={buttonClass}
                    >
                      Edit
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
          {result.canManage && <InvitePanel eventId={eventId} />}
        </>
      )}
    </section>
  );
}