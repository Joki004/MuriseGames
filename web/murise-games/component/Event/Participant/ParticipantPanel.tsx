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
                <p className="font-medium text-zinc-100">
                  {participant.displayName}
                </p>
                <p className="text-xs text-zinc-500">{participant.status}</p>
              </div>

              {canManage ? (
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
