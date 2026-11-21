"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/component/Auth/AuthProvider";

const NAV_LINKS = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/join", label: "Join Event" },
];

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const { session, account, signOut } = useAuth();

  async function handleSignOut() {
    await signOut();
    router.replace("/login");
  }

  return (
    <header className="sticky top-0 z-40 border-b border-zinc-800 bg-zinc-950/90 backdrop-blur">
      <nav className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
        <div className="flex items-center gap-6">
          <Link
            href={session ? "/dashboard" : "/login"}
            className="text-sm font-bold tracking-wider text-emerald-400"
          >
            MURISEGAMES
          </Link>

          {session && (
            <div className="flex items-center gap-2">
              {NAV_LINKS.map((link) => {
                const active = pathname === link.href;
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`rounded-md px-2.5 py-1 text-sm transition ${
                      active
                        ? "bg-zinc-800 font-medium text-zinc-100"
                        : "text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200"
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </div>
          )}
        </div>

        <div className="flex items-center gap-3">
          {session ? (
            <>
              <span className="hidden text-xs text-zinc-400 sm:inline">
                {account?.displayName ?? session.user.email}
              </span>
              <button
                type="button"
                onClick={() => void handleSignOut()}
                className="rounded-md border border-zinc-700 px-2.5 py-1 text-xs text-zinc-200 hover:bg-zinc-800"
              >
                Sign Out
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-md bg-emerald-600 px-3 py-1 text-xs font-medium text-white hover:bg-emerald-500"
            >
              Sign In
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}