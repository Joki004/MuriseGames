# V1 delivery boundary

Event: **28 November 2026**, rehearsal target **23 November 2026** (Europe/Warsaw).
The skeleton establishes a buildable connection between the frontend, authentication and the catalogue.
It does not complete the Trello event features. No task is being marked complete automatically.

## Next implementation order

1. Configure dev Supabase, verify the full signup/login/API flow, and add password recovery.
2. Add profiles, events and memberships; enforce global admin vs event organiser permissions.
3. Add teams, manual participant assignment and event game selection. Decide the minimal join/invite flow.
4. Approve the nine games' exact rules from the master book before implementing scoring handlers.
5. Build the balanced station schedule with a feasibility check, manual adjustment and publish/freeze.
6. Add organiser score entry, audit trail, correction flow and shared leaderboard polling.
7. Deploy and rehearse with actual phones, TV and rooms. Test recovery from lost network and duplicate submits.

## User stories for the feature work

- As a player, I can create an account, sign in, recover access and see only the event data I may access.
- As an organiser, I can create a themed event, select catalogue games, set teams and add participants.
- As an organiser, I can assign another organiser within my event without granting global admin access.
- As a player, I can see my team, rules, next activity and the latest published leaderboard.
- As an organiser, I can generate and review a rotation that avoids repeated games and balances opponents;
  if exact constraints are impossible, I see what must change before publishing.
- As an organiser, I can record game-specific results and correct mistakes with an audit trail.
- As a platform admin, I can perform explicitly authorized cross-event actions; this is a separate role.

For the event MVP, round robin, elimination tournaments, catalogue editing UI and richer invitation
automation can follow the initial release. An old 7 December date is not an event-readiness requirement.

## Game acceptance checklist

Each game needs approved: participation mode, duration, inputs, validation, points, ties, penalties,
timeouts, forfeit handling, host/player visibility and an example score calculation. The current
catalogue deliberately reports `PENDING_REVIEW`; it must not be used as an approved rulebook.

Existing board: https://trello.com/b/TBXtbRf6/for-the-plot-game-night-app
