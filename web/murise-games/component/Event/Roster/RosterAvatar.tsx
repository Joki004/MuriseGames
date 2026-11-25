"use client";

import Image from "next/image";
import { useState } from "react";
import type { RosterPicture } from "@/modules/Event/Roster";

export type RosterAvatarSize = "xs" | "sm" | "md" | "lg" | "xl" | "2xl";

const BOX: Record<RosterAvatarSize, string> = {
  xs: "h-6 w-6 text-[0.6rem]",
  sm: "h-8 w-8 text-xs",
  md: "h-12 w-12 text-base",
  lg: "h-16 w-16 text-2xl",
  xl: "h-24 w-24 text-4xl",
  "2xl": "h-36 w-36 text-6xl",
};

const PIXELS: Record<RosterAvatarSize, number> = {
  xs: 24,
  sm: 32,
  md: 48,
  lg: 64,
  xl: 96,
  "2xl": 144,
};

/** First letter of the first two words, ignoring brackets and symbols ("Wija (Bob)" → "WB"). */
function initialsOf(label: string, max: number) {
  return label
    .split(/\s+/)
    .map((part) => part.match(/[\p{L}\p{N}]/u)?.[0] ?? "")
    .filter(Boolean)
    .slice(0, max)
    .join("")
    .toUpperCase();
}

interface RosterAvatarProps {
  picture: RosterPicture | null | undefined;
  label: string;
  /** People are round, teams are rounded squares. */
  kind: "team" | "person";
  size?: RosterAvatarSize;
  /** Border color, usually the team color. */
  color?: string;
  /** Draw attention, e.g. "this is you". */
  highlight?: boolean;
  className?: string;
}

/** Read-only picture: uploaded image, else emoji, else initials. */
export function RosterAvatar({
  picture,
  label,
  kind,
  size = "md",
  color,
  highlight = false,
  className,
}: RosterAvatarProps) {
  // Remember which URL failed, so a new URL is tried again without an effect.
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const url = picture?.imageUrl ?? null;
  const imageSrc = url !== null && failedUrl !== url ? url : null;
  // Small circles only have room for one letter.
  const letters = initialsOf(label, size === "xs" || size === "sm" ? 1 : 2);
  const fallback = picture?.emoji || letters || (kind === "team" ? "🎲" : "?");

  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      className={[
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden",
        "border-2 bg-surface-raised font-semibold text-foreground",
        kind === "person"
          ? "rounded-full"
          : size === "lg" || size === "xl" || size === "2xl"
            ? "rounded-card"
            : "rounded-control",
        highlight ? "ring-2 ring-accent ring-offset-2 ring-offset-background" : "",
        BOX[size],
        className ?? "",
      ].join(" ")}
      style={{ borderColor: color ?? "var(--border)" }}
    >
      {imageSrc ? (
        <Image
          src={imageSrc}
          alt=""
          fill
          unoptimized
          sizes={`${PIXELS[size]}px`}
          className="object-cover"
          onError={() => setFailedUrl(imageSrc)}
        />
      ) : (
        <span aria-hidden="true">{fallback}</span>
      )}
    </span>
  );
}
