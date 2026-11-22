import { CluePanel } from "@/component/Event/Clue/CluePanel";
import { TitlePanel } from "@/component/Event/Title/TitlePanel";

export default function CluesPage() {
  return (
    <div className="space-y-6">
      <TitlePanel />
      <CluePanel />
    </div>
  );
}