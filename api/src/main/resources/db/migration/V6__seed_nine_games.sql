-- V6__seed_nine_games.sql
-- Seeds the 9 FOR THE PLOT master-book activities (v1, published).

WITH defs (code, name, kind, summary, rules_markdown, end_game_scoring_summary, participation_mode, scoring_handler, scoring_config, source_ref) AS (
    VALUES
        (
            'grab-the-cup',
            'Grab the Cup',
            'competitive',
            'Head-to-head reflex duels: touch body parts on command and grab the cup only on CUP.',
            'Four player duels per pairing. First to 2 valid grabs wins a duel. Reaching early loses the attempt.',
            '1 raw point per duel won (max 4 per fixture). Normalized: 5 * wins / max.',
            'head_to_head',
            'grab_the_cup_v1',
            '{"duels": 4, "attempt_wins": 2, "max_raw": 4, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-6'
        ),
        (
            'sensory-chain',
            'The Sensory Chain',
            'party',
            'Four 45-second team rotations: Mime -> Sound FX -> Guesser, with 1 pass per turn.',
            'Teams of 4 rotate roles across 4 turns (3 action cards per turn). No speech, mouthing, or spelling.',
            '1 raw point per correct card (max 12). Normalized: 5 * correct / 12.',
            'team_solo',
            'sensory_chain_v1',
            '{"turns": 4, "turn_seconds": 45, "cards_per_turn": 3, "passes_per_turn": 1, "max_raw": 12, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-7'
        ),
        (
            'unified-alibi',
            'Unified Alibi',
            'social_deduction',
            'Paired teammates prepare an alibi in 90s, then answer 10 questions separately to match details.',
            'Two pairs per team, fresh case per pair. Fixed facts must be accurate and invented details must match.',
            '1 raw point per semantic match (max 20 per team). Normalized: raw / 4.',
            'team_solo',
            'unified_alibi_v1',
            '{"pairs": 2, "prep_seconds": 90, "questions_per_pair": 10, "max_raw": 20, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-8'
        ),
        (
            'social-scenario',
            'Social Scenario',
            'social_deduction',
            'Defender completes 3 secret goals while Attacker tries to trigger 2 secret red flags.',
            'Two rounds (swapping attacker/defender legs) plus optional third round if lead <= 6. Fresh scenario each turn.',
            'Defender: 1/goal + 1 clean finish. Attacker: 2 per confirmed red flag. Normalized to 5.',
            'head_to_head',
            'social_scenario_v1',
            '{"goals": 3, "red_flags": 2, "prep_seconds": 30, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-9'
        ),
        (
            'poison-game',
            'Poison Game',
            'competitive',
            'Alternating cup picks against a secret poison marker known only to the gamemaster.',
            'Two rounds per match with 6 identical cups. Picking poison loses the round immediately; swap starter in round 2.',
            '1 raw point per round won (2-0 or 1-1). Normalized: 5 * rounds_won / rounds_played.',
            'head_to_head',
            'poison_game_v1',
            '{"cups": 6, "rounds": 2, "turn_seconds": 5, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-10'
        ),
        (
            'rhythm-grid-hunt',
            'Rhythm Grid Hunt',
            'competitive',
            '1 hunter vs 3 runners on a 3x3 grid across 12 movement cues; landing on the same square catches.',
            'Four rounds (2 turns each, swapping hunter/runner). Move orthogonally or stay on cue; no diagonals.',
            'Hunter earns catches (0-3); runners earn survivors (3-c). Max 24 per fixture. Normalized: 5 * raw / 24.',
            'head_to_head',
            'rhythm_grid_hunt_v1',
            '{"rounds": 4, "cues_per_turn": 12, "max_raw": 24, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-11'
        ),
        (
            'group-chat-meltdown',
            'Group Chat Meltdown',
            'party',
            'Select 2-4 memes/stickers/GIFs to communicate a secret situation for teammates to guess.',
            'Four rounds rotating creator. 60s to pick 2-4 images; teammates have 45s to guess the situation.',
            '1 raw point per correct round (max 4). Normalized: raw * 1.25.',
            'team_solo',
            'group_chat_meltdown_v1',
            '{"rounds": 4, "select_seconds": 60, "guess_seconds": 45, "max_raw": 4, "max_normalized": 5}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-5-12'
        ),
        (
            'final-rehearsal',
            'Final Rehearsal',
            'rehearsal',
            'Protected 20-minute private rehearsal block for all teams before the Secret Scene Premiere.',
            'All teams rehearse their registered scene, props, sound, and transitions. Unscored activity.',
            'Unscored (0 league points).',
            'all_teams',
            'unscored_rehearsal_v1',
            '{"duration_minutes": 20, "scored": false}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-2-13'
        ),
        (
            'secret-scene-premiere',
            'Secret Scene Premiere',
            'party',
            'Staged clue releases, private title guessing, and live finale reveals and performances.',
            'One guess per opposing team per stage (0-3). All guessing closes before the first reveal.',
            'Earliest correct guess earns 4/3/2/1 pts; performer earns +1 per opponent guessing correctly after a clue.',
            'all_teams',
            'secret_scene_premiere_v1',
            '{"stages": [0, 1, 2, 3], "stage_points": {"0": 4, "1": 3, "2": 2, "3": 1}, "performer_bonus": 1}'::jsonb,
            '01_FOR_THE_PLOT_Rulebook.pptx#slides-2-5-14'
        )
),
     inserted_defs AS (
         INSERT INTO murise.game_definitions (code, name, kind, active)
             SELECT code, name, kind, true
             FROM defs
             ON CONFLICT (code) DO NOTHING
             RETURNING id, code
     ),
     all_defs AS (
         SELECT id, code FROM inserted_defs
         UNION
         SELECT gd.id, gd.code
         FROM murise.game_definitions gd
                  JOIN defs d ON d.code = gd.code
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
    ad.id,
    1,
    d.summary,
    d.rules_markdown,
    d.end_game_scoring_summary,
    d.participation_mode,
    d.scoring_handler,
    d.scoring_config,
    '{}'::jsonb,
    '{}'::jsonb,
    'published',
    d.source_ref
FROM all_defs ad
         JOIN defs d ON d.code = ad.code
ON CONFLICT (game_definition_id, version) DO NOTHING;