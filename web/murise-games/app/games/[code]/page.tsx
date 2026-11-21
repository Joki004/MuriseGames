import { Suspense } from "react";
import { CatalogGameView } from "@/component/Games/CatalogView";
import { LoadingState } from "@/component/ui";

export default function GamePage() {
  return (
    <Suspense fallback={<LoadingState label="Opening game…" />}>
      <CatalogGameView />
    </Suspense>
  );
}