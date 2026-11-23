import { CluePanel } from "@/component/Event/Clue/CluePanel";
import { ClueReleasePanel } from "@/component/Event/Clue/ClueReleasePanel";
import { TitlePanel } from "@/component/Event/Title/TitlePanel";

export default function CluesPage() {
  return (
    <div className="space-y-6">
      <ClueReleasePanel />
      <TitlePanel />
      <CluePanel />
    </div>
  );
}