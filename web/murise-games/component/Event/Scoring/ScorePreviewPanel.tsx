"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import { Button } from "@/component/ui/Button";
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui/Card";
import { Input, Select } from "@/component/ui/Fields";
import {
  ScorePreviewApi,
  type ResultState,
  type ScorePreviewGame,
  type ScorePreviewResult,
} from "@/modules/Event/Scoring/score-preview";

interface DraftFixture {
  key: number;
  state: ResultState;
  team: string[];
  opponent: string[];
}

function newFixture(game: ScorePreviewGame, key: number): DraftFixture {
  return {
    key,
    state: "NOT_PLAYED",
    team: game.fields.map(() => ""),
    opponent: game.headToHead ? game.fields.map(() => "") : [],
  };
}

export function ScorePreviewPanel() {
  const { event, canManage } = useEventDetail();

  if (!canManage) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Scoring is managed by your organizer</CardTitle>
          <CardDescription>
            Your organizer records official results and controls when
            standings are shared.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return <OrganizerPreview key={event.id} eventId={event.id} />;
}

function OrganizerPreview({ eventId }: { eventId: string }) {
  const [games, setGames] = useState<ScorePreviewGame[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);

      try {
        const options = await ScorePreviewApi.games(eventId);

        if (!active) return;

        setGames(options);
        setSelectedId((current) => {
          if (options.some((game) => game.eventGameId === current)) {
            return current;
          }

          return (
            options.find((game) => !game.unavailableReason)?.eventGameId ??
            options[0]?.eventGameId ??
            ""
          );
        });
      } catch (cause) {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : "Could not load scoring options.",
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, [eventId, reloadKey]);

  const selected = games.find((game) => game.eventGameId === selectedId);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <p className="text-sm font-medium text-highlight">
            Scoring · Policy A
          </p>
          <CardTitle>Same opportunities. Clear scores.</CardTitle>
          <CardDescription>
            Every team receives the same scored games and equal opportunities.
            Each included game contributes up to five points. Premiere bonuses
            stay separate.
          </CardDescription>
        </CardHeader>

        <div className="rounded-control border border-border bg-surface-raised p-4">
          <p className="font-medium">Try a score before recording results</p>
          <p className="mt-1 text-sm text-muted">
            This preview explains the calculation. It does not save results,
            approve rules or publish points.
          </p>
        </div>
      </Card>

      {loading && (
        <p role="status" className="text-sm text-muted">
          Loading your event’s scoring options…
        </p>
      )}

      {error && (
        <Card>
          <p role="alert">{error}</p>
          <Button
            className="mt-4"
            variant="secondary"
            onClick={() => setReloadKey((value) => value + 1)}
          >
            Try again
          </Button>
        </Card>
      )}

      {!loading && !error && games.length === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Add your first game</CardTitle>
            <CardDescription>
              Choose activities for this event before previewing their scores.
            </CardDescription>
          </CardHeader>
          <Link
            href={`/events/${eventId}/games`}
            className="font-medium text-highlight hover:underline"
          >
            Choose games →
          </Link>
        </Card>
      )}

      {!loading && !error && games.length > 0 && (
        <Card>
          <Select
            label="Which game would you like to check?"
            value={selectedId}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            {games.map((game) => (
              <option key={game.eventGameId} value={game.eventGameId}>
                {game.name}
                {game.unavailableReason ? " · unavailable" : ""}
              </option>
            ))}
          </Select>

          {selected && (
            <p className="mt-2 text-sm text-muted">
              Using this event’s pinned rules: version {selected.ruleVersion}.
            </p>
          )}
        </Card>
      )}

      {!loading && !error && selected && (
        selected.unavailableReason ? (
          <Card>
            <CardHeader>
              <CardTitle>{selected.name}</CardTitle>
              <CardDescription>
                {selected.unavailableReason}
              </CardDescription>
            </CardHeader>
          </Card>
        ) : (
          <PreviewForm
            key={`${selected.eventGameId}:${selected.gameVersionId}`}
            eventId={eventId}
            game={selected}
          />
        )
      )}
    </div>
  );
}

function PreviewForm({
  eventId,
  game,
}: {
  eventId: string;
  game: ScorePreviewGame;
}) {
  const nextKey = useRef(2);
  const alive = useRef(true);
const resultHeading = useRef<HTMLSpanElement>(null);

  const [fixtures, setFixtures] = useState<DraftFixture[]>([
    newFixture(game, 1),
  ]);
  const [result, setResult] = useState<ScorePreviewResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    alive.current = true;

    return () => {
      alive.current = false;
    };
  }, []);

  useEffect(() => {
    if (result) resultHeading.current?.focus();
  }, [result]);

  function changeFixture(
    key: number,
    update: (fixture: DraftFixture) => DraftFixture,
  ) {
    setFixtures((current) =>
      current.map((fixture) => fixture.key === key ? update(fixture) : fixture),
    );
    setResult(null);
    setError(null);
  }

  function changeValue(
    key: number,
    side: "team" | "opponent",
    index: number,
    value: string,
  ) {
    changeFixture(key, (fixture) => ({
      ...fixture,
      [side]: fixture[side].map((previous, position) =>
        position === index ? value : previous,
      ),
    }));
  }

  function addFixture() {
    setFixtures((current) => [
      ...current,
      newFixture(game, nextKey.current++),
    ]);
    setResult(null);
    setError(null);
  }

  function removeFixture(key: number) {
    setFixtures((current) => current.filter((fixture) => fixture.key !== key));
    setResult(null);
    setError(null);
  }

  function numbers(values: string[]): number[] {
    return values.map((value) => {
      if (!/^\d+$/.test(value)) {
        throw new Error("Complete every result with a whole number.");
      }

      const parsed = Number(value);

      if (
        !Number.isSafeInteger(parsed) ||
        parsed < 0 ||
        parsed > game.maximumPerField
      ) {
        throw new Error(
          `Enter whole numbers from 0 to ${game.maximumPerField}.`,
        );
      }

      return parsed;
    });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;

    setBusy(true);
    setError(null);
    setResult(null);

    try {
      const payload = fixtures.map((fixture) => ({
        state: fixture.state,
        team: fixture.state === "CONFIRMED" ? numbers(fixture.team) : [],
        opponent:
          fixture.state === "CONFIRMED" && game.headToHead
            ? numbers(fixture.opponent)
            : [],
      }));

      const response = await ScorePreviewApi.calculate(
        eventId,
        game.eventGameId,
        payload,
      );

      if (alive.current) setResult(response);
    } catch (cause) {
      if (alive.current) {
        setError(
          cause instanceof Error
            ? cause.message
            : "Could not calculate the preview.",
        );
      }
    } finally {
      if (alive.current) setBusy(false);
    }
  }

  const unitMaximum = game.fields.length * game.maximumPerField;

  return (
    <>
      <form onSubmit={submit} className="space-y-5">
        <Card>
          <CardHeader>
            <CardTitle>{game.name}</CardTitle>
            <CardDescription>{game.inputHint}</CardDescription>
          </CardHeader>

          <p className="text-sm text-muted">{game.decisionNote}</p>

          {game.headToHead && (
            <p className="mt-3 text-sm text-muted">
              Add one example fixture per scheduled opponent. A bye is an
              empty playing slot, not a fixture with a win or loss.
            </p>
          )}

          <div className="mt-4 rounded-control border border-border p-4">
            <p className="text-sm font-medium">Maximum available raw points</p>
            <p className="mt-1 text-xl font-semibold">
              {fixtures.length} × {unitMaximum} ={" "}
              {fixtures.length * unitMaximum}
            </p>
            <p className="mt-1 text-sm text-muted">
              The official denominator will come from the published schedule.
              Missing results do not reduce it.
            </p>
          </div>
        </Card>

        {fixtures.map((fixture, fixtureIndex) => (
          <Card key={fixture.key}>
            <fieldset disabled={busy} className="space-y-4">
              <legend className="mb-3 text-lg font-semibold">
                {game.headToHead
                  ? `Match ${fixtureIndex + 1}`
                  : "Team appearance"}
              </legend>

              <Select
                label="Result status"
                value={fixture.state}
                onChange={(event) => {
                  const state = event.target.value as ResultState;

                  changeFixture(fixture.key, (current) => ({
                    ...current,
                    state,
                    team: game.fields.map(() => ""),
                    opponent: game.headToHead
                      ? game.fields.map(() => "")
                      : [],
                  }));
                }}
              >
                <option value="NOT_PLAYED">Awaiting a result</option>
                <option value="CONFIRMED">Result confirmed by the judge</option>
                <option value="CANCELLED">Cancelled · needs resolution</option>
              </Select>

              {fixture.state === "CONFIRMED" ? (
                <div className="space-y-4">
                  {game.fields.map((label, index) => (
                    <div
                      key={label}
                      className={
                        game.headToHead
                          ? "grid gap-3 sm:grid-cols-2"
                          : "max-w-sm"
                      }
                    >
                      <Input
                        label={`${label} · your team`}
                        type="number"
                        inputMode="numeric"
                        min={0}
                        max={game.maximumPerField}
                        step={1}
                        required
                        value={fixture.team[index]}
                        onChange={(event) =>
                          changeValue(
                            fixture.key,
                            "team",
                            index,
                            event.target.value,
                          )
                        }
                      />

                      {game.headToHead && (
                        <Input
                          label={`${label} · opponent`}
                          type="number"
                          inputMode="numeric"
                          min={0}
                          max={game.maximumPerField}
                          step={1}
                          required
                          value={fixture.opponent[index]}
                          onChange={(event) =>
                            changeValue(
                              fixture.key,
                              "opponent",
                              index,
                              event.target.value,
                            )
                          }
                        />
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted">
                  {fixture.state === "CANCELLED"
                    ? "This opportunity stays in the denominator. It must be resolved before the game can be final."
                    : "No score is recorded for this fixture yet. It is not a loss."}
                </p>
              )}

              {game.headToHead && fixtures.length > 1 && (
                <Button
                  variant="secondary"
                  onClick={() => removeFixture(fixture.key)}
                >
                  Remove this example match
                </Button>
              )}
            </fieldset>
          </Card>
        ))}

        {game.headToHead && (
          <Button
            variant="secondary"
            disabled={busy || fixtures.length >= 31}
            onClick={addFixture}
          >
            + Add an example match
          </Button>
        )}

        {error && (
          <p role="alert" className="ui-field-error">
            {error}
          </p>
        )}

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <Button
            type="submit"
            loading={busy}
            loadingText="Calculating…"
          >
            Preview score
          </Button>
          <p className="text-sm text-muted">
            No points will be saved or published.
          </p>
        </div>
      </form>

      {result && (
        <Card aria-live="polite">
          <CardHeader>
            <CardTitle>
              <span ref={resultHeading} tabIndex={-1}>
                {result.complete
                  ? "Complete example"
                  : "Provisional example"}
              </span>
            </CardTitle>
            <CardDescription>{result.message}</CardDescription>
          </CardHeader>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-control bg-surface-raised p-4">
              <p className="text-sm text-muted">Game contribution</p>
              <p className="mt-1 text-3xl font-semibold text-highlight">
                {result.displayPoints}
                <span className="ml-1 text-base text-muted">/ 5</span>
              </p>
            </div>

            <div className="rounded-control bg-surface-raised p-4">
              <p className="text-sm text-muted">Raw result</p>
              <p className="mt-1 text-2xl font-semibold">
                {result.rawScore} / {result.rawMaximum}
              </p>
            </div>

            <div className="rounded-control bg-surface-raised p-4">
              <p className="text-sm text-muted">Confirmed fixtures</p>
              <p className="mt-1 text-2xl font-semibold">
                {result.confirmedFixtures} / {result.expectedFixtures}
              </p>
            </div>
          </div>

          <p className="mt-4 text-sm text-muted">
            Calculation: {result.rawScore} ÷ {result.rawMaximum} × 5.
            The display is rounded once; the exact value is retained.
          </p>

          <details className="mt-4 text-sm">
            <summary className="cursor-pointer font-medium text-highlight">
              Calculation details
            </summary>
            <dl className="mt-3 grid gap-2 text-muted sm:grid-cols-2">
              <div>
                <dt>Exact fraction</dt>
                <dd className="font-mono text-foreground">
                  {result.exactNumerator}/{result.exactDenominator}
                </dd>
              </div>
              <div>
                <dt>Four-decimal settlement projection</dt>
                <dd className="font-mono text-foreground">
                  {result.settlementAmount}
                </dd>
              </div>
            </dl>
          </details>
        </Card>
      )}
    </>
  );
}