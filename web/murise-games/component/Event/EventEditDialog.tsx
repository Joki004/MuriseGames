"use client";

import { useState, type FormEvent } from "react";
import { Alert, Button, Dialog, Input, Select } from "@/component/ui";
import { EventController } from "@/modules/Event/event.controller";
import type {
  EventDetailResponse,
  EventResponse,
  EventVisibility,
} from "@/modules/Event/event.type";

/** ISO instant -> "YYYY-MM-DDTHH:mm" in the device's local time, for datetime-local inputs. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`;
}

interface EventEditButtonProps {
  event: EventResponse;
  onSaved: (updated: EventDetailResponse) => void;
}

/** Organizer-only: edit name, theme, start time, venue and joining policy. */
export function EventEditButton({ event, onSaved }: EventEditButtonProps) {
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const closed = event.status === "completed" || event.status === "archived";

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (saving) return;

    const fields = new FormData(e.currentTarget);
    setSaving(true);
    setError(null);

    try {
      const updated = await EventController.update(event.id, {
        name: String(fields.get("name") ?? ""),
        theme: String(fields.get("theme") ?? ""),
        startsAtLocal: String(fields.get("startsAt") ?? ""),
        // Keep the event's own timezone; the start time is an absolute instant.
        timezone: event.timezone,
        venue: String(fields.get("venue") ?? ""),
        visibility: String(
          fields.get("visibility") ?? event.visibility,
        ) as EventVisibility,
      });
      onSaved(updated);
      setOpen(false);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not save the event details.",
      );
    } finally {
      setSaving(false);
    }
  }

  if (closed) return null;

  return (
    <>
      <Button
        variant="secondary"
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
      >
        Edit event details
      </Button>

      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (!saving) setOpen(next);
        }}
        busy={saving}
        title="Edit event details"
        description="Changes show on every screen straight away."
      >
        {/* key: re-mount with fresh defaults each time the dialog opens */}
        <form
          key={open ? event.updatedAt : "closed"}
          onSubmit={handleSubmit}
          className="mt-4 space-y-4"
        >
          <fieldset disabled={saving} className="space-y-4">
            <Input
              label="Event name"
              name="name"
              required
              maxLength={160}
              defaultValue={event.name}
            />

            <Input
              label="Theme — optional"
              name="theme"
              maxLength={300}
              defaultValue={event.theme ?? ""}
            />

            <Input
              label="Date and time"
              name="startsAt"
              type="datetime-local"
              required
              step={60}
              defaultValue={toLocalInput(event.startsAt)}
              hint={`In your device's local time. The event keeps its timezone (${event.timezone}).`}
            />

            <Input
              label="Venue — optional"
              name="venue"
              maxLength={300}
              defaultValue={event.venue ?? ""}
              placeholder="Name and address"
            />

            <Select
              label="Joining policy"
              name="visibility"
              defaultValue={event.visibility}
            >
              <option value="private">Private — organizer approval</option>
              <option value="public">Public — automatic admission</option>
            </Select>
          </fieldset>

          {error && <Alert tone="error">{error}</Alert>}

          <div className="flex flex-wrap gap-3">
            <Button type="submit" loading={saving} loadingText="Saving…">
              Save changes
            </Button>
            <Button
              variant="secondary"
              disabled={saving}
              onClick={() => setOpen(false)}
            >
              Cancel
            </Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
