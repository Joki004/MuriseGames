"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { PageShell } from "@/component/Layout/PageShell";
import { EventCreateForm } from "@/component/Event/EventCreateForm";
import { AsyncContent } from "@/component/ui/AsyncContent";
import {
  Badge,
  Button,
  Card,
  Dialog,
  EmptyState,
} from "@/component/ui";
import { EventController } from "@/modules/Event/event.controller";
import type { EventResponse } from "@/modules/Event/event.type";

export function formatEventTime(event: EventResponse) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: event.timezone,
  }).format(new Date(event.startsAt));
}

export function DashboardView() {
  return (
    <RequireAccount>
      <DashboardContent />
    </RequireAccount>
  );
}

function DashboardContent() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(
    () => EventController.listMine(page, 12),
    [page],
  );

  return (
    <PageShell
      title="Your game nights"
      description="Plan the next one, revisit an event, or join your friends."
      action={
        <Button onClick={() => setCreating(true)}>
          + Create event
        </Button>
      }
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">My events</h2>

        <Button
          variant="secondary"
          onClick={() => setRevision((value) => value + 1)}
        >
          Refresh
        </Button>
      </div>

      <AsyncContent
        key={`${page}:${revision}`}
        load={load}
        label="Loading your events…"
      >
        {(result) => (
          <>
            {result.content.length === 0 ? (
              <EmptyState
                title={
                  page === 0
                    ? "Your next game night starts here"
                    : "No events on this page"
                }
                description="Create an event or follow an invitation to join one."
                action={
                  <Button onClick={() => setCreating(true)}>
                    Create event
                  </Button>
                }
              />
            ) : (
              <ul className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
                {result.content.map((event) => (
                  <li key={event.id} className="min-w-0">
                    <Card className="flex h-full flex-col gap-5">
                      <div className="flex flex-wrap gap-2">
                        <Badge
                          tone={event.status === "live" ? "success" : "accent"}
                        >
                          {event.status}
                        </Badge>
                        <Badge>{event.visibility}</Badge>
                      </div>

                      <div className="min-w-0">
                        <h3 className="break-words text-xl font-semibold">
                          <Link
                            href={`/events/${event.id}`}
                            className="hover:text-highlight"
                          >
                            {event.name}
                          </Link>
                        </h3>

                        {event.theme && (
                          <p className="mt-2 break-words text-muted">
                            {event.theme}
                          </p>
                        )}
                      </div>

                      <div className="space-y-1 text-sm text-muted">
                        <p>
                          <time dateTime={event.startsAt}>
                            {formatEventTime(event)}
                          </time>
                        </p>
                        <p>{event.timezone}</p>
                        {event.venue && (
                          <p className="break-words">{event.venue}</p>
                        )}
                      </div>

                      <Link
                        href={`/events/${event.id}`}
                        className="ui-button ui-button--secondary mt-auto"
                      >
                        Open event
                      </Link>
                    </Card>
                  </li>
                ))}
              </ul>
            )}

            <footer className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-sm text-muted">
                {result.page.totalElements} event(s)
                {result.page.totalPages > 0 &&
                  ` · Page ${result.page.number + 1} of ${result.page.totalPages}`}
              </p>

              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((value) => value - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="secondary"
                  disabled={page + 1 >= result.page.totalPages}
                  onClick={() => setPage((value) => value + 1)}
                >
                  Next
                </Button>
              </div>
            </footer>
          </>
        )}
      </AsyncContent>

      <Dialog
        open={creating}
        onOpenChange={setCreating}
        title="Create an event"
        description="Give your next game night a name and a date."
        busy={saving}
      >
        {creating && (
          <EventCreateForm
            onBusyChange={setSaving}
            onCreated={(event) => {
              setCreating(false);
              router.push(`/events/${event.id}`);
            }}
          />
        )}
      </Dialog>
    </PageShell>
  );
}