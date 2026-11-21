import { Suspense, type ReactNode } from "react";
import { EventShell } from "@/component/Event/EventShell";
import { LoadingState } from "@/component/ui";

export default function EventLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <Suspense fallback={<LoadingState label="Opening event…" />}>
      <EventShell>{children}</EventShell>
    </Suspense>
  );
}