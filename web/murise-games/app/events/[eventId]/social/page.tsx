import { SocialCasePoolPanel } from "@/component/Event/Social/SocialCasePoolPanel";

export default async function SocialCasesPage({
  params,
}: {
  params: Promise<{ eventId: string }>;
}) {
  const { eventId } = await params;

  return <SocialCasePoolPanel eventId={eventId} />;
}