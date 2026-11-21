"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { UserController } from "@/modules/User/user.controller";
import type { UserResponse } from "@/modules/User/user.type";
import {
  Alert,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Dialog,
  Input,
} from "@/component/ui";

interface ProfileCardProps {
  initialDisplayName: string;
  onUpdated: (profile: UserResponse) => void;
}

export function ProfileCard({
  initialDisplayName,
  onUpdated,
}: ProfileCardProps) {
  const titleId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const busy = useRef(false);

  const [open, setOpen] = useState(false);
  const [displayName, setDisplayName] = useState(initialDisplayName);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  function startEditing() {
    setDisplayName(initialDisplayName);
    setError(null);
    setSuccess(null);
    setOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;

    busy.current = true;
    setSaving(true);
    setError(null);

    try {
      const updated = await UserController.updateDisplayName(displayName);

      onUpdated(updated);
      setDisplayName(updated.displayName);
      setSuccess("Your display name has been updated.");
      setOpen(false);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not update your name.",
      );
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  return (
    <>
      <Card aria-labelledby={titleId}>
        <CardHeader>
          <CardTitle id={titleId}>Your profile</CardTitle>
          <CardDescription>
            The name other players see during your game nights.
          </CardDescription>
        </CardHeader>

        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <p className="text-sm text-muted">Display name</p>
            <p className="mt-1 break-words text-lg font-semibold">
              {initialDisplayName}
            </p>
          </div>

          <Button
            variant="secondary"
            onClick={startEditing}
            className="sm:shrink-0"
          >
            Edit name
          </Button>
        </div>

        {success && (
          <Alert tone="success" className="mt-5">
            {success}
          </Alert>
        )}
      </Card>

      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="Edit your display name"
        description="Choose a name your friends will recognize."
        busy={saving}
        initialFocusRef={inputRef}
      >
        <form onSubmit={handleSubmit} className="space-y-5">
          <fieldset disabled={saving} className="space-y-5">
            <Input
              ref={inputRef}
              label="Display name"
              name="displayName"
              required
              maxLength={80}
              autoComplete="nickname"
              hint="Up to 80 characters."
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />

            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Button
                variant="secondary"
                onClick={() => setOpen(false)}
              >
                Cancel
              </Button>

              <Button
                type="submit"
                loading={saving}
                loadingText="Saving…"
                disabled={
                  !displayName.trim() ||
                  displayName.trim() === initialDisplayName
                }
              >
                Save changes
              </Button>
            </div>
          </fieldset>

          {error && <Alert tone="error">{error}</Alert>}
        </form>
      </Dialog>
    </>
  );
}