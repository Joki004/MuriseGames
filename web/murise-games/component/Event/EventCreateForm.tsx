"use client";

import { useRef, useState, type FormEvent } from "react";
import { EventController } from "@/modules/Event/event.controller";
import type {
  EventResponse,
  EventVisibility,
} from "@/modules/Event/event.type";

interface EventCreateFormProps {
  onCreated: (event: EventResponse) => void;
}

const inputClass =
  "mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 " +
  "text-sm text-zinc-100 outline-none focus:border-emerald-500";

export function EventCreateForm({ onCreated }: EventCreateFormProps) {
  const submittingRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (submittingRef.current) return;

    const form = event.currentTarget;
    const fields = new FormData(form);

    submittingRef.current = true;
    setSubmitting(true);
    setError(null);

    try {
      const created = await EventController.create({
        name: String(fields.get("name") ?? ""),
        theme: String(fields.get("theme") ?? ""),
        startsAtLocal: String(fields.get("startsAt") ?? ""),
        venue: String(fields.get("venue") ?? ""),
        visibility: String(
          fields.get("visibility") ?? "private",
        ) as EventVisibility,
      });

      form.reset();
      onCreated(created);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not create the event.",
      );
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-xl border border-zinc-800 bg-zinc-900/70 p-6">
      <h2 className="text-lg font-semibold">Create an event</h2>

      <form onSubmit={handleSubmit} className="mt-4">
        <fieldset disabled={submitting} className="space-y-4">
          <div>
            <label htmlFor="event-name" className="text-sm text-zinc-300">
              Event name
            </label>
            <input
              id="event-name"
              name="name"
              required
              maxLength={160}
              placeholder="FOR THE PLOT"
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="event-theme" className="text-sm text-zinc-300">
              Theme — optional
            </label>
            <input
              id="event-theme"
              name="theme"
              maxLength={300}
              placeholder="Movies and television"
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="event-start" className="text-sm text-zinc-300">
              Date and time
            </label>
            <input
              id="event-start"
              name="startsAt"
              type="datetime-local"
              required
              step={60}
              aria-describedby="event-time-help"
              className={inputClass}
            />
            <p id="event-time-help" className="mt-1 text-xs text-zinc-400">
              Enter the time in your device’s local timezone. The saved
              timezone appears on the event card.
            </p>
          </div>

          <div>
            <label htmlFor="event-venue" className="text-sm text-zinc-300">
              Venue — optional
            </label>
            <input
              id="event-venue"
              name="venue"
              maxLength={300}
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="event-visibility" className="text-sm text-zinc-300">
              Joining policy
            </label>
            <select
              id="event-visibility"
              name="visibility"
              defaultValue="private"
              className={inputClass}
            >
              <option value="private">Private — organizer approval</option>
              <option value="public">Public — automatic admission</option>
            </select>
            <p className="mt-1 text-xs text-zinc-400">
              Saved for the invitation flow, which is being added next.
            </p>
          </div>

          <button
            type="submit"
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold
              text-white hover:bg-emerald-500 disabled:opacity-50"
          >
            {submitting ? "Creating…" : "Create event"}
          </button>
        </fieldset>

        {error && (
          <div role="alert" className="mt-4 text-sm text-rose-300">
            <p>{error}</p>
            <p className="mt-1 text-xs">
              If the connection failed, refresh your events before retrying
              to check whether the event was saved.
            </p>
          </div>
        )}
      </form>
    </section>
  );
}