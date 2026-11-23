import { CluePanel } from "@/component/Event/Clue/CluePanel";
import { ClueReleasePanel } from "@/component/Event/Clue/ClueReleasePanel";
import { FinaleHostPanel } from "@/component/Event/Finale/FinaleHostPanel";
import { TitlePanel } from "@/component/Event/Title/TitlePanel";

export default function CluesPage() {
  return (
    <div className="space-y-6">
      <ClueReleasePanel />
      <FinaleHostPanel />
      <TitlePanel />
      <CluePanel />
    </div>
  );
}