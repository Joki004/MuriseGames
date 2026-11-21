-- V8__publish_detailed_game_rules_v2.sql
-- Publishes v2 of the 9 FOR THE PLOT master-book games with full, structured player-facing rules.
-- Respects murise.protect_game_version (v1 remains immutable for any event already pinned to it).

WITH v2_content (code, summary, rules_markdown, end_game_scoring_summary) AS (
    VALUES
        (
            'grab-the-cup',
            'Head-to-head reflex duels: follow body-part commands and grab the cup only when the host calls CUP.',
            $md$
## Objective
Out-react your opponent in a series of quick reflex duels by grabbing the cup only on the exact **CUP** cue.

> **Host briefing:** "Touch the body part I call. Only on **CUP** may you grab. First to two wins takes the duel."

## Players & Arrangement
- **Format:** Head-to-head (two teams face off).
- **Lineup:** 4 player duels per pairing, using 4 different teammates (if team sizes are unequal, rotate repeats fairly).

## Materials & Setup
- One upright plastic cup placed at equal reach between the two active players on a stable table.
- Both players start with hands ready and eyes on the host.

## How to Play
1. The host calls out body parts (for example: *head, shoulders, knees*). Both players immediately touch the called body part.
2. Players may **only** reach for the cup when the host calls **CUP**.
3. The first player to lift and control the cup on **CUP** wins the attempt.
4. Reaching early (a false start) immediately loses that attempt.
5. **First to 2 valid attempts** wins the duel. Reset hands and place the cup back in the centre after every attempt.
6. Rotate in the next pair of players until all 4 duels are complete.

## Tie-breakers & Important Exceptions
- **Disputed grabs:** Simultaneous control, a dropped cup, or moved furniture stops the attempt—release and replay cleanly (no wrestling).
- **Accessibility:** Seated players use *head, shoulders, and elbows*.
- **Ties:** Tied team scores stand.
$md$,
            $md$
- **Raw points:** **1 point** per duel won (4 duels per pairing).
- **Leaderboard score:** Normalized to **5 points** (`5 × wins / max_duels`).
- **Example:** Winning 3 of 4 duels scores `3.75` (displayed as **3.8 pts**).
$md$
        ),
        (
            'sensory-chain',
            'Four 45-second team rotations: pass a secret action from Mime to Sound FX to Guesser with 1 pass per turn.',
            $md$
## Objective
Relay a secret action card through **movement**, then **sound effects**, so your teammate can guess it before the 45-second timer expires.

> **Host briefing:** "Send an action through movement, then noises, then a guess. Rotate roles after each turn."

## Players & Arrangement
- **Format:** Team solo (one team plays at a time to prevent sound interference).
- **Lineup:** Teams of 4 play **4 turns of 45 seconds**.
- **Roles (rotate every turn):**
  1. **Mime** — sees the action card and acts it out silently.
  2. **Sound FX** — watches the Mime and produces sound effects only.
  3. **Guesser** — faces away from the Mime and guesses the action from the sounds.
  4. **Card / Time Helper** — manages the card stack without giving hints.

## Materials & Setup
- 12 action cards per team (4 triplets of 3 cards), a 45-second timer, and optional blindfold or face-away seating.

## How to Play
1. Start the 45-second timer and reveal the first card in the turn's triplet to the **Mime** only.
2. The **Mime** acts silently; the **Sound FX** player watches and makes noises only (**no words, mouthing, spelling, lyrics, or extra hints**).
3. The **Guesser** calls out guesses. Exact wording is not required as long as the meaning matches (e.g., *vacuuming* / *hoovering*).
4. Once correct, immediately move to the next card in the triplet.
5. Teams may use **1 pass per turn** to skip a difficult card.
6. After 45 seconds, rotate all four roles and use a fresh triplet for the next turn.

## Tie-breakers & Important Exceptions
- **Rule violations:** Any spoken clue, peeking, or help from the 4th player voids that card (**0 points**); play continues with the next card.
- **Unfinished or passed cards:** Score **0 points**.
- **Ties:** Tied team scores stand.
$md$,
            $md$
- **Raw points:** **1 point** per correct action card (maximum **12** across 4 turns).
- **Leaderboard score:** Normalized to **5 points** (`5 × correct / 12`).
- **Example:** 9 correct cards out of 12 scores `3.75` (displayed as **3.8 pts**).
$md$
        ),
        (
            'unified-alibi',
            'Paired teammates build a shared alibi in 90 seconds, then answer 10 questions separately to match details.',
            $md$
## Objective
Agree on a convincing shared story with your partner, then answer 10 interrogation questions separately so your invented details match without contradicting the fixed facts.

> **Host briefing:** "Agree your story in 90 seconds, then answer separately. Fixed facts must be correct and your invented details must match."

## Players & Arrangement
- **Format:** Team solo.
- **Lineup:** Two pairs per team, playing **2 rounds** (10 questions per pair). The second pair receives a completely fresh case.

## Materials & Setup
- A case brief containing fixed facts, 10 ordered interrogation questions, and separate interview spaces (or the approved written fallback).

## How to Play
1. **Preparation (90 seconds):** Both partners read the case brief together. Memorize the fixed facts and invent consistent details to cover likely follow-up questions.
2. **Separation:** Partners are separated with **no communication** allowed.
3. **Interrogation:** Both partners receive the exact same 10 questions. Both answers are locked before any comparison is shown.
4. **Comparison:** The judge compares each pair of locked answers for semantic matches.

## Tie-breakers & Important Exceptions
- **Semantic matching:** Different wording counts as a match if the meaning is the same.
- **Zero-point answers:** Blank answers, missing answers, or contradictions of the case's fixed facts score **0**. Two blanks never count as a match.
- **Ties:** Tied team scores stand.
- **Awaiting review:** Final event format between separated-room judge recording vs. simultaneous 15-second written answers.
$md$,
            $md$
- **Raw points:** **1 point** per matching answer (10 per pair, maximum **20** per team).
- **Leaderboard score:** Normalized to **5 points** (`raw / 4`).
- **Example:** Pair 1 gets 7 + Pair 2 gets 9 = 16/20 → **4.0 pts**.
$md$
        ),
        (
            'social-scenario',
            'Head-to-head roleplay: Defender completes 3 secret goals while Attacker tries to trigger 2 secret red flags.',
            $md$
## Objective
Navigate a tense everyday conversation: the **Defender** tries to complete 3 secret goals cleanly, while the **Attacker** tries to bait the Defender into triggering 1 of 2 secret red flags.

> **Host briefing:** "Complete your three goals while your opponent tries to trigger a secret red flag. Then swap roles."

## Players & Arrangement
- **Format:** Head-to-head.
- **Structure:** Two full rounds (each round has two legs so both teams attack and defend with fresh players), plus an **optional 3rd round** if the lead after Round 2 is **6 points or less** (a lead of 7+ skips Round 3).

## Materials & Setup
- A fresh everyday scenario for every turn, private Defender goal cards, private Attacker red-flag cards, and an assigned judge.

## How to Play
1. **Preparation (30 seconds):** Both players read the public scenario and their private role cards.
2. **Live conversation:** Roleplay the scene naturally.
3. The judge tracks completed Defender goals and watches for confirmed Attacker red flags.
4. **Turn ends immediately when:**
   - The Defender completes all **3 goals**, OR
   - The judge confirms a **red flag**, OR
   - The turn timer expires.
5. Swap Attacker/Defender roles on a **fresh scenario** for the next leg.

## Tie-breakers & Important Exceptions
- **Replays:** If a scenario is accidentally tainted, replay using a fresh reserve scenario.
- **Awaiting review:** Turn timer (**60s** vs. **180s**), simultaneous goal/red-flag precedence, timeout survival points, and final leaderboard denominator (**12** vs. **18**).
$md$,
            $md$
- **Defender:** **+1** per completed goal, plus **+1** for a clean finish.
- **Attacker:** **+2** for a confirmed red flag.
- **Leaderboard score:** Normalized to **5 points** *(final denominator awaiting review)*.
$md$
        ),
        (
            'poison-game',
            'Alternating cup picks against a secret poison marker known only to the gamemaster.',
            $md$
## Objective
Survive the table by choosing safe cups and forcing the opposing team to pick the single secretly poisoned cup.

> **Host briefing:** "Choose a remaining cup within 5 seconds. Poison loses the round. Then reset and swap who starts."

## Players & Arrangement
- **Format:** Head-to-head, **2 rounds** per match.
- **Rotation:** Teams alternate turns, and a **different teammate** must choose each turn.

## Materials & Setup
- **6 identical cups** per station (all drinks are ordinary; pointing/choosing counts without needing to drink).
- Only the **gamemaster** knows which cup holds the secret poison marker.

## How to Play
1. The starting team selects one remaining cup within **5 seconds**.
2. If the cup is **safe**, it is removed from the table and play passes to the opposing team.
3. If the cup is **poison**, the round ends immediately and the **opposing team wins the round**.
4. **Round 2:** Reset all 6 cups with a brand-new secret poison marker and swap which team goes first.

## Tie-breakers & Important Exceptions
- **Ties:** A 1–1 split stands (no sudden-death round required).
- **Awaiting review:** Confirmed default cup count (6) and host ruling for a 5-second timeout or accidental marker exposure.
$md$,
            $md$
- **Raw points:** **1 point** per round won (match ends **2–0** or **1–1**).
- **Leaderboard score:** Normalized to **5 points** (`5 × rounds_won / rounds_played`).
- **Example:** 1 win in 2 rounds scores **2.5 pts**; 3 wins in 4 rounds scores `3.75` (**3.8 pts**).
$md$
        ),
        (
            'rhythm-grid-hunt',
            '1 Hunter vs 3 Runners on a 3x3 grid across 12 movement cues; landing on the same square catches.',
            $md$
## Objective
- **As Hunter:** Anticipate movement and land on the same square as the Runners to catch them.
- **As Runners:** Evade the Hunter and survive all 12 movement cues.

> **Host briefing:** "Feint before the cue, move once on it. Matching the hunter's landing square means caught."

## Players & Arrangement
- **Format:** Head-to-head, teams of 4.
- **Structure:** **4 rounds** (2 turns per round, swapping Hunter and Runners). Each teammate hunts once and sits out one running turn.

## Materials & Setup
- A 3×3 grid, regular audio or host movement cues, and a judge.
- **Starting positions (identical every turn):** Hunter starts in the **centre**; 3 Runners start in **three corners**.

## How to Play
1. Each turn consists of **12 movement cues**.
2. Before a cue, players may feint. **On the cue**, each player moves **once** to an orthogonally adjacent square (up, down, left, right) or stays put. **No diagonals.**
3. **Catches:** Landing on the **exact same square** as the Hunter catches a Runner (crossing paths does not count). Caught Runners step off for the rest of the turn.
4. The turn ends after **12 cues** or once all **3 Runners** are caught.
5. Swap roles and reset starting positions for the second turn of the round.

## Tie-breakers & Important Exceptions
- **Safety:** No grabbing or pushing; stop immediately if tape or floor surface becomes unsafe.
- **Disputed cues:** Restore the last agreed positions and replay the cue.
- **Ties:** Tied scores stand.
- **Awaiting review:** Physical layout (one shared 3×3 grid vs. separate numbered grids) and derived **24-point** raw maximum.
$md$,
            $md$
- **Hunter team:** **1 raw point** per catch (`0–3` per turn).
- **Runner team:** **1 raw point** per surviving runner (`3 − catches` per turn).
- **Leaderboard score:** Normalized to **5 points** (`5 × raw / 24`, *24-point denominator awaiting review*).
$md$
        ),
        (
            'group-chat-meltdown',
            'Communicate a secret everyday situation to your teammates using only 2-4 memes, stickers, or GIFs.',
            $md$
## Objective
Convey a secret everyday situation to your teammates using only **2 to 4 memes, stickers, or GIFs**—with zero speaking or gestures.

> **Host briefing:** "Show 2–4 images that describe your secret situation. Your teammates have 45 seconds to guess."

## Players & Arrangement
- **Format:** Team solo (teams of 4).
- **Structure:** **4 rounds**, rotating a different **Creator** each round so everyone creates once.

## Materials & Setup
- Private situation prompts from the host and a phone/device to select images.

## How to Play
1. The host privately gives the **Creator** a secret situation (e.g., *waving back to someone who was actually greeting the person behind you*).
2. **Selection (60 seconds):** The Creator selects and arranges **2–4 memes, stickers, or GIFs** from their phone or the internet.
   - Existing meme text is allowed.
   - **No** added captions, visible search bars/terms, or images that literally spell out the answer.
3. **Guessing (45 seconds):** Show only the selected images. The other 3 teammates have 45 seconds to guess the situation while the Creator stays completely silent (no gestures or hints).
4. Other teams remain silent during another team's turn.

## Tie-breakers & Important Exceptions
- **Meaning over wording:** The host accepts any guess that captures the situation's meaning; exact phrasing is not required.
- **Ties:** Tied scores stand.
- **Awaiting review:** Confirm whether this event uses the 2–4 image secret-situation format or the shared-prompt single-sticker voting variant.
$md$,
            $md$
- **Raw points:** **1 point** per round guessed within 45 seconds (maximum **4**).
- **Leaderboard score:** Normalized to **5 points** (`raw × 1.25`).
- **Example:** 3 correct rounds scores **3.75 pts** (displayed as **3.8 pts**).
$md$
        ),
        (
            'final-rehearsal',
            'Protected 20-minute private rehearsal block for all teams before the Secret Scene Premiere.',
            $md$
## Objective
Polish your team's registered secret scene, transitions, and props so you are ready to perform smoothly during the finale.

> **Host briefing:** "You have 20 minutes to rehearse your registered scene, check props, and be ready for the premiere."

## Players & Arrangement
- **Format:** All teams simultaneously (unscored).
- **Duration:** Protected **20-minute** block—every team rehearses privately in a separate space so titles stay secret.

## Materials & Setup
- Your registered scene title, any props, music, or sound cues.

## How to Play
1. Rehearse your opening, transitions, and ending.
2. Verify that your stage setup takes **under 1 minute**.
3. **Keep your registered title unchanged**—the clues released during the night already reference it.
4. Acting, narration, sound effects, props, and frozen poses are all valid ways to participate.

## Tie-breakers & Important Exceptions
- **Unscored:** Awards **0 league points** (acting quality is never scored).
- **If a team cannot perform:** Their title is still revealed and all guesses against it are still scored.
- **Awaiting review:** Final premiere performance length (**2–3 min** vs. **3–5 min**).
$md$,
            $md$
- **Unscored activity:** Awards **0 league points**.
$md$
        ),
        (
            'secret-scene-premiere',
            'Staged clue releases, private title guessing, and live finale reveals and performances.',
            $md$
## Objective
Deduce every opposing team's secret film, show, or play title as early as possible across staged clue releases—then celebrate with live performances of every scene.

> **Host briefing:** "Last guesses are locked before any reveal. We reveal the title and guessing points, then celebrate your scene."

## Players & Arrangement
- **Format:** All teams.
- **Guessing:** Team captains submit private guesses on behalf of their team for every opposing team's scene (no self-guessing).

## Materials & Setup
- Secretly registered titles with host-approved aliases and **3 truthful clues per team** (from subtle to revealing).

## How to Play
1. **Staged Guessing (Stages 0–3):**
   - **Stage 0:** Before any clues are released.
   - **Stages 1, 2, 3:** After each host-approved clue release (one clue from every team is released together).
   - Submit at most **1 guess per opposing team per stage**. Guesses receive a neutral receipt—correctness stays secret until the finale.
2. **Lockout:** **All guessing closes before the first title is revealed.**
3. **Reveal & Premiere:** For each team, the host reveals the secret title, the earliest correct guesses, and the points earned. The team then performs their scene (≤ 1 min setup).

## Tie-breakers & Important Exceptions
- **Earliest correct only:** Only your **earliest** correct guess for a given team scores; wrong or blank guesses earn 0.
- **Acting quality:** Unscored—performances are for fun and celebration.
- **Final event ties:** Tied final event totals share the win.
- **Awaiting review:** Performer bonus edge case when guessed at Stage 0, leaked-title `+2` policy interaction, and final performance length (**2–3 min** vs. **3–5 min**).
$md$,
            $md$
| Guessing Stage | Points (Earliest Correct) |
| --- | --- |
| **Stage 0** (before clues) | **4 pts** |
| **Stage 1** (after Clue 1) | **3 pts** |
| **Stage 2** (after Clue 2) | **2 pts** |
| **Stage 3** (after Clue 3) | **1 pt** |

- **Performer bonus:** **+1 pt** per opposing team that correctly identifies your title after a clue.
$md$
        )
)
INSERT INTO murise.game_versions (
    game_definition_id,
    version,
    summary,
    rules_markdown,
    end_game_scoring_summary,
    participation_mode,
    scoring_handler,
    scoring_config,
    settings_schema,
    result_schema,
    status,
    source_ref
)
SELECT
    gd.id,
    2,
    v2.summary,
    v2.rules_markdown,
    v2.end_game_scoring_summary,
    v1.participation_mode,
    v1.scoring_handler,
    v1.scoring_config,
    v1.settings_schema,
    v1.result_schema,
    'published',
    v1.source_ref
FROM v2_content v2
         JOIN murise.game_definitions gd ON gd.code = v2.code
         JOIN murise.game_versions v1
              ON v1.game_definition_id = gd.id
                  AND v1.version = 1
ON CONFLICT (game_definition_id, version) DO NOTHING;