"use client";

import { useEffect, useState } from "react";
import { Badge, Card } from "@/component/ui";
import { RosterAvatar } from "@/component/Event/Roster/RosterAvatar";
import {
  PlayerPointsController,
  type StarPlayersRankingResponse,
} from "@/modules/Event/PlayerPoints";
import type { EventRosterResponse } from "@/modules/Event/Roster";

interface StarPlayersCardProps {
  eventId: string;
  /** Changes when something happened (snapshot version): the ranking is refetched. */
  refreshKey?: string | number;
  roster: EventRosterResponse | null;
  /** Phone: top N only. The TV shows everyone in a scrollable list. */
  limit?: number;
  size?: "tv" | "phone";
  /** Use this ranking instead of loading one (TV preview). */
  override?: StarPlayersRankingResponse;
}

/** Personal points ranking. Follows the scoreboard: hidden when it is hidden. */
export function StarPlayersCard({
  eventId,
  refreshKey,
  roster,
  limit = 5,
  size = "tv",
  override,
}: StarPlayersCardProps) {
  const [loaded, setLoaded] = useState<StarPlayersRankingResponse | null>(null);

  useEffect(() => {
    if (override) return;
    let cancelled = false;
    PlayerPointsController.ranking(eventId)
      .then((data) => {
        if (!cancelled) setLoaded(data);
      })
      .catch(() => {
        // Keep the last ranking.
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, refreshKey, override]);

  const ranking = override ?? loaded;
  const tv = size === "tv";
  const hidden = !ranking || ranking.hiddenFromViewer;
  const rows = ranking?.rows ?? [];

  // Phones only show the card when there is something to show.
  if (!tv && (hidden || rows.length === 0)) {
    return null;
  }

  const avatarOf = (userId: string | null) => {
    if (!userId || !roster) return null;
    for (const team of roster.teams) {
      const member = team.members.find((m) => m.userId === userId);
      if (member) return member.picture;
    }
    return null;
  };

  const shown = tv ? rows : rows.slice(0, limit);

  return (
    <Card
      className={
        tv ? "flex flex-1 flex-col gap-4 p-6" : "space-y-3"
      }
    >
      <div className="flex shrink-0 items-center justify-between gap-2">
        <h2 className={tv ? "text-2xl font-bold 2xl:text-3xl" : "text-lg font-semibold"}>
          ★ Star players
        </h2>
        {ranking?.frozenAt && <Badge tone="warning">Frozen</Badge>}
      </div>

      {hidden ? (
        <p className="text-lg text-muted">Revealed with the scores.</p>
      ) : rows.length === 0 ? (
        <p className="text-lg text-muted">No star players yet.</p>
      ) : (
        <ol
          className={
            tv
              ? "max-h-[45dvh] min-h-0 flex-1 space-y-2 overflow-y-auto pr-1 [scrollbar-width:thin]"
              : "space-y-2"
          }
        >
          {shown.map((row) => (
            <li
              key={row.participantId}
              className={[
                "flex items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-3 py-2",
                tv ? "text-lg 2xl:text-xl" : "text-sm",
              ].join(" ")}
            >
              <div className="flex min-w-0 items-center gap-3">
                <span className="w-8 font-mono font-bold text-muted">#{row.rank}</span>
                <RosterAvatar
                  picture={avatarOf(row.userId)}
                  label={row.displayName}
                  kind="person"
                  size={tv ? "sm" : "xs"}
                  color={row.teamColor}
                />
                <div className="min-w-0">
                  <p className="truncate font-semibold">{row.displayName}</p>
                  <p className="truncate text-xs text-muted">{row.teamName}</p>
                </div>
              </div>
              <span className="shrink-0 font-mono font-bold">★ {row.points}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
