"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

type MarkdownEditorProps = {
  titleName?: string;
  bodyName?: string;
  defaultTitle?: string;
  defaultBody?: string;
  submitLabel?: string;
};

function insertMarkdown(
  value: string,
  selectionStart: number,
  selectionEnd: number,
  before: string,
  after = "",
) {
  const selected = value.slice(selectionStart, selectionEnd) || "text";
  const next =
    value.slice(0, selectionStart) +
    before +
    selected +
    after +
    value.slice(selectionEnd);
  return next;
}

function simplePreview(md: string): string {
  return md
    .replace(/^### (.*)$/gm, "<h3>$1</h3>")
    .replace(/^## (.*)$/gm, "<h2>$1</h2>")
    .replace(/^# (.*)$/gm, "<h1>$1</h1>")
    .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.*?)\*/g, "<em>$1</em>")
    .replace(/\[(.*?)\]\((.*?)\)/g, '<a href="$2">$1</a>')
    .replace(/^- (.*)$/gm, "<li>$1</li>")
    .replace(/\n/g, "<br/>");
}

export function MarkdownEditor({
  titleName = "title",
  bodyName = "body",
  defaultTitle = "",
  defaultBody = "",
  submitLabel = "Save draft",
}: MarkdownEditorProps) {
  const [body, setBody] = useState(defaultBody);

  function wrap(before: string, after = "") {
    const el = document.getElementById("notice-body") as HTMLTextAreaElement | null;
    if (!el) {
      setBody((prev) => `${prev}${before}text${after}`);
      return;
    }
    const next = insertMarkdown(
      body,
      el.selectionStart,
      el.selectionEnd,
      before,
      after,
    );
    setBody(next);
  }

  return (
    <div className="space-y-3">
      <Input label="Title" name={titleName} defaultValue={defaultTitle} required />
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" onClick={() => wrap("**", "**")}>
          Bold
        </Button>
        <Button type="button" variant="secondary" onClick={() => wrap("*", "*")}>
          Italic
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => wrap("[", "](https://)")}
        >
          Link
        </Button>
        <Button type="button" variant="secondary" onClick={() => wrap("- ")}>
          List
        </Button>
      </div>
      <Textarea
        id="notice-body"
        label="Body (Markdown)"
        name={bodyName}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        required
      />
      <div className="rounded-md border border-neutral-200 bg-neutral-50 p-3 text-sm">
        <div className="mb-1 text-xs font-semibold uppercase text-neutral-500">
          Preview
        </div>
        <div
          className="prose prose-sm max-w-none"
          dangerouslySetInnerHTML={{ __html: simplePreview(body) }}
        />
      </div>
      <Button type="submit">{submitLabel}</Button>
    </div>
  );
}
