import { afterEach, beforeEach, expect, test, vi } from "vitest";
import {
  clearQuizProfiles,
  readQuizProfile,
  saveQuizProfile,
} from "./quiz-session";
import { clearSession } from "./api";
let storage: Map<string, string>;
beforeEach(() => {
  clearQuizProfiles();
  storage = new Map();
  vi.stubGlobal("sessionStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    clear: () => storage.clear(),
  });
});
afterEach(() => vi.unstubAllGlobals());
test("remembers named or skipped choices during a session and isolates identities", () => {
  expect(readQuizProfile("visitor")).toBeNull();
  saveQuizProfile("visitor", { displayName: "Zoë O’Neil", gender: "woman" });
  expect(readQuizProfile("visitor")).toEqual({
    displayName: "Zoë O’Neil",
    gender: "woman",
  });
  expect(readQuizProfile("another-account")).toBeNull();
  saveQuizProfile("visitor", { displayName: "", gender: "undisclosed" });
  expect(readQuizProfile("visitor")).toEqual({
    displayName: "",
    gender: "undisclosed",
  });
});
test("a fresh session does not retain the previous choice; logout clears it", () => {
  saveQuizProfile("visitor", { displayName: "Biak", gender: "man" });
  storage.clear();
  expect(readQuizProfile("visitor")).toBeNull();
  saveQuizProfile("visitor", { displayName: "Biak", gender: "man" });
  clearSession();
  expect(readQuizProfile("visitor")).toBeNull();
});
test("navigation still remembers a choice if session storage is unavailable", () => {
  vi.stubGlobal("sessionStorage", {
    getItem: () => {
      throw new Error("Disabled");
    },
    setItem: () => {
      throw new Error("Disabled");
    },
    clear: () => {
      throw new Error("Disabled");
    },
  });
  saveQuizProfile("visitor", { displayName: "", gender: "undisclosed" });
  expect(readQuizProfile("visitor")).toEqual({
    displayName: "",
    gender: "undisclosed",
  });
  clearSession();
  expect(readQuizProfile("visitor")).toBeNull();
});
