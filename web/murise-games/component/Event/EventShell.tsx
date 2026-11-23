"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { AsyncContent } from "@/component/ui/AsyncContent";
import { Badge } from "@/component/ui";
import { EventController } from "@/modules/Event/event.controller";
import type { EventDetailResponse } from "@/modules/Event/event.type";
import { formatEventTime } from "@/component/Dashboard/DashboardView";
import { PictureControl } from "@/component/Picture/PictureControl";
import { EventEditButton } from "@/component/Event/EventEditDialog";

interface EventContextValue {
  detail: EventDetailResponse;
  replaceDetail: (next: EventDetailResponse) => void;
}

const EventContext = createContext<EventContextValue | null>(null);

export function useEventDetail() {
  const context = useContext(EventContext);

  if (!context) {
    throw new Error("Event pages must be rendered inside EventShell.");
  }

  return {
    ...context.detail,
    canManage: context.detail.roles.includes("ORGANIZER"),
    /** Swap in fresh event details after an edit, without reloading the page. */
    replaceDetail: context.replaceDetail,
  };
}

/** Holds the loaded event so edits can update every page in place. */
function EventProvider({
  initial,
  children,
}: {
  initial: EventDetailResponse;
  children: ReactNode;
}) {
  const [detail, setDetail] = useState(initial);

  return (
    <EventContext.Provider value={{ detail, replaceDetail: setDetail }}>
      {children}
    </EventContext.Provider>
  );
}

export function EventShell({ children }: { children: ReactNode }) {
  const { eventId } = useParams<{ eventId: string }>();

  return (
    <RequireAccount>
      <EventLoader key={eventId} eventId={eventId}>
        {children}
      </EventLoader>
    </RequireAccount>
  );
}

function EventLoader({
  eventId,
  children,
}: {
  eventId: string;
  children: ReactNode;
}) {
  const load = useCallback(() => EventController.getDetail(eventId), [eventId]);

  return (
    <main
      id="main-content"
      className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8 sm:px-6"
    >
      <Link
        href="/dashboard"
        className="text-sm text-highlight hover:underline"
      >
        ← All events
      </Link>

      <AsyncContent load={load} label="Opening event…">
        {(detail) => (
          <EventProvider initial={detail}>
            <EventHeader />
            <div className="min-w-0">{children}</div>
          </EventProvider>
        )}
      </AsyncContent>
    </main>
  );
}

function EventHeader() {
  const pathname = usePathname();
  const { event, roles, canManage, replaceDetail } = useEventDetail();
  const base = `/events/${event.id}`;

  const links = [
    { href: base, label: "Overview" },
    { href: `${base}/people`, label: "People" },
    { href: `${base}/games`, label: "Games" },
    ...(canManage
      ? [
          { href: `${base}/invitations`, label: "Invitations" },
          { href: `${base}/clues`, label: "Clues" },
          { href: `${base}/scoring`, label: "Scoring" },
          { href: `/events/${event.id}/schedule`, label: "Schedule" },
        ]
      : []),
    ...(roles.includes("REFEREE")
      ? [{ href: `${base}/referee`, label: "Referee" }]
      : []),
  ];

  return (
    <header className="space-y-6">
      <PictureControl
        slot="EVENT_COVER"
        targetId={event.id}
        label={event.theme || event.name}
        editable={canManage}
      />
      <div className="space-y-3">
        <div className="flex flex-wrap gap-2">
          <Badge tone={event.status === "live" ? "success" : "accent"}>
            {event.status}
          </Badge>
          <Badge>{event.visibility}</Badge>
          {canManage && <Badge tone="warning">Organizer</Badge>}
        </div>

        <PictureControl
          slot="EVENT_ICON"
          targetId={event.id}
          label={event.name}
          editable={canManage}
        />
        <h1 className="break-words text-3xl font-semibold tracking-tight sm:text-4xl">
          {event.name}
        </h1>

        {event.theme && (
          <p className="break-words text-lg text-muted">{event.theme}</p>
        )}

        <p className="text-sm text-muted">
          <time dateTime={event.startsAt}>{formatEventTime(event)}</time>
          {" · "}
          {event.timezone}
          {event.venue ? ` · ${event.venue}` : ""}
        </p>

        {canManage && (
          <EventEditButton event={event} onSaved={replaceDetail} />
        )}
      </div>

      <nav
        aria-label="Event navigation"
        className="grid grid-cols-2 gap-2 rounded-card border border-border bg-surface p-2 sm:flex sm:flex-wrap"
      >
        {links.map(({ href, label }) => {
          const active = pathname === href;

          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={[
                "rounded-control px-4 py-3 text-center text-sm font-medium",
                active
                  ? "bg-primary text-white"
                  : "text-muted hover:bg-surface-raised hover:text-foreground",
              ].join(" ")}
            >
              {label}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}
