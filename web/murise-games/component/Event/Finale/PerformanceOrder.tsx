"use client";

import { Badge, Card } from "@/component/ui";
import { RosterAvatar } from "@/component/Event/Roster/RosterAvatar";
import { TeamIdentity } from "@/component/Event/Roster/TeamIdentity";
import type { RevealListResponse } from "@/modules/Event/Finale";
import type { EventRosterResponse, RosterTeam } from "@/modules/Event/Roster";

/** The codes of the two finale activities in the game catalog. */
export const REHEARSAL_GAME_CODE = "final-rehearsal";
export const PREMIERE_GAME_CODE = "secret-scene-premiere";

export interface PerformanceStatus {
  order: RosterTeam[];
  performed: Set<string>;
  /** The team whose title was revealed last: it is on stage now. */
  nowPerforming: RosterTeam | null;
  upNext: RosterTeam | null;
}

/**
 * Performance order = the teams' display order (People → Teams → Edit →
 * Display order). A team has performed once its title is revealed. Titles are
 * never needed here, so nothing secret is shown.
 */
export function performanceStatus(
  roster: EventRosterResponse | null,
  reveals: RevealListResponse | null,
): PerformanceStatus {
  const order = roster?.teams ?? [];
  const revealed = reveals?.reveals ?? [];
  const performed = new Set(revealed.map((r) => r.teamId));

  const latest = [...revealed].sort(
    (a, b) => Date.parse(b.revealedAt) - Date.parse(a.revealedAt),
  )[0];

  return {
    order,
    performed,
    nowPerforming: latest ? (order.find((t) => t.teamId === latest.teamId) ?? null) : null,
    upNext: order.find((t) => !performed.has(t.teamId)) ?? null,
  };
}

/** TV, finale: the team on stage, big, with who is next. */
export function NowPerformingHero({ status }: { status: PerformanceStatus }) {
  if (!status.nowPerforming && !status.upNext) return null;

  return (
    <Card className="flex flex-wrap items-center justify-around gap-8 p-6">
      {status.nowPerforming && (
        <div className="space-y-3 text-center">
          <Badge tone="accent" className="text-base">
            🎭 Now performing
          </Badge>
          <TeamIdentity team={status.nowPerforming} size="lg" members="names" align="center" />
        </div>
      )}
      {status.upNext && (
        <div className="space-y-3 text-center opacity-80">
          <Badge className="text-base">Up next</Badge>
          <TeamIdentity team={status.upNext} size="md" members="faces" align="center" />
        </div>
      )}
    </Card>
  );
}

/** The running order, with who has performed. TV (rehearsal) or phone. */
export function PerformanceOrderList({
  status,
  variant,
  highlightTeamId = null,
}: {
  status: PerformanceStatus;
  variant: "tv" | "phone";
  highlightTeamId?: string | null;
}) {
  const tv = variant === "tv";

  return (
    <ol className={tv ? "grid gap-4 sm:grid-cols-2 xl:grid-cols-3" : "space-y-2"}>
      {status.order.map((team, index) => {
        const done = status.performed.has(team.teamId);
        const onStage = status.nowPerforming?.teamId === team.teamId;
        const mine = highlightTeamId === team.teamId;

        return (
          <li
            key={team.teamId}
            className={[
              "flex items-center gap-3 rounded-[var(--ui-radius-control)] border px-3 py-2",
              onStage || mine ? "border-accent" : "border-border",
              done && !onStage ? "opacity-60" : "",
              tv ? "text-xl" : "text-sm",
            ].join(" ")}
          >
            <span className="w-8 font-mono font-bold text-muted">#{index + 1}</span>
            <RosterAvatar
              picture={team.picture}
              label={team.name}
              kind="team"
              size={tv ? "lg" : "sm"}
              color={team.color}
            />
            <span className="min-w-0 flex-1 truncate font-semibold">
              {team.name}
              {mine ? " (you)" : ""}
            </span>
            {onStage ? (
              <Badge tone="accent">On stage</Badge>
            ) : done ? (
              <Badge tone="success">Performed</Badge>
            ) : status.upNext?.teamId === team.teamId && status.performed.size > 0 ? (
              <Badge>Next</Badge>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

/** Phone: where my team is in the running order, during rehearsal and the premiere. */
export function PerformanceOrderCard({
  status,
  myTeamId,
  rehearsal,
}: {
  status: PerformanceStatus;
  myTeamId: string | null;
  rehearsal: boolean;
}) {
  if (status.order.length === 0) return null;

  const position = status.order.findIndex((t) => t.teamId === myTeamId);
  const mineOnStage = status.nowPerforming?.teamId === myTeamId;
  const mineNext = status.upNext?.teamId === myTeamId;

  return (
    <Card className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <p className="font-semibold">
          {rehearsal ? "🎭 Final rehearsal" : "🎬 Secret Scene Premiere"}
        </p>
        {position >= 0 && (
          <Badge tone={mineOnStage || mineNext ? "accent" : "neutral"}>
            {mineOnStage
              ? "You're on stage!"
              : mineNext && !rehearsal
                ? "You're up next"
                : `You perform #${position + 1} of ${status.order.length}`}
          </Badge>
        )}
      </div>
      {rehearsal && (
        <p className="text-sm text-muted">
          Rehearse your registered scene, check props and roles, and be ready to set up in
          under a minute.
        </p>
      )}
      <PerformanceOrderList status={status} variant="phone" highlightTeamId={myTeamId} />
    </Card>
  );
}
