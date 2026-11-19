"use client";

import { useEffect, useState, type FormEvent } from "react";
import type { Session } from "@supabase/supabase-js";
import { getSupabase } from "@/lib/supabase";
import { getGames, type Game } from "@/lib/api";

export default function Account() {
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [games, setGames] = useState<Game[] | null>(null);

  useEffect(() => {
    const client = getSupabase();
    // The subscription emits INITIAL_SESSION after restoring browser storage.
    if (!client) {
      queueMicrotask(() => { setConfigured(false); setReady(true); });
      return;
    }
    const { data } = client.auth.onAuthStateChange((_event, current) => {
      setConfigured(true);
      setSession(current);
      setReady(true);
      if (!current) setGames(null);
    });
    return () => data.subscription.unsubscribe();
  }, []);

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const client = getSupabase();
    if (!client) return;
    const form = new FormData(event.currentTarget);
    const credentials = { email: String(form.get("email")), password: String(form.get("password")) };
    setBusy(true);
    setMessage("");
    try {
      const result = mode === "login"
        ? await client.auth.signInWithPassword(credentials)
        : await client.auth.signUp({ ...credentials, options: { emailRedirectTo: `${window.location.origin}/account` } });
      if (result.error) throw result.error;
      setMessage(mode === "signup" && !result.data.session
        ? "Check your email to confirm your account, then return here to sign in."
        : "You’re signed in.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "We couldn’t sign you in. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function signOut() {
    setBusy(true);
    try {
      const result = await getSupabase()?.auth.signOut();
      if (result?.error) throw result.error;
      setGames(null);
      setMessage("You’re signed out.");
    } catch {
      setMessage("We couldn’t sign you out. Please try again.");
    } finally { setBusy(false); }
  }

  async function loadGames() {
    setBusy(true);
    setMessage("");
    try { setGames(await getGames()); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not load games."); }
    finally { setBusy(false); }
  }

  return (
    <section className="account">
      <p className="eyebrow">YOUR NEXT GREAT NIGHT STARTS HERE</p>
      <h1>{session ? "You’re in." : mode === "login" ? "Welcome back." : "Join the story."}</h1>
      {!ready ? <p role="status">Loading your account…</p> : !configured ? (
        <div className="panel"><h2>Opening soon</h2><p>Sign-up is being prepared. Please check back shortly.</p></div>
      ) : session ? (
        <div className="panel">
          <p>Signed in as <strong>{session.user.email}</strong></p>
          <p>Browse the starting catalogue. Event creation and scoring are still in development.</p>
          <div className="actions"><button disabled={busy} onClick={loadGames}>View games</button><button className="secondary" disabled={busy} onClick={signOut}>Sign out</button></div>
          {games && <ul className="game-list">{games.map(game => <li key={game.code}><strong>{game.name}</strong><span>{game.rulesStatus === "APPROVED" ? "Rules approved" : "Rules being prepared"}</span></li>)}</ul>}
        </div>
      ) : (
        <div className="panel">
          <form onSubmit={authenticate}>
            <label htmlFor="email">Email</label><input id="email" name="email" type="email" autoComplete="email" required />
            <label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} minLength={mode === "signup" ? 8 : undefined} required />
            <button disabled={busy} type="submit">{busy ? "One moment…" : mode === "login" ? "Sign in" : "Create account"}</button>
          </form>
          <button className="text-button" disabled={busy} onClick={() => { setMode(mode === "login" ? "signup" : "login"); setMessage(""); }}>{mode === "login" ? "New here? Create an account" : "Already have an account? Sign in"}</button>
        </div>
      )}
      <p role="status" aria-live="polite">{message}</p>
    </section>
  );
}
