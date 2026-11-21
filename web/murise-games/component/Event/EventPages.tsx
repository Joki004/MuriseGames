"use client";

import { useState } from "react";
import { useEventDetail } from "./EventShell";
import { TeamPanel } from "./Team/TeamPanel";
import { ParticipantPanel } from "./Participant/ParticipantPanel";
import { JoinRequestPanel } from "./JoinRequest/JoinRequestPanel";
import { InvitePanel } from "./Invite/InvitePanel";
import { EventGamePanel } from "./EventGame/EventGamePanel";
import { ScoreboardPanel } from "./Scoring/ScoreboardPanel";
import {
  Alert,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";

export function EventOverview() {
  const { event, canManage } = useEventDetail();

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>About this event</CardTitle>
          <CardDescription>
            Everything you need before game night.
          </CardDescription>
        </CardHeader>

        <dl className="grid gap-6 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-muted">Venue</dt>
            <dd className="mt-1 break-words">
              {event.venue || "To be announced"}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted">Joining policy</dt>
            <dd className="mt-1">
              {event.visibility === "private"
                ? "Invitation with organizer approval"
                : "Automatic admission through an invitation"}
            </dd>
          </div>
        </dl>

        {canManage && (
          <p className="mt-6 text-sm text-muted">
            You organize this event. Joining a team as a player is a
            separate action.
          </p>
        )}
      </Card>

      {event.scoreboardVisibility === "hidden" && !canManage ? (
        <Alert tone="info">The scoreboard has not been revealed yet.</Alert>
      ) : (
        <ScoreboardPanel eventId={event.id} />
      )}
    </div>
  );
}

export function EventPeople() {
  const { event, canManage } = useEventDetail();
  const [rosterRevision, setRosterRevision] = useState(0);

  function refreshRoster() {
    setRosterRevision((value) => value + 1);
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">People</h2>
        <p className="mt-1 text-muted">
          Teams, participants and admission requests.
        </p>
      </div>

      <TeamPanel eventId={event.id} onChanged={refreshRoster} />

      {canManage && (
        <JoinRequestPanel
          key={`waiting:${rosterRevision}`}
          eventId={event.id}
          onApproved={refreshRoster}
        />
      )}

      <ParticipantPanel
        eventId={event.id}
        canManage={canManage}
        refreshTrigger={rosterRevision}
      />
    </div>
  );
}

export function EventInvitations() {
  const { event, canManage } = useEventDetail();

  if (!canManage) {
    return (
      <Alert tone="info">
        Only event organizers can manage invitations.
      </Alert>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Invitations</h2>
        <p className="mt-1 text-muted">
          Create invitation links, check their usage and revoke access.
        </p>
      </div>

      <InvitePanel eventId={event.id} />
    </div>
  );
}

export function EventGames() {
  const { event, canManage } = useEventDetail();

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Event games</h2>
        <p className="mt-1 text-muted">
          The lineup, saved rules and house rules for this game night.
        </p>
      </div>

      <EventGamePanel eventId={event.id} canManage={canManage} />
    </div>
  );
}