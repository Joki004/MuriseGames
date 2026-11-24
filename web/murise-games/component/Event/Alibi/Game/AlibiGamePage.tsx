"use client";

import { useEventDetail } from "@/component/Event/EventShell";
import { Alert } from "@/component/ui";
import { AlibiHostPanel } from "./AlibiHostPanel";
import { AlibiJudgePanel } from "./AlibiJudgePanel";

/** Organizers run the slots and judge; assigned referees judge. */
export function AlibiGamePage() {
  const { event, canManage, roles } = useEventDetail();
  const isReferee = roles.includes("REFEREE");

  if (!canManage && !isReferee) {
    return (
      <Alert tone="info">
        Only organizers and referees run the Alibi game. Players play on the
        Player screen.
      </Alert>
    );
  }

  return (
    <div className="space-y-8">
      {canManage && <AlibiHostPanel eventId={event.id} />}
      <AlibiJudgePanel eventId={event.id} isOrganizer={canManage} />
    </div>
  );
}
