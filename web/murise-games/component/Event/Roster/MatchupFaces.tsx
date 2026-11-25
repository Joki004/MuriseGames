"use client";

import {
  rosterTeamFor,
  type EventRosterResponse,
} from "@/modules/Event/Roster";
import { TeamIdentity, type TeamIdentitySize } from "./TeamIdentity";

interface FixtureTeam {
  teamId: string;
  teamName: string;
  teamColor: string;
  side: string;
}

interface MatchupFacesProps {
  roster: EventRosterResponse | null;
  teams: FixtureTeam[];
  size?: TeamIdentitySize;
  members?: "none" | "faces" | "names";
  highlightUserId?: string | null;
}

const VS_CLASS: Record<TeamIdentitySize, string> = {
  sm: "text-sm",
  md: "text-lg",
  lg: "text-3xl",
  xl: "text-5xl",
};

/** Teams side by side with "VS" between them. */
export function MatchupFaces({
  roster,
  teams,
  size = "md",
  members = "faces",
  highlightUserId = null,
}: MatchupFacesProps) {
  const resolved = teams
    .filter((t) => t.side !== "bye")
    .map((t) => rosterTeamFor(roster, t.teamId, t.teamName, t.teamColor));

  return (
    <div className="flex w-full items-center justify-center gap-4">
      {resolved.map((team, index) => (
        <div key={team.teamId} className="contents">
          {index > 0 && (
            <span
              className={[
                "shrink-0 font-extrabold tracking-widest text-muted",
                VS_CLASS[size],
              ].join(" ")}
            >
              VS
            </span>
          )}
          <div className="min-w-0 flex-1">
            <TeamIdentity
              team={team}
              size={size}
              members={members}
              align="center"
              highlightUserId={highlightUserId}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
