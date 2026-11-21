"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, useRef, useState } from "react";
import { useAuth } from "@/component/Auth/AuthProvider";
import { Alert, Button } from "@/component/ui";

const LINKS = [
  { href: "/dashboard", label: "My events" },
  { href: "/games", label: "Games" },
  { href: "/join", label: "Join event" },
  { href: "/account", label: "Account" },
];

function isActive(pathname: string, href: string) {
  if (href === "/dashboard") {
    return pathname === href || pathname.startsWith("/events/");
  }

  return pathname === href || pathname.startsWith(`${href}/`);
}

export function Navbar() {
  return (
    <Suspense
      fallback={
        <header className="h-[73px] border-b border-border bg-background" />
      }
    >
      <RouteNavbar />
    </Suspense>
  );
}

function RouteNavbar() {
  const pathname = usePathname();

  // A route change closes the mobile menu by resetting its local state.
  return <Navigation key={pathname} pathname={pathname} />;
}

function Navigation({ pathname }: { pathname: string }) {
  const router = useRouter();
  const { session, loading, signOut } = useAuth();
  const menuButton = useRef<HTMLButtonElement>(null);
  const busy = useRef(false);

  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSignOut() {
    if (busy.current) return;

    busy.current = true;
    setSigningOut(true);
    setError(null);

    try {
      await signOut();
      router.replace("/login");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not sign out.");
    } finally {
      busy.current = false;
      setSigningOut(false);
    }
  }

  function links(mobile = false) {
    return LINKS.map(({ href, label }) => {
      const active = isActive(pathname, href);

      return (
        <Link
          key={href}
          href={href}
          aria-current={active ? "page" : undefined}
          onClick={() => setOpen(false)}
          className={[
            "rounded-control px-3 py-2.5 text-sm font-medium transition-colors",
            mobile ? "block" : "",
            active
              ? "bg-surface-raised text-highlight"
              : "text-muted hover:bg-surface hover:text-foreground",
          ].join(" ")}
        >
          {label}
        </Link>
      );
    });
  }

  return (
    <header
      className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur"
      onKeyDown={(event) => {
        if (event.key === "Escape" && open) {
          setOpen(false);
          menuButton.current?.focus();
        }
      }}
    >
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-primary focus:px-4 focus:py-2 focus:text-white"
      >
        Skip to content
      </a>

      <div className="mx-auto flex min-h-[72px] max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Link
          href={session ? "/dashboard" : "/login"}
          className="shrink-0 text-sm font-bold tracking-[0.14em] text-highlight"
        >
          MURISE<span className="text-accent">GAMES</span>
        </Link>

        {!loading && session && (
          <>
            <nav
              aria-label="Main navigation"
              className="hidden items-center gap-1 md:flex"
            >
              {links()}
            </nav>

            <div className="hidden md:block">
              <Button
                variant="secondary"
                loading={signingOut}
                loadingText="Signing out…"
                onClick={() => void handleSignOut()}
              >
                Sign out
              </Button>
            </div>

            <Button
              ref={menuButton}
              variant="secondary"
              className="md:hidden"
              aria-expanded={open}
              aria-controls="mobile-navigation"
              onClick={() => setOpen((value) => !value)}
            >
              {open ? "Close" : "Menu"}
            </Button>
          </>
        )}

        {!loading && !session && (
          <Link href="/login" className="ui-button ui-button--primary">
            Sign in
          </Link>
        )}
      </div>

      {session && (
        <nav
          id="mobile-navigation"
          aria-label="Mobile navigation"
          className={
            open
              ? "space-y-1 border-t border-border px-4 py-3 md:hidden"
              : "hidden"
          }
        >
          {links(true)}

          <Button
            variant="secondary"
            fullWidth
            className="mt-3"
            loading={signingOut}
            loadingText="Signing out…"
            onClick={() => void handleSignOut()}
          >
            Sign out
          </Button>
        </nav>
      )}

      {error && (
        <div className="mx-auto max-w-6xl px-4 pb-3">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
    </header>
  );
}