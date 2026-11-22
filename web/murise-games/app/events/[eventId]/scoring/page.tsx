import { ScoringPlanPanel } from
  "@/component/Event/Scoring/ScoringPlanPanel";
import { ScorePreviewPanel } from
  "@/component/Event/Scoring/ScorePreviewPanel";
import { RankingStatusPanel } from "@/component/Event/Scoring/RankingStatusPanel";

export default function ScoringPage() {
  return (
    <div className="space-y-6">
      <ScoringPlanPanel />
<RankingStatusPanel />
      <details className="rounded-card border border-border bg-surface p-5">
        <summary className="cursor-pointer font-semibold">
          Try a score calculation
        </summary>
        <div className="mt-5">
          <ScorePreviewPanel />
        </div>
      </details>
    </div>
  );
}