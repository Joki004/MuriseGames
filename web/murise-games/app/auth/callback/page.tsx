import { Suspense } from "react";
import { AuthCallbackView } from "@/component/Auth/AuthCallbackView";

export default function AuthCallbackPage() {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <Suspense fallback={<p className="text-sm text-zinc-400">Verifying link...</p>}>
        <AuthCallbackView />
      </Suspense>
    </main>
  );
}