import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { EventService } from "./event.service";
import type {
  EventCreateInput,
  EventPage,
  EventResponse,
  EventDetailResponse,
  EventUpdateInput,
} from "./event.type";


function optionalText(value: string): string | null {
  return value.trim() || null;
}

function parseLocalDateTime(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);

  if (!match) {
    throw new Error("Choose a valid event date and time.");
  }

  const [, year, month, day, hour, minute] = match;
  const date = new Date(value);

  // Reject invalid dates and local times skipped by daylight-saving changes.
  if (
    Number.isNaN(date.getTime()) ||
    date.getFullYear() !== Number(year) ||
    date.getMonth() + 1 !== Number(month) ||
    date.getDate() !== Number(day) ||
    date.getHours() !== Number(hour) ||
    date.getMinutes() !== Number(minute)
  ) {
    throw new Error("This date or local time is invalid. Choose another time.");
  }

  return date;
}

export const EventController = {
  async update(
    eventId: string,
    input: EventUpdateInput,
  ): Promise<EventDetailResponse> {
    const name = input.name.trim();

    if (!name || name.length > 160) {
      throw new Error("Event name must contain between 1 and 160 characters.");
    }

    if (input.theme.length > 300 || input.venue.length > 300) {
      throw new Error("Theme and venue must each be at most 300 characters.");
    }

    if (!["public", "private"].includes(input.visibility)) {
      throw new Error("Choose public or private visibility.");
    }

    const startsAt = parseLocalDateTime(input.startsAtLocal);

    try {
      const response = await EventService.update(eventId, {
        name,
        theme: optionalText(input.theme),
        startsAt: startsAt.toISOString(),
        timezone: input.timezone,
        venue: optionalText(input.venue),
        visibility: input.visibility,
      });

      if (!response.data) {
        throw new Error("The server returned an empty event payload.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not save the event details."),
      );
    }
  },

  async create(input: EventCreateInput): Promise<EventResponse> {
    const name = input.name.trim();

    if (!name || name.length > 160) {
      throw new Error("Event name must contain between 1 and 160 characters.");
    }

    if (input.theme.length > 300 || input.venue.length > 300) {
      throw new Error("Theme and venue must each be at most 300 characters.");
    }

    if (!["public", "private"].includes(input.visibility)) {
      throw new Error("Choose public or private visibility.");
    }

    const startsAt = parseLocalDateTime(input.startsAtLocal);
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

    try {
      const response = await EventService.create({
        name,
        theme: optionalText(input.theme),
        startsAt: startsAt.toISOString(),
        timezone,
        venue: optionalText(input.venue),
        visibility: input.visibility,
      });

      if (!response.data) {
        throw new Error("The server returned an empty event payload.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not create the event."),
      );
    }
  },

  async listMine(page = 0, size = 10): Promise<EventPage> {
    try {
      const response = await EventService.listMine(page, size);

      if (!response.data) {
        throw new Error("The server returned an empty event-list payload.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load your events."),
      );
    }
  },

  async getDetail(eventId: string): Promise<EventDetailResponse> {
    try {
      const response = await EventService.getDetail(eventId);

      if (!response.data) {
        throw new Error("The server returned an empty event payload.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not open this event."),
      );
    }
  },
};
