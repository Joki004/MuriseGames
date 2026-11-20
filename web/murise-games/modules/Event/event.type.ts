export type EventVisibility = "public" | "private";

export interface EventResponse {
  id: string;
  name: string;
  theme: string | null;
  startsAt: string;
  timezone: string;
  venue: string | null;
  status: "draft" | "published" | "live" | "completed" | "archived";
  visibility: EventVisibility;
  scoreboardVisibility: "public" | "hidden" | "frozen";
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EventCreateRequest {
  name: string;
  theme: string | null;
  startsAt: string;
  timezone: string;
  venue: string | null;
  visibility: EventVisibility;
}

export interface EventCreateInput {
  name: string;
  theme: string;
  startsAtLocal: string;
  venue: string;
  visibility: EventVisibility;
}

export interface EventPage {
  content: EventResponse[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}