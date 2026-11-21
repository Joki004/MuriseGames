"use client";

import type { ComponentProps } from "react";
import { cx } from "./cx";

interface ButtonProps extends ComponentProps<"button"> {
  variant?: "primary" | "secondary" | "danger";
  loading?: boolean;
  loadingText?: string;
  fullWidth?: boolean;
}

export function Button({
  variant = "primary",
  loading = false,
  loadingText = "Working…",
  fullWidth = false,
  type = "button",
  disabled,
  className,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || props["aria-busy"]}
      className={cx(
        "ui-button",
        `ui-button--${variant}`,
        fullWidth && "w-full",
        className,
      )}
    >
      {loading && <span className="ui-spinner" aria-hidden="true" />}
      {loading ? loadingText : children}
    </button>
  );
}