"use client";

import {
  useEffect,
  useId,
  useRef,
  type ReactNode,
  type RefObject,
} from "react";
import { Button } from "./Button";
import { CardDescription, CardTitle } from "./Card";

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  busy?: boolean;
  initialFocusRef?: RefObject<HTMLElement | null>;
  children: ReactNode;
}

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  busy = false,
  initialFocusRef,
  children,
}: DialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog || !open) return;

    const opener = document.activeElement;

    if (!dialog.open) dialog.showModal();
    initialFocusRef?.current?.focus();

    return () => {
      if (dialog.open) dialog.close();

      if (opener instanceof HTMLElement && opener.isConnected) {
        opener.focus();
      }
    };
  }, [open, initialFocusRef]);

  return (
    <dialog
      ref={dialogRef}
      className="ui-dialog"
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onOpenChange(false);
      }}
      onClose={(event) => {
        // Ignore stale close events if the dialog has already reopened.
        if (!event.currentTarget.open && open) {
          onOpenChange(false);
        }
      }}
    >
      <div className="ui-dialog-body">
        <header className="mb-6 flex items-start justify-between gap-4">
          <div className="min-w-0 space-y-2">
            <CardTitle id={titleId}>{title}</CardTitle>
            {description && (
              <CardDescription id={descriptionId}>
                {description}
              </CardDescription>
            )}
          </div>

          <Button
            variant="secondary"
            aria-label="Close dialog"
            disabled={busy}
            onClick={() => onOpenChange(false)}
            className="w-11 shrink-0 px-0"
          >
            <span aria-hidden="true" className="text-xl">
              ×
            </span>
          </Button>
        </header>

        {children}
      </div>
    </dialog>
  );
}