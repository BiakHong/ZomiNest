import { test, expect, vi, afterEach } from "vitest";
import { api, clearSession } from "./api";
afterEach(() => vi.unstubAllGlobals());
test("mutations acquire a session token and use same-origin cookies", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ csrf: "test-token", user: null })),
    )
    .mockResolvedValueOnce(new Response(JSON.stringify({ id: "saved" })));
  vi.stubGlobal("fetch", fetch);
  vi.stubGlobal("sessionStorage", { clear: vi.fn() });
  clearSession();
  await api("/submissions", "POST", { title: "A question" });
  expect(fetch.mock.calls[1][1].credentials).toBe("same-origin");
  expect(fetch.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("test-token");
});
test("API errors retain server validation messages", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ message: "Answer every question." }), {
          status: 400,
        }),
      ),
  );
  await expect(api("/quizzes")).rejects.toThrow("Answer every question.");
});
