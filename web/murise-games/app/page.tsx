import Link from "next/link";

const FEATURES = [
  {
    title: "Teams and invitations",
    text: "Share one invitation link. Players create an account, join the event and land on their team.",
  },
  {
    title: "Games and schedule",
    text: "Pick games from the catalog and get a balanced schedule, so every team plays a fair number of matches.",
  },
  {
    title: "Live scores",
    text: "Hosts and referees record results as the night goes on. The scoreboard updates on its own.",
  },
  {
    title: "Player and TV screens",
    text: "Each player follows their team on their phone, and a TV screen shows the room what's happening.",
  },
];

const HOST_STEPS = [
  "Create an event with its date and venue.",
  "Choose the games and set up the teams.",
  "Send the invitation link and run the night from your host screen.",
];

const PLAYER_STEPS = [
  "Open the invitation link from your host.",
  "Sign up or log in to join the event.",
  "Follow your team, your matches and your results on your phone.",
];

export default function Home() {
  return (
    <main
      id="main-content"
      className="mx-auto flex w-full max-w-5xl flex-col gap-16 px-6 py-16"
    >
      <section className="flex flex-col items-center gap-8 text-center">
        <div className="space-y-4">
          <p className="text-sm font-semibold uppercase tracking-widest text-muted">
            Team game nights, organised
          </p>
          <h1 className="text-4xl font-bold tracking-tight sm:text-6xl">
            Host the game night.
            <br />
            We&apos;ll keep the score.
          </h1>
          <p className="mx-auto max-w-2xl text-lg text-muted">
            MuriseGames helps you run team game nights: invite your friends,
            build the teams, schedule the games and follow the scores live, all
            from one place.
          </p>
        </div>

        <div className="flex flex-wrap justify-center gap-3">
          <Link href="/login" className="ui-button ui-button--primary">
            Sign up or log in
          </Link>
          <Link href="/dashboard" className="ui-button ui-button--secondary">
            My events
          </Link>
        </div>

        <p className="max-w-md text-sm text-muted">
          After signing up, check your inbox for the confirmation email. If it
          isn&apos;t there, look in your spam folder.
        </p>
      </section>

      <section aria-labelledby="features-heading" className="space-y-6">
        <h2
          id="features-heading"
          className="text-center text-2xl font-semibold tracking-tight"
        >
          Everything a game night needs
        </h2>
        <ul className="grid gap-4 sm:grid-cols-2">
          {FEATURES.map((feature) => (
            <li key={feature.title} className="ui-card space-y-2">
              <h3 className="text-lg font-semibold">{feature.title}</h3>
              <p className="text-muted">{feature.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="how-heading" className="space-y-6">
        <h2
          id="how-heading"
          className="text-center text-2xl font-semibold tracking-tight"
        >
          How it works
        </h2>
        <div className="grid gap-4 md:grid-cols-2">
          <StepList title="If you're hosting" steps={HOST_STEPS} />
          <StepList title="If you're invited" steps={PLAYER_STEPS} />
        </div>
      </section>

      <section className="ui-card flex flex-col items-center gap-2 text-center">
        <h2 className="text-lg font-semibold">Got an invitation?</h2>
        <p className="max-w-xl text-muted">
          Open the invitation link your host sent you. It takes you straight to
          the right event, and you can sign up from there.
        </p>
      </section>
    </main>
  );
}

function StepList({ title, steps }: { title: string; steps: string[] }) {
  return (
    <div className="ui-card space-y-4">
      <h3 className="text-lg font-semibold">{title}</h3>
      <ol className="space-y-3">
        {steps.map((step, index) => (
          <li key={step} className="flex gap-3">
            <span
              aria-hidden="true"
              className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-semibold text-white"
            >
              {index + 1}
            </span>
            <span className="text-muted">{step}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}