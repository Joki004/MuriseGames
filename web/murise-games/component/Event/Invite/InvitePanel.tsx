"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  InviteController,
  type CreatedInviteLink,
} from "@/modules/Event/Invite/invite.controller";
import type { EventInviteResponse } from "@/modules/Event/Invite/invite.type";

const buttonClass =
  "rounded-lg border border-zinc-700 px-3 py-1.5 text-sm " +
  "text-zinc-200 hover:bg-zinc-800 disabled:opacity-40";

const inputClass =
  "mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-950 " +
  "px-3 py-2 text-sm text-zinc-100";

export function InvitePanel({ eventId }: { eventId: string }) {
  return <InviteManager key={eventId} eventId={eventId} />;
}

function InviteManager({ eventId }: { eventId: string }) {
  const busy = useRef(false);
  const [invites, setInvites] = useState<EventInviteResponse[]>([]);
  const [created, setCreated] = useState<CreatedInviteLink | null>(null);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copyMessage, setCopyMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const response = await InviteController.list(eventId);
        if (active) setInvites(response);
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load invitations.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, [eventId, revision]);

  function reload() {
    setLoading(true);
    setError(null);
    setRevision((value) => value + 1);
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;

    const fields = new FormData(event.currentTarget);
    const limit = String(fields.get("maxUses") ?? "").trim();

    busy.current = true;
    setSaving(true);
    setError(null);
    setCopyMessage(null);

    try {
      const result = await InviteController.create(eventId, {
        expiresInHours: Number(fields.get("expiresInHours")),
        maxUses: limit ? Number(limit) : null,
      });

      setCreated(result);
      reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not create invite.");
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  async function revoke(inviteId: string) {
    if (busy.current) return;

    busy.current = true;
    setSaving(true);
    setError(null);

    try {
      await InviteController.revoke(eventId, inviteId);

      if (created?.invite.id === inviteId) {
        setCreated(null);
        setCopyMessage(null);
      }

      reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not revoke invite.");
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }

  async function copyLink() {
    if (!created) return;

    try {
      await navigator.clipboard.writeText(created.url);
      setCopyMessage("Link copied.");
    } catch {
      setCopyMessage("Select the link and copy it manually.");
    }
  }

  return (
    <div className="mt-4 space-y-4">
      <form onSubmit={create} className="rounded-lg border border-zinc-700 p-4">
        <h4 className="font-semibold">New invite link</h4>

        <fieldset disabled={saving || loading} className="mt-3 space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm text-zinc-300">
              Expires after — hours
              <input
                name="expiresInHours"
                type="number"
                min={1}
                max={720}
                step={1}
                defaultValue={168}
                required
                className={inputClass}
              />
            </label>

            <label className="block text-sm text-zinc-300">
              Use limit — blank means unlimited
              <input
                name="maxUses"
                type="number"
                min={1}
                max={2147483647}
                step={1}
                className={inputClass}
              />
            </label>
          </div>

          <button type="submit" className={buttonClass}>
            {saving ? "Saving…" : "Create invite link"}
          </button>
        </fieldset>
      </form>

      {created && (
        <div className="rounded-lg border border-emerald-800 bg-emerald-950/30 p-4">
          <p className="text-sm text-emerald-200">
            Copy this link now. It cannot be retrieved after leaving this panel.
          </p>

          <label className="mt-3 block text-sm text-zinc-300">
            Invite link
            <input
              readOnly
              value={created.url}
              onFocus={(event) => event.currentTarget.select()}
              className={inputClass}
            />
          </label>

          <button
            type="button"
            onClick={() => void copyLink()}
            className={`${buttonClass} mt-3`}
          >
            Copy link
          </button>

          {copyMessage && (
            <p role="status" className="mt-2 text-sm text-emerald-200">
              {copyMessage}
            </p>
          )}
        </div>
      )}

      <div className="flex items-center justify-between gap-3">
        <h4 className="font-semibold">Invitations</h4>
        <button
          type="button"
          disabled={loading || saving}
          onClick={reload}
          className={buttonClass}
        >
          Refresh invites
        </button>
      </div>

      {error && (
        <p role="alert" className="text-sm text-rose-300">
          {error}
        </p>
      )}

      {loading ? (
        <p role="status" className="text-sm text-zinc-400">
          Loading invitations…
        </p>
      ) : (
        <ul className="space-y-2">
          {invites.length === 0 && !error && (
            <li className="text-sm text-zinc-400">No invitations yet.</li>
          )}

          {invites.map((invite) => (
            <li
              key={invite.id}
              className="rounded-lg border border-zinc-800 p-3"
            >
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">
                  Invite {invite.id.slice(0, 8)} · {invite.status}
                </p>

                {!invite.revokedAt && (
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => void revoke(invite.id)}
                    className={buttonClass}
                  >
                    Revoke
                  </button>
                )}
              </div>

              <p className="mt-1 text-xs text-zinc-400">
                Uses: {invite.useCount} / {invite.maxUses ?? "unlimited"}
              </p>

              <p className="mt-1 text-xs text-zinc-400">
                Expires: {new Date(invite.expiresAt).toLocaleString()}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}