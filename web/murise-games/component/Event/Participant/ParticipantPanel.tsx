"use client";

import { useEffect, useState } from "react";
import {
  ParticipantController,
  type EventParticipantResponse,
  type TeamOption,
} from "@/modules/Event/Participant";
import { PictureControl } from "@/component/Picture/PictureControl";

interface ParticipantPanelProps {
  eventId: string;
  refreshTrigger?: number;
  canManage?: boolean;
}

export function ParticipantPanel({
  eventId,
  refreshTrigger = 0,
  canManage = false,
}: ParticipantPanelProps) {
  const [participants, setParticipants] = useState<EventParticipantResponse[]>(
    [],
  );
  const [teams, setTeams] = useState<TeamOption[]>([]);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function fetchRoster() {
      try {
        const [roster, activeTeams] = await Promise.all([
          ParticipantController.list(eventId),
          ParticipantController.listActiveTeamOptions(eventId),
        ]);
        if (active) {
          setParticipants(roster);
          setTeams(activeTeams);
          setError(null);
        }
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load roster.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void fetchRoster();

    return () => {
      active = false;
    };
  }, [eventId, revision, refreshTrigger]);

  function reload() {
    setLoading(true);
    setError(null);
    setRevision((v) => v + 1);
  }

  async function handleTeamChange(participantId: string, nextTeamId: string) {
    setSavingId(participantId);
    setError(null);
    try {
      const updated = await ParticipantController.assignTeam(
        eventId,
        participantId,
        nextTeamId || null,
      );
      setParticipants((prev) =>
        prev.map((p) => (p.id === updated.id ? updated : p)),
      );
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not reassign participant.",
      );
    } finally {
      setSavingId(null);
    }
  }

  async function handleCaptainToggle(
    participantId: string,
    nextCaptain: boolean,
  ) {
    setSavingId(participantId);
    setError(null);
    try {
      const updated = await ParticipantController.setCaptain(
        eventId,
        participantId,
        nextCaptain,
      );
      setParticipants((prev) =>
        prev.map((p) => {
          if (p.id === updated.id) return updated;
          if (nextCaptain && updated.teamId && p.teamId === updated.teamId) {
            return { ...p, captain: false };
          }
          return p;
        }),
      );
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not update team captain.",
      );
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/50 p-3">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
          Roster ({participants.length})
        </h4>
        <button
          type="button"
          onClick={reload}
          disabled={loading}
          className="rounded border border-zinc-700 px-2 py-0.5 text-xs text-zinc-300 hover:bg-zinc-800 disabled:opacity-40"
        >
          Refresh
        </button>
      </div>

      {loading && <p className="mt-2 text-xs text-zinc-400">Loading roster…</p>}

      {error && (
        <p role="alert" className="mt-2 text-xs text-rose-300">
          {error}
        </p>
      )}

      {!loading && participants.length === 0 && (
        <p className="mt-2 text-xs text-zinc-500">No participants yet.</p>
      )}

      {!loading && participants.length > 0 && (
        <ul className="mt-2 space-y-2">
          {participants.map((participant) => (
            <li
              key={participant.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-zinc-800 bg-zinc-950/70 px-3 py-2 text-sm"
            >
              {participant.userId && (
                <PictureControl
                  slot="AVATAR"
                  targetId={participant.userId}
                  label={participant.displayName}
                />
              )}
             <div>
                <div className="flex items-center gap-2">
                  <p className="font-medium text-zinc-100">
                    {participant.displayName}
                  </p>
                  {participant.captain && (
                    <span className="rounded bg-amber-500/20 px-1.5 py-0.5 text-[11px] font-semibold text-amber-300">
                      Captain
                    </span>
                  )}
                </div>
                <p className="text-xs text-zinc-500">{participant.status}</p>
              </div>

              {canManage ? (
                <div className="flex flex-wrap items-center gap-2">
                  <select
                    aria-label={`Team for ${participant.displayName}`}
                    value={participant.teamId ?? ""}
                    disabled={savingId === participant.id}
                    onChange={(event) =>
                      void handleTeamChange(participant.id, event.target.value)
                    }
                    className="ui-control max-w-full sm:w-auto"
                  >
                    <option value="">Unassigned</option>
                    {teams.map((team) => (
                      <option key={team.id} value={team.id}>
                        {team.name}
                      </option>
                    ))}
                  </select>

                  {participant.teamId && participant.status === "active" && (
                    <button
                      type="button"
                      disabled={savingId === participant.id}
                      onClick={() =>
                        void handleCaptainToggle(
                          participant.id,
                          !participant.captain,
                        )
                      }
                      className="rounded border border-zinc-700 px-2.5 py-1.5 text-xs text-zinc-200 hover:bg-zinc-800 disabled:opacity-40"
                    >
                      {participant.captain ? "Remove captain" : "Make captain"}
                    </button>
                  )}
                </div>
              ) : (
                <span className="text-sm text-muted">
                  {participant.teamId
                    ? (teams.find((team) => team.id === participant.teamId)
                        ?.name ?? "Team unavailable")
                    : "Unassigned"}
                </span>
              )}
             
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
