"use client";

import { useRef, useState, type FormEvent } from "react";
import { EventController } from "@/modules/Event/event.controller";
import type {
  EventResponse,
  EventVisibility,
} from "@/modules/Event/event.type";
import { Alert, Button, Input, Select } from "@/component/ui";

interface EventCreateFormProps {
  onCreated: (event: EventResponse) => void;
  onBusyChange?: (busy: boolean) => void;
}

export function EventCreateForm({
  onCreated,
  onBusyChange,
}: EventCreateFormProps) {
  const busy = useRef(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;

    const fields = new FormData(event.currentTarget);

    busy.current = true;
    setSaving(true);
    onBusyChange?.(true);
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

      onCreated(created);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not create the event.",
      );
    } finally {
      busy.current = false;
      setSaving(false);
      onBusyChange?.(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <fieldset disabled={saving} className="space-y-5">
        <Input
          label="Event name"
          name="name"
          required
          maxLength={160}
          placeholder="FOR THE PLOT"
        />

        <Input
          label="Theme — optional"
          name="theme"
          maxLength={300}
          placeholder="Movies and television"
        />

        <Input
          label="Date and time"
          name="startsAt"
          type="datetime-local"
          required
          step={60}
          hint="Enter the time in your device’s local timezone. The event displays its saved timezone."
        />

        <Input
          label="Venue — optional"
          name="venue"
          maxLength={300}
        />

        <Select
          label="Joining policy"
          name="visibility"
          defaultValue="private"
          hint="Private events require organizer approval after someone follows an invitation."
        >
          <option value="private">Private — organizer approval</option>
          <option value="public">Public — automatic admission</option>
        </Select>

        <Button
          type="submit"
          fullWidth
          loading={saving}
          loadingText="Creating event…"
        >
          Create event
        </Button>
      </fieldset>

      {error && (
        <Alert tone="error">
          <p>{error}</p>
          <p className="mt-2">
            If the connection failed, check your events before retrying.
          </p>
        </Alert>
      )}
    </form>
  );
}