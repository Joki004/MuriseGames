"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { useAuth } from "@/component/Auth/AuthProvider";
import { useEventDetail } from "./EventShell";
import { TeamPanel } from "./Team/TeamPanel";
import { ParticipantPanel } from "./Participant/ParticipantPanel";
import { JoinRequestPanel } from "./JoinRequest/JoinRequestPanel";
import { InvitePanel } from "./Invite/InvitePanel";
import { EventGamePanel } from "./EventGame/EventGamePanel";
import { ScoreboardPanel } from "./Scoring/ScoreboardPanel";
import { EventRolePanel } from "./Role/EventRolePanel";
import { AsyncContent } from "@/component/ui/AsyncContent";
import { ParticipantController } from "@/modules/Event/Participant";
import { EventLivePanel } from "./Live/EventLivePanel";
import { RefereeAssignmentPanel } from "./Referee/RefereeAssignmentPanel";

import {
  Alert,
  Badge,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";

export function EventOverview() {
  const { event, canManage } = useEventDetail();
  const { account } = useAuth();

  const loadRoster = useCallback(async () => {
    const [participants, teams] = await Promise.all([
      ParticipantController.list(event.id),
      ParticipantController.listActiveTeamOptions(event.id),
    ]);
    return { participants, teams };
  }, [event.id]);

  return (
    <div className="space-y-6">
      {canManage ? (
        <section
          aria-label="Organizer actions"
          className="grid gap-4 md:grid-cols-3"
        >
          <Card className="flex flex-col justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold">People &amp; teams</h3>
              <p className="mt-1 text-sm text-muted">
                Create teams, approve waiting room requests, and assign players.
              </p>
            </div>
            <Link
              href={`/events/${event.id}/people`}
              className="ui-button ui-button--secondary"
            >
              Manage people
            </Link>
          </Card>

          <Card className="flex flex-col justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold">Invitations</h3>
              <p className="mt-1 text-sm text-muted">
                Generate invite links, check usage, or revoke access.
              </p>
            </div>
            <Link
              href={`/events/${event.id}/invitations`}
              className="ui-button ui-button--secondary"
            >
              Manage invitations
            </Link>
          </Card>

          <Card className="flex flex-col justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold">Selected games</h3>
              <p className="mt-1 text-sm text-muted">
                Build the lineup, update game status, and add house rules.
              </p>
            </div>
            <Link
              href={`/events/${event.id}/games`}
              className="ui-button ui-button--secondary"
            >
              Manage games
            </Link>
          </Card>
        </section>
      ) : (
        <section
          aria-label="Player overview"
          className="grid gap-4 md:grid-cols-2"
        >
          <Card className="flex flex-col justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold">Your team</h3>
              <div className="mt-2">
                <AsyncContent load={loadRoster} label="Checking your team…">
                  {({ participants, teams }) => {
                    const me = participants.find(
                      (p) => account?.id && p.userId === account.id,
                    );
                    if (!me) {
                      return (
                        <p className="text-sm text-muted">
                          You are not on the active roster yet.
                        </p>
                      );
                    }

                    const team = me.teamId
                      ? teams.find((t) => t.id === me.teamId)
                      : undefined;

                    return team ? (
                      <div className="flex items-center gap-2">
                        <Badge tone="accent">{team.name}</Badge>
                        {me.captain && <Badge tone="success">Captain</Badge>}
                      </div>
                    ) : (
                      <p className="text-sm text-muted">
                        Waiting for the organizer to assign your team.
                      </p>
                    );
                  }}
                </AsyncContent>
              </div>
            </div>
            <Link
              href={`/events/${event.id}/people`}
              className="ui-button ui-button--secondary"
            >
              View teams &amp; roster
            </Link>
          </Card>

          <Card className="flex flex-col justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold">Games &amp; rules</h3>
              <p className="mt-1 text-sm text-muted">
                Review the selected lineup, scoring, and house rules for this
                event.
              </p>
            </div>
            <Link
              href={`/events/${event.id}/games`}
              className="ui-button ui-button--secondary"
            >
              View games &amp; rules
            </Link>
          </Card>
        </section>
      )}
      {canManage ? (
        <div className="flex flex-wrap gap-2">
          <Link
            href={`/events/${event.id}/host`}
            className="ui-button ui-button--primary"
          >
            ▶ Run the night (Host)
          </Link>
          <Link
            href={`/events/${event.id}/tv`}
            target="_blank"
            className="ui-button ui-button--secondary"
          >
            Open TV screen ↗
          </Link>
          <Link
            href={`/events/${event.id}/play`}
            className="ui-button ui-button--secondary"
          >
            Player screen
          </Link>
        </div>
      ) : (
        <Card className="space-y-3">
          <div>
            <h3 className="text-base font-semibold">Game night screen</h3>
            <p className="mt-1 text-sm text-muted">
              Open it on your phone and keep it open all night: it shows your
              next match, the Alibi questions and the clues.
            </p>
          </div>
          <Link
            href={`/events/${event.id}/play`}
            className="ui-button ui-button--primary w-full"
          >
            Open my game screen
          </Link>
        </Card>
      )}
      <EventLivePanel eventId={event.id} canManage={canManage} />
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
            You organize this event. Joining a team as a player is a separate
            action.
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
      {canManage && <EventRolePanel eventId={event.id} />}
      {canManage && <RefereeAssignmentPanel eventId={event.id} />}
    </div>
  );
}

export function EventInvitations() {
  const { event, canManage } = useEventDetail();

  if (!canManage) {
    return (
      <Alert tone="info">Only event organizers can manage invitations.</Alert>
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
