import { getSupabase } from "./supabase";

export type Game = { code: string; name: string; rulesStatus: string };

export async function getGames(): Promise<Game[]> {
  const client = getSupabase();
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!client || !baseUrl) throw new Error("The app connection has not been configured yet.");
  const { data, error } = await client.auth.getSession();
  if (error || !data.session) throw new Error("Please sign in to view the games.");
  const response = await fetch(`${baseUrl.replace(/\/$/, "")}/api/games`, {
    headers: { Authorization: `Bearer ${data.session.access_token}` },
    cache: "no-store",
    signal: AbortSignal.timeout(10000),
  });
  if (response.status === 401) throw new Error("Your session has expired. Please sign in again.");
  if (!response.ok) throw new Error("The games could not be loaded. Please try again.");
  return response.json() as Promise<Game[]>;
}
