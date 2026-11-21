"use client";

import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";

export function Rules({ text }: { text: string }) {
  return (
    <div className="game-rules">
      <Markdown remarkPlugins={[remarkGfm]} skipHtml>
        {text}
      </Markdown>
    </div>
  );
}