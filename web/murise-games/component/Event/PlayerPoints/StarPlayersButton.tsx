"use client";

import { useState } from "react";
import { Button, Dialog } from "@/component/ui";
import { StarPlayersEditor } from "./StarPlayersEditor";

interface StarPlayersButtonProps {
  eventId: string;
  fixtureId: string;
  title: string;
}

/** Opens the star-player picker for one match. */
export function StarPlayersButton({ eventId, fixtureId, title }: StarPlayersButtonProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>
        ★ Players
      </Button>
      <Dialog open={open} onOpenChange={setOpen} title={title} description="Star players">
        <div className="mt-4">
          {open && (
            <StarPlayersEditor
              eventId={eventId}
              fixtureId={fixtureId}
              onClose={() => setOpen(false)}
            />
          )}
        </div>
      </Dialog>
    </>
  );
}
