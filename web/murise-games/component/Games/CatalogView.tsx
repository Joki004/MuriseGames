"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { PageShell } from "@/component/Layout/PageShell";
import { AsyncContent } from "@/component/ui/AsyncContent";
import {
  Badge,
  Card,
  CardHeader,
  CardTitle,
  EmptyState,
  Input,
  Select,
} from "@/component/ui";
import {
  CatalogController,
  type CatalogGameResponse,
} from "@/modules/Catalog";
import { Rules } from "./Rules";

const loadCatalog = () => CatalogController.list();

function readable(value: string) {
  return value.replaceAll("_", " ");
}

export function CatalogView() {
  return (
    <RequireAccount>
      <PageShell
        title="Games catalog"
        description="Find your next challenge and read the published rules."
      >
        <AsyncContent load={loadCatalog} label="Loading games…">
          {(games) => <CatalogList games={games} />}
        </AsyncContent>
      </PageShell>
    </RequireAccount>
  );
}

function CatalogList({ games }: { games: CatalogGameResponse[] }) {
  const [search, setSearch] = useState("");
  const [kind, setKind] = useState("");

  const query = search.trim().toLowerCase();
  const filtered = games.filter(
    (game) =>
      (!kind || game.kind === kind) &&
      `${game.name} ${game.summary}`.toLowerCase().includes(query),
  );

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-[1fr_14rem]">
        <Input
          label="Search games"
          type="search"
          placeholder="Search by name or description"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />

        <Select
          label="Game type"
          value={kind}
          onChange={(event) => setKind(event.target.value)}
        >
          <option value="">All types</option>
          <option value="party">Party</option>
          <option value="competitive">Competitive</option>
          <option value="social_deduction">Social deduction</option>
          <option value="rehearsal">Rehearsal</option>
        </Select>
      </div>

      <p role="status" className="text-sm text-muted">
        {filtered.length} game(s)
      </p>

      {filtered.length === 0 ? (
        <EmptyState
          title={games.length === 0 ? "No published games yet" : "No matching games"}
          description={
            games.length === 0
              ? "Published games will appear here."
              : "Try another search or game type."
          }
        />
      ) : (
        <ul className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((game) => (
            <li key={game.definitionId} className="min-w-0">
              <Card className="flex h-full flex-col gap-4">
                <div className="flex flex-wrap gap-2">
                  <Badge tone="accent">{readable(game.kind)}</Badge>
                  <Badge>{readable(game.participationMode)}</Badge>
                </div>

                <h2 className="break-words text-xl font-semibold">
                  <Link
                    href={`/games/${encodeURIComponent(game.code)}`}
                    className="hover:text-highlight"
                  >
                    {game.name}
                  </Link>
                </h2>

                <p className="break-words text-sm text-muted">
                  {game.summary}
                </p>

                <Link
                  href={`/games/${encodeURIComponent(game.code)}`}
                  className="ui-button ui-button--secondary mt-auto"
                >
                  View game and rules
                </Link>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function CatalogGameView() {
  const { code } = useParams<{ code: string }>();

  return (
    <RequireAccount>
      <AsyncContent
        key={code}
        load={loadCatalog}
        label="Loading game rules…"
      >
        {(games) => {
          const game = games.find((item) => item.code === code);

          if (!game) {
            return (
              <PageShell title="Game unavailable">
                <EmptyState
                  title="This game is not in the published catalog"
                  description="It may have been removed or is not published yet."
                  action={
                    <Link href="/games" className="ui-button ui-button--secondary">
                      Back to games
                    </Link>
                  }
                />
              </PageShell>
            );
          }

          return (
            <PageShell
              title={game.name}
              description={game.summary}
              action={
                <Link href="/games" className="ui-button ui-button--secondary">
                  All games
                </Link>
              }
            >
              <div className="flex flex-wrap gap-2">
                <Badge tone="accent">{readable(game.kind)}</Badge>
                <Badge>{readable(game.participationMode)}</Badge>
                <Badge>Published version {game.version}</Badge>
              </div>

              <Card>
                <CardHeader>
                  <CardTitle>How to play</CardTitle>
                </CardHeader>
                <Rules text={game.rulesMarkdown} />
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Scoring</CardTitle>
                </CardHeader>
                <Rules text={game.endGameScoringSummary} />
              </Card>
            </PageShell>
          );
        }}
      </AsyncContent>
    </RequireAccount>
  );
}