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
    <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur">
      <nav className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <div className="flex min-w-0 max-w-full flex-wrap items-center gap-x-6 gap-y-2">
          <Link
            href={session ? "/dashboard" : "/login"}
            className="text-sm font-bold tracking-wider text-highlight"
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
                        ? "bg-surface-raised font-medium text-foreground"
                        : "text-muted hover:bg-surface-raised hover:text-foreground"
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
              <span className="hidden text-xs text-muted sm:inline">
                {account?.displayName ?? session.user.email}
              </span>
              <button
                type="button"
                onClick={() => void handleSignOut()}
                className="ui-button ui-button--secondary"
              >
                Sign Out
              </button>
            </>
          ) : (
            <Link href="/login" className="ui-button ui-button--primary">
              Sign In
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
