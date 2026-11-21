import type { ReactNode } from "react";

interface PageShellProps {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
}

export function PageShell({
  title,
  description,
  action,
  children,
}: PageShellProps) {
  return (
    <main
      id="main-content"
      className="mx-auto w-full max-w-6xl space-y-8 px-4 py-8 sm:px-6 sm:py-10"
    >
      <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-3xl font-semibold tracking-tight text-foreground">
            {title}
          </h1>

          {description && (
            <p className="mt-2 max-w-2xl text-muted">{description}</p>
          )}
        </div>

        {action && <div className="shrink-0">{action}</div>}
      </header>

      {children}
    </main>
  );
}