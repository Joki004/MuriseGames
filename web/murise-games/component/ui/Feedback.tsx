import type { ReactNode } from "react";
import { cx } from "./cx";

interface AlertProps {
  tone?: "success" | "error" | "info";
  title?: string;
  children: ReactNode;
  className?: string;
}

export function Alert({
  tone = "info",
  title,
  children,
  className,
}: AlertProps) {
  const icon = tone === "success" ? "✓" : tone === "error" ? "!" : "i";

  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      aria-atomic="true"
      className={cx("ui-alert", `ui-alert--${tone}`, className)}
    >
      <span aria-hidden="true" className="font-bold">
        {icon}
      </span>

      <div className="min-w-0">
        {title && <p className="mb-1 font-semibold">{title}</p>}
        <div>{children}</div>
      </div>
    </div>
  );
}

export function LoadingState({
  label = "Loading…",
}: {
  label?: string;
}) {
  return (
    <div role="status" className="ui-loading">
      <span className="ui-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({
  title,
  description,
  icon = "🎲",
  action,
}: EmptyStateProps) {
  return (
    <div className="ui-empty">
      <span aria-hidden="true" className="text-3xl">
        {icon}
      </span>
      <h3 className="font-semibold text-foreground">{title}</h3>
      {description && <p className="ui-description">{description}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
}