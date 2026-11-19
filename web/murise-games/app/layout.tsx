import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "MuriseGames — For the plot",
  description: "A little friendly competition. A lot of stories to tell.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <header className="site-header">
          <Link className="brand" href="/">MuriseGames<span>FOR THE PLOT</span></Link>
          <nav aria-label="Main navigation"><Link href="/account">Your account</Link></nav>
        </header>
        <main>{children}</main>
        <footer>Good company. Friendly rivalry. Stories worth keeping.</footer>
      </body>
    </html>
  );
}
