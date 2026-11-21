"use client";

import { useState, type FormEvent } from "react";

type State = { kind: "idle" } | { kind: "busy" } | { kind: "error"; message: string } | { kind: "done" };

export function DeleteAccountForm() {
  const [state, setState] = useState<State>({ kind: "idle" });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setState({ kind: "busy" });
    try {
      const res = await fetch("/api/auth/delete-account", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: data.get("email"), password: data.get("password") }),
      });
      if (res.ok) {
        setState({ kind: "done" });
        return;
      }
      const body = (await res.json().catch(() => null)) as { error?: { message?: string } } | null;
      setState({ kind: "error", message: body?.error?.message ?? "Couldn't delete the account. Try again." });
    } catch {
      setState({ kind: "error", message: "Couldn't reach CampusBazaar. Check your connection and try again." });
    }
  }

  if (state.kind === "done") {
    return (
      <p className="callout" role="status">
        Your account has been deleted and you&apos;ve been signed out on every device. You can uninstall the app.
      </p>
    );
  }

  return (
    <form className="form" onSubmit={submit} noValidate={false}>
      <label className="form__field">
        <span>Email</span>
        <input name="email" type="email" autoComplete="email" required />
      </label>
      <label className="form__field">
        <span>Password</span>
        <input name="password" type="password" autoComplete="current-password" required minLength={8} />
      </label>
      <label className="form__check">
        <input name="confirm" type="checkbox" required />
        <span>I understand my listings, wanted posts and profile will be removed for good.</span>
      </label>
      {state.kind === "error" && (
        <p className="form__error" role="alert">
          {state.message}
        </p>
      )}
      <button className="btn btn--danger btn--big" type="submit" disabled={state.kind === "busy"}>
        {state.kind === "busy" ? "Deleting…" : "Delete my account"}
      </button>
    </form>
  );
}
