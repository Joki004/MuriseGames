"use client";

import { useId, type ComponentProps, type ReactNode } from "react";
import { cx } from "./cx";

interface FieldMeta {
  label: string;
  hint?: string;
  error?: string;
}

interface FieldShellProps extends FieldMeta {
  id: string;
  required?: boolean;
  children: ReactNode;
}

function FieldShell({
  id,
  label,
  hint,
  error,
  required,
  children,
}: FieldShellProps) {
  return (
    <div className="ui-field">
      <label htmlFor={id} className="ui-label">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-1 text-highlight">
            *
          </span>
        )}
      </label>

      {children}

      {hint && (
        <p id={`${id}-hint`} className="ui-hint">
          {hint}
        </p>
      )}

      {error && (
        <p id={`${id}-error`} className="ui-field-error">
          {error}
        </p>
      )}
    </div>
  );
}

function useFieldAttributes(
  suppliedId: string | undefined,
  hint: string | undefined,
  error: string | undefined,
  describedBy: string | undefined,
) {
  const generatedId = useId();
  const id = suppliedId ?? generatedId;

  return {
    id,
    "aria-describedby":
      [
        describedBy,
        hint ? `${id}-hint` : undefined,
        error ? `${id}-error` : undefined,
      ]
        .filter(Boolean)
        .join(" ") || undefined,
  };
}

export function Input({
  label,
  hint,
  error,
  id,
  required,
  className,
  ...props
}: ComponentProps<"input"> & FieldMeta) {
  const attributes = useFieldAttributes(
    id,
    hint,
    error,
    props["aria-describedby"],
  );

  return (
    <FieldShell
      id={attributes.id}
      label={label}
      hint={hint}
      error={error}
      required={required}
    >
      <input
        {...props}
        {...attributes}
        required={required}
        aria-invalid={error ? true : props["aria-invalid"]}
        className={cx("ui-control", className)}
      />
    </FieldShell>
  );
}

export function Select({
  label,
  hint,
  error,
  id,
  required,
  className,
  children,
  ...props
}: ComponentProps<"select"> & FieldMeta) {
  const attributes = useFieldAttributes(
    id,
    hint,
    error,
    props["aria-describedby"],
  );

  return (
    <FieldShell
      id={attributes.id}
      label={label}
      hint={hint}
      error={error}
      required={required}
    >
      <select
        {...props}
        {...attributes}
        required={required}
        aria-invalid={error ? true : props["aria-invalid"]}
        className={cx("ui-control", className)}
      >
        {children}
      </select>
    </FieldShell>
  );
}

export function Textarea({
  label,
  hint,
  error,
  id,
  required,
  className,
  rows = 4,
  ...props
}: ComponentProps<"textarea"> & FieldMeta) {
  const attributes = useFieldAttributes(
    id,
    hint,
    error,
    props["aria-describedby"],
  );

  return (
    <FieldShell
      id={attributes.id}
      label={label}
      hint={hint}
      error={error}
      required={required}
    >
      <textarea
        {...props}
        {...attributes}
        rows={rows}
        required={required}
        aria-invalid={error ? true : props["aria-invalid"]}
        className={cx("ui-control", className)}
      />
    </FieldShell>
  );
}