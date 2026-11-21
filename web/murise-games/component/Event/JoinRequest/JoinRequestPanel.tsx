"use client";

import { useCallback, useEffect, useState } from "react";
import {
  JoinRequestController,
  type JoinRequestResponse,
} from "@/modules/Event/JoinRequest";
import {
  ParticipantController,
  type TeamOption,
} from "@/modules/Event/Participant";

interface JoinRequestPanelProps {
  eventId: string;
  onApproved?: () => void;
}

export function JoinRequestPanel({
  eventId,
  onApproved,
}: JoinRequestPanelProps) {
  const [requests, setRequests] = useState<JoinRequestResponse[] | null>(null);
  const [teams, setTeams] = useState<TeamOption[]>([]);
  const [selectedTeamByRequest, setSelectedTeamByRequest] = useState<
    Record<string, string>
  >({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const pending = await JoinRequestController.listForOrganizer(
        eventId,
        "pending"
      );
      setRequests(pending);

      if (pending !== null) {
        const activeTeams =
          await ParticipantController.listActiveTeamOptions(eventId);
        setTeams(activeTeams);
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not load waiting room."
      );
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleApprove(requestId: string) {
    setBusyId(requestId);
    setError(null);
    try {
      const chosenTeam = selectedTeamByRequest[requestId] || null;
      await JoinRequestController.approve(eventId, requestId, chosenTeam);
      await load();
      onApproved?.();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not approve request."
      );
    } finally {
      setBusyId(null);
    }
  }

  async function handleReject(requestId: string) {
    setBusyId(requestId);
    setError(null);
    try {
      await JoinRequestController.reject(eventId, requestId);
      await load();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not reject request."
      );
    } finally {
      setBusyId(null);
    }
  }

  // Non-organizer: hide waiting room completely.
  if (!loading && requests === null && !error) {
    return null;
  }

  return (
    <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/50 p-3">
      <div className="flex items-center justify-between gap-2">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
          Waiting Room {requests ? `(${requests.length})` : ""}
        </h4>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className="rounded border border-zinc-700 px-2 py-0.5 text-xs text-zinc-300 hover:bg-zinc-800 disabled:opacity-40"
        >
          Refresh
        </button>
      </div>

      {loading && (
        <p className="mt-2 text-xs text-zinc-400">Loading waiting room…</p>
      )}

      {error && (
        <p role="alert" className="mt-2 text-xs text-rose-300">
          {error}
        </p>
      )}

      {!loading && requests && requests.length === 0 && (
        <p className="mt-2 text-xs text-zinc-500">No pending join requests.</p>
      )}

      {!loading && requests && requests.length > 0 && (
        <ul className="mt-2 space-y-2">
          {requests.map((req) => (
            <li
              key={req.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-zinc-800 bg-zinc-950/70 px-3 py-2 text-sm"
            >
              <div>
                <p className="font-medium text-zinc-100">{req.displayName}</p>
                <p className="text-xs text-zinc-500">
                  Requested {new Date(req.createdAt).toLocaleString()}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <select
                  aria-label={`Assign team for ${req.displayName}`}
                  value={selectedTeamByRequest[req.id] ?? ""}
                  onChange={(e) =>
                    setSelectedTeamByRequest((prev) => ({
                      ...prev,
                      [req.id]: e.target.value,
                    }))
                  }
                  disabled={busyId === req.id}
                  className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-200"
                >
                  <option value="">No team yet</option>
                  {teams.map((team) => (
                    <option key={team.id} value={team.id}>
                      {team.name}
                    </option>
                  ))}
                </select>

                <button
                  type="button"
                  disabled={busyId === req.id}
                  onClick={() => void handleApprove(req.id)}
                  className="rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
                >
                  Approve
                </button>

                <button
                  type="button"
                  disabled={busyId === req.id}
                  onClick={() => void handleReject(req.id)}
                  className="rounded-md border border-rose-800 bg-rose-950/50 px-2.5 py-1 text-xs font-medium text-rose-200 hover:bg-rose-900/60 disabled:opacity-50"
                >
                  Reject
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}