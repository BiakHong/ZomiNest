import { clearQuizProfiles } from "./quiz-session";
export type RecordData = Record<string, any>;
let csrf = "";
export async function api(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<any> {
  if (method !== "GET" && !csrf) {
    const s = await api("/session");
    csrf = s.csrf;
  }
  const r = await fetch("/api" + path, {
    method,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(method !== "GET" ? { "X-CSRF-TOKEN": csrf } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (r.status === 403) {
    csrf = "";
  }
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    throw new Error(e.message || `Request failed (${r.status}).`);
  }
  if (r.status === 204 || r.headers.get("content-length") === "0") return null;
  const text = await r.text();
  const value = text ? JSON.parse(text) : null;
  if (path === "/session" && value) csrf = value.csrf;
  return value;
}
export function clearSession() {
  csrf = "";
  clearQuizProfiles();
  try {
    sessionStorage.clear();
  } catch {
    /* Browser storage may be unavailable. */
  }
}
