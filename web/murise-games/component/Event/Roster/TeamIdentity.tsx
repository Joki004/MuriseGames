"use client";

import type { RosterTeam } from "@/modules/Event/Roster";
import { RosterAvatar, type RosterAvatarSize } from "./RosterAvatar";

export type TeamIdentitySize = "sm" | "md" | "lg" | "xl";

interface TeamIdentityProps {
  team: RosterTeam;
  size?: TeamIdentitySize;
  /** "faces": a row of member avatars. "names": avatars with names (TV, lobby). */
  members?: "none" | "faces" | "names";
  align?: "start" | "center";
  /** Ring around this user's avatar ("you"). */
  highlightUserId?: string | null;
}

const TEAM_SIZE: Record<TeamIdentitySize, RosterAvatarSize> = {
  sm: "md",
  md: "lg",
  lg: "xl",
  xl: "2xl",
};

const MEMBER_SIZE: Record<TeamIdentitySize, RosterAvatarSize> = {
  sm: "xs",
  md: "sm",
  lg: "md",
  xl: "lg",
};

const NAME_CLASS: Record<TeamIdentitySize, string> = {
  sm: "text-base",
  md: "text-xl",
  lg: "text-3xl",
  xl: "text-5xl",
};

const MEMBER_LABEL: Record<TeamIdentitySize, string> = {
  sm: "w-14 text-[0.65rem]",
  md: "w-16 text-xs",
  lg: "w-20 text-sm",
  xl: "w-24 text-base",
};

/** Team picture, name and the people in it. */
export function TeamIdentity({
  team,
  size = "md",
  members = "faces",
  align = "start",
  highlightUserId = null,
}: TeamIdentityProps) {
  const centered = align === "center";

  return (
    <div
      className={[
        "flex min-w-0 gap-3",
        centered ? "flex-col items-center text-center" : "items-center",
      ].join(" ")}
    >
      <RosterAvatar
        picture={team.picture}
        label={team.name}
        kind="team"
        size={TEAM_SIZE[size]}
        color={team.color}
      />

      <div
        className={[
          "min-w-0 max-w-full space-y-3",
          centered ? "flex flex-col items-center" : "",
        ].join(" ")}
      >
        <p className={["max-w-full truncate font-bold", NAME_CLASS[size]].join(" ")}>
          {team.name}
        </p>

        {members !== "none" && team.members.length > 0 && (
          <ul
            aria-label={`${team.name} members`}
            className={[
              "flex flex-wrap gap-3",
              centered ? "justify-center" : "",
            ].join(" ")}
          >
            {team.members.map((member) => {
              const isMe =
                highlightUserId !== null && member.userId === highlightUserId;
              const label = member.captain
                ? `${member.displayName} (captain)`
                : member.displayName;

              return (
                <li
                  key={member.participantId}
                  className={
                    members === "names"
                      ? [
                          "flex flex-col items-center gap-1",
                          MEMBER_LABEL[size],
                        ].join(" ")
                      : undefined
                  }
                >
                  <RosterAvatar
                    picture={member.picture}
                    label={label}
                    kind="person"
                    size={MEMBER_SIZE[size]}
                    color={team.color}
                    highlight={isMe}
                  />
                  {members === "names" && (
                    <span className="w-full truncate text-center text-muted">
                      {member.captain ? "★ " : ""}
                      {isMe ? "You" : member.displayName}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
