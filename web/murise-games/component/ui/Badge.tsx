import type { ComponentProps } from "react";
import { cx } from "./cx";

interface BadgeProps extends ComponentProps<"span"> {
  tone?: "neutral" | "success" | "warning" | "danger" | "accent";
}

export function Badge({
  tone = "neutral",
  className,
  ...props
}: BadgeProps) {
  return (
    <span
      {...props}
      className={cx("ui-badge", `ui-badge--${tone}`, className)}
    />
  );
}