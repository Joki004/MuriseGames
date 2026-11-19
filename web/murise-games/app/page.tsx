import Link from "next/link";

export default function Home() {
  return (
    <section className="hero">
      <p className="eyebrow">THE NIGHT HAS A PLOT TWIST</p>
      <h1>Bring your people.<br /><em>Make a story.</em></h1>
      <p className="intro">Your home for game nights, unlikely victories, and the team you’ll be talking about tomorrow.</p>
      <Link className="button" href="/account">Get ready for game night <span aria-hidden="true">↗</span></Link>
      <div className="preview-grid">
        <article><span className="number">01</span><h2>Your crew</h2><p>Team up with friends and give your night an identity.</p></article>
        <article><span className="number">02</span><h2>Your challenge</h2><p>Physical, creative, and unexpected. Everyone has a moment to shine.</p></article>
        <article><span className="number">03</span><h2>Your story</h2><p>The best part is what happens between the points.</p></article>
      </div>
      <p className="muted">We’re building the first edition. Event creation, schedules, and scoring are coming next.</p>
    </section>
  );
}
