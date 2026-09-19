"use client";

import type { ReactNode } from "react";

import { Button } from "./button";

type ModalProps = {
  title: string;
  children: ReactNode;
  onClose: () => void;
  open?: boolean;
};

export function Modal({ title, children, onClose, open = true }: ModalProps) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div className="w-full max-w-lg rounded-lg bg-white p-4 shadow-lg">
        <div className="mb-3 flex items-start justify-between gap-3">
          <h2 id="modal-title" className="text-lg font-semibold text-neutral-900">
            {title}
          </h2>
          <Button type="button" variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
        {children}
      </div>
    </div>
  );
}
