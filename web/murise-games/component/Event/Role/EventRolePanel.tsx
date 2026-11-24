"use client";

import { useCallback, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";
import { AsyncContent } from "@/component/ui/AsyncContent";
import { ParticipantController } from "@/modules/Event/Participant";
import { EventRoleController, type EventRole } from "@/modules/Event/Role";
import { extractApiErrorMessage } from "@/modules/Api/api.service";
interface EventRolePanelProps {
  eventId: string;
}

const DELEGATABLE_ROLES: EventRole[] = ["ORGANIZER", "REFEREE", "CASE_WRITER"];

const ROLE_LABELS: Record<EventRole, string> = {
  ORGANIZER: "organizer",
  REFEREE: "referee",
  PLAYER: "player",
  VIEWER: "viewer",
  CASE_WRITER: "case writer",
};

export function EventRolePanel({ eventId }: EventRolePanelProps) {
  const [revision, setRevision] = useState(0);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [members, participants] = await Promise.all([
      EventRoleController.list(eventId),
      ParticipantController.list(eventId),
    ]);

    const nameByUserId = new Map<string, string>();
    for (const p of participants) {
      if (p.userId) nameByUserId.set(p.userId, p.displayName);
    }

    return { members, nameByUserId };
  }, [eventId]);

  async function toggleRole(
    userId: string,
    role: EventRole,
    currentlyHasRole: boolean,
  ) {
    const key = `${userId}:${role}`;
    setBusyKey(key);
    setError(null);
    try {
      if (currentlyHasRole) {
        await EventRoleController.revoke(eventId, userId, role);
      } else {
        await EventRoleController.grant(eventId, userId, role);
      }
      setRevision((v) => v + 1);
    } catch (err: unknown) {
      setError(extractApiErrorMessage(err, "Could not update event role."));
    } finally {
      setBusyKey(null);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>Organizers &amp; roles</CardTitle>
            <CardDescription>
              Appoint co-organizers, referees or case writers. A case writer
              can only write Alibi cases and open their own; remove the role
              once the cases are ready. Removing the last active organizer is
              blocked.
            </CardDescription>
          </div>
          <Button variant="secondary" onClick={() => setRevision((v) => v + 1)}>
            Refresh
          </Button>
        </div>
      </CardHeader>

      {error && (
        <div className="mb-4">
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      <AsyncContent key={revision} load={load} label="Loading event roles…">
        {({ members, nameByUserId }) => (
          <ul className="space-y-3">
            {members.map((member) => {
              const displayName =
                nameByUserId.get(member.userId) ??
                `Member ${member.userId.slice(0, 8)}`;

              return (
                <li
                  key={member.membershipId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3"
                >
                  <div className="min-w-0 space-y-1">
                    <p className="break-words font-medium">{displayName}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {member.roles.map((role) => (
                        <Badge
                          key={role}
                          tone={role === "ORGANIZER" ? "accent" : undefined}
                        >
                          {ROLE_LABELS[role] ?? role}
                        </Badge>
                      ))}
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {DELEGATABLE_ROLES.map((role) => {
                      const hasRole = member.roles.includes(role);
                      const key = `${member.userId}:${role}`;
                      return (
                        <Button
                          key={role}
                          variant={hasRole ? "danger" : "secondary"}
                          disabled={busyKey !== null}
                          onClick={() =>
                            void toggleRole(member.userId, role, hasRole)
                          }
                        >
                          {busyKey === key
                            ? "Saving…"
                            : hasRole
                              ? `Revoke ${ROLE_LABELS[role]}`
                              : `Make ${ROLE_LABELS[role]}`}
                        </Button>
                      );
                    })}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </AsyncContent>
    </Card>
  );
}
