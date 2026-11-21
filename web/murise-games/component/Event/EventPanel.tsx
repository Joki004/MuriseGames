"use client";

import { useEffect, useState } from "react";
import { EventController } from "@/modules/Event/event.controller";
import { JoinRequestPanel } from "./JoinRequest/JoinRequestPanel";
import { ParticipantPanel } from "@/component/Event/Participant/ParticipantPanel";
import type { EventPage, EventResponse } from "@/modules/Event/event.type";
import { EventCreateForm } from "./EventCreateForm";
import { TeamPanel } from "@/component/Event/Team/TeamPanel";
import { EventGamePanel } from "@/component/Event/EventGame/EventGamePanel";
import { ScoreboardPanel } from "@/component/Event/Scoring/ScoreboardPanel";
import { EventService } from "@/modules";
const buttonClass =
  "rounded-lg border border-zinc-700 px-3 py-1.5 text-sm " +
  "text-zinc-200 hover:bg-zinc-800 disabled:opacity-40";

function formatEventTime(event: EventResponse): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: event.timezone,
  }).format(new Date(event.startsAt));
}

export function EventPanel() {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<EventPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<EventResponse | null>(null);
  const [rosterRevision, setRosterRevision] = useState(0);
  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const response = await EventController.listMine(page, 10);

        if (active) {
          setResult(response);
          setError(null);
        }
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load events.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, [page, revision]);

  function reload(targetPage = page) {
    setLoading(true);
    setError(null);
    setResult(null);
    setPage(targetPage);
    setRevision((value) => value + 1);
  }

  function handleCreated(event: EventResponse) {
    setCreated(event);
    reload(0);
  }

  return (
    <div className="space-y-6">
      <EventCreateForm onCreated={handleCreated} />

      {created && (
        <div
          role="status"
          className="rounded-xl border border-emerald-800 bg-emerald-950/40
            p-4 text-sm text-emerald-200"
        >
          <p>
            Created <strong>{created.name}</strong>.
          </p>
          <p className="mt-1">
            Status: {created.status} · Visibility: {created.visibility}
          </p>
          <p className="mt-1 break-all font-mono text-xs">ID: {created.id}</p>
        </div>
      )}

      <section className="rounded-xl border border-zinc-800 bg-zinc-900/70 p-6">
        <header className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">My events</h2>
          <button
            type="button"
            onClick={() => reload()}
            disabled={loading}
            className={buttonClass}
          >
            Refresh
          </button>
        </header>

        {loading && (
          <p role="status" className="mt-4 text-sm text-zinc-400">
            Loading events…
          </p>
        )}

        {error && (
          <p role="alert" className="mt-4 text-sm text-rose-300">
            {error}
          </p>
        )}

        {!loading && !error && result && (
          <>
            {result.content.length === 0 ? (
              <p className="mt-4 text-sm text-zinc-400">
                No events on this page. Create your first event above.
              </p>
            ) : (
              <ul className="mt-4 space-y-3">
                {result.content.map((event) => (
                  <li
                    key={event.id}
                    className="rounded-xl border border-zinc-800 bg-zinc-950/60 p-4"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <h3 className="font-semibold">{event.name}</h3>
                      <span className="text-xs text-zinc-400">
                        <div className="flex items-center gap-2 text-xs text-zinc-400">
                          <select
                            aria-label={`Status for ${event.name}`}
                            value={event.status}
                            onChange={async (e) => {
                              await EventService.updateStatus(
                                event.id,
                                e.target.value,
                              );
                              reload();
                            }}
                            className="rounded border border-zinc-700 bg-zinc-900 px-2 py-0.5 text-xs text-zinc-200"
                          >
                            <option value="draft">draft</option>
                            <option value="published">published</option>
                            <option value="live">live</option>
                            <option value="completed">completed</option>
                            <option value="archived">archived</option>
                          </select>
                          <span>· {event.visibility}</span>
                        </div>
                      </span>
                    </div>

                    {event.theme && (
                      <p className="mt-1 text-sm text-zinc-300">
                        {event.theme}
                      </p>
                    )}

                    <p className="mt-2 text-sm text-zinc-400">
                      <time dateTime={event.startsAt}>
                        {formatEventTime(event)}
                      </time>
                      {" · "}
                      {event.timezone}
                    </p>

                    <TeamPanel eventId={event.id} />
                    <JoinRequestPanel
                      eventId={event.id}
                      onApproved={() => setRosterRevision((value) => value + 1)}
                    />
                    <ParticipantPanel
                      eventId={event.id}
                      refreshTrigger={rosterRevision}
                    />

                    <EventGamePanel eventId={event.id} />
                    <ScoreboardPanel eventId={event.id} />

                    {event.venue && (
                      <p className="mt-1 text-sm text-zinc-400">
                        {event.venue}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}

            <footer className="mt-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs text-zinc-400">
                {result.page.totalElements} event(s)
                {result.page.totalPages > 0 &&
                  ` · Page ${result.page.number + 1} of ${result.page.totalPages}`}
              </p>

              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => reload(page - 1)}
                  className={buttonClass}
                >
                  Previous
                </button>

                <button
                  type="button"
                  disabled={page + 1 >= result.page.totalPages}
                  onClick={() => reload(page + 1)}
                  className={buttonClass}
                >
                  Next
                </button>
              </div>
            </footer>
          </>
        )}
      </section>
    </div>
  );
}
