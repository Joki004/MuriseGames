"use client";

import { useState, type FormEvent } from "react";
import { UserController } from "@/modules/User/user.controller";
import type { UserResponse } from "@/modules/User/user.type";

interface ProfileCardProps {
  initialDisplayName: string;
  onUpdated: (profile: UserResponse) => void;
}

export function ProfileCard({ initialDisplayName, onUpdated }: ProfileCardProps) {
  const [displayName, setDisplayName] = useState(initialDisplayName);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setFeedback(null);

    try {
      const updated = await UserController.updateDisplayName(displayName);
      setDisplayName(updated.displayName);
      onUpdated(updated);
      setFeedback({
        type: "success",
        text: "Display name saved to murise.profiles.",
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text:
          err instanceof Error
            ? err.message
            : "Failed to update display name.",
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-xl border border-zinc-800 bg-zinc-900/70 p-6">
      <h2 className="text-lg font-semibold text-zinc-100">
        Profile Settings{" "}
        <span className="text-xs font-normal text-zinc-400">
          PATCH /api/users/me
        </span>
      </h2>

      <form onSubmit={handleSubmit} className="mt-4 space-y-4">
        <div>
          <label
            htmlFor="displayName"
            className="block text-sm font-medium text-zinc-300"
          >
            Display Name
          </label>
          <input
            id="displayName"
            type="text"
            maxLength={80}
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-emerald-500"
          />
        </div>

        <button
          type="submit"
          disabled={saving}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
        >
          {saving ? "Saving..." : "Update Display Name"}
        </button>

        {feedback && (
          <p
            className={`text-sm ${
              feedback.type === "success" ? "text-emerald-400" : "text-rose-400"
            }`}
          >
            {feedback.text}
          </p>
        )}
      </form>
    </section>
  );
}