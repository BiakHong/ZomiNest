import { test, expect, type Page } from "@playwright/test";
const base = process.env.TEST_BASE_URL || "http://localhost:5173";
async function api(page: Page, path: string, method = "GET", data?: unknown) {
  const session = await (await page.request.get("/api/session")).json();
  const response = await page.request.fetch("/api" + path, {
    method,
    data,
    headers: { "X-CSRF-TOKEN": session.csrf },
  });
  expect(
    response.ok(),
    `${method} ${path}: ${await response.text()}`,
  ).toBeTruthy();
  return response.json();
}
async function start(page: Page, slug: string, name: string) {
  await page.goto("/quizzes/" + slug);
  await page.getByRole("button", { name: /Start (quiz|test)/ }).click();
  const dialog = page.getByRole("dialog", { name: "Na min bang?" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Na min bang?", { exact: true }).fill(name);
  await dialog.getByRole("radio", { name: "Numei", exact: true }).check();
  await dialog.getByRole("button", { name: "Save & start" }).click();
  await expect(page).toHaveURL(/\/play\//);
  return page.url().split("/play/")[1];
}
async function layout(page: Page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  for (const group of await page.locator(".likert-row").all()) {
    const boxes = await group.locator("label").evaluateAll((nodes) =>
      nodes.map((n) => {
        const b = n.getBoundingClientRect();
        return { top: b.top, width: b.width, height: b.height };
      }),
    );
    expect(boxes).toHaveLength(7);
    expect(new Set(boxes.map((b) => b.top)).size).toBe(1);
    expect(boxes.every((b) => b.width >= 35 && b.height >= 44)).toBeTruthy();
  }
}

test("60 statements, horizontal radios, keyboard, validation, refresh and resume", async ({
  page,
}, testInfo) => {
  const id = await start(page, "discover-personality", "Biak O’Neil");
  await expect(page.locator(".personality-statement")).toHaveCount(5);
  await expect(page.getByRole("radio")).toHaveCount(35);
  await expect(page.getByRole("radio", { checked: true })).toHaveCount(0);
  await layout(page);
  await page.screenshot({
    path: testInfo.outputPath("personality-page.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(page.locator(".answer-required")).toHaveCount(5);
  await expect(page.locator("#statement-q01")).toBeFocused();
  const first = page.locator("#statement-q01");
  await first.getByRole("radio", { name: "Neutral", exact: true }).focus();
  await page.keyboard.press("ArrowRight");
  await expect(
    first.getByRole("radio", { name: "Slightly agree", exact: true }),
  ).toBeChecked();
  await page.keyboard.press("ArrowLeft");
  await expect(
    first.getByRole("radio", { name: "Neutral", exact: true }),
  ).toBeChecked();
  for (const group of await page.locator(".personality-statement").all())
    await group.getByRole("radio", { name: "Neutral", exact: true }).check();
  await expect(
    page.getByText("5 of 60 answered", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Page 1 of 12", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(page.getByText("Page 2 of 12", { exact: true })).toBeVisible();
  await page
    .locator("#statement-q06")
    .getByRole("radio", { name: "Agree", exact: true })
    .check();
  await page
    .getByRole("button", { name: "Save progress", exact: true })
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Progress saved" }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByText("Page 2 of 12", { exact: true })).toBeVisible();
  await expect(
    page
      .locator("#statement-q06")
      .getByRole("radio", { name: "Agree", exact: true }),
  ).toBeChecked();
  await page.goto("/results");
  await expect(
    page.locator(".result-row").filter({ hasText: "Biak O’Neil" }),
  ).toContainText("Resume attempt");
  await page.locator(".result-row").filter({ hasText: "Biak O’Neil" }).click();
  await expect(page.getByText("Page 2 of 12", { exact: true })).toBeVisible();
  // Force the server copy to be used when this browser's progress cache is unavailable.
  await page.evaluate((id) => localStorage.removeItem("attempt:" + id), id);
  await page.reload();
  await expect(page.getByText("Page 2 of 12", { exact: true })).toBeVisible();
  await expect(page.getByText("Biak O’Neil", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.getByRole("radio", { checked: true })).toHaveCount(5);
  const seen: string[] = [];
  for (let n = 1; n <= 12; n++) {
    await expect(
      page.getByText(`Page ${n} of 12`, { exact: true }),
    ).toBeVisible();
    for (const group of await page.locator(".personality-statement").all()) {
      seen.push((await group.getAttribute("id"))!);
      await group.getByRole("radio", { name: "Neutral", exact: true }).check();
    }
    if (n === 12)
      await page
        .getByRole("button", { name: "See my results", exact: true })
        .click();
    else await page.getByRole("button", { name: "Next", exact: true }).click();
  }
  expect(seen).toHaveLength(60);
  expect(new Set(seen).size).toBe(60);
  await expect(
    page.getByRole("heading", { name: "Biak O’Neil’s personality result" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Balanced profile", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("meter")).toHaveCount(5);
  await expect(
    page.getByLabel("Na min midangte muh ding na phal dia."),
  ).not.toBeChecked();
  await expect(
    page.getByRole("button", { name: "Share my result", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Same personality as me" }),
  ).toBeDisabled();
  const a = await api(page, "/attempts/" + id);
  expect(a.shared).toBe(false);
  expect(a.result.code).toBe("XXXX-X");
  expect(a.answers.q01).toBe(0);
  expect(
    a.result.dimensions.every(
      (d: { raw: number; displayPositive: number; displayNegative: number }) =>
        d.raw === 0 && d.displayPositive === 50 && d.displayNegative === 50,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("personality-result.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: "Retake test" }).click();
  await expect(page.locator(".session-player")).toContainText("Biak O’Neil");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByRole("button", { name: "Start test" })).toBeVisible();
});

test("shared results reach another visitor, matches ignore A/T, and owners can retract", async ({
  page,
  browser,
}) => {
  const id = await start(page, "discover-personality", "Shared Name");
  const positives = new Set([
    1, 11, 16, 31, 36, 43, 53, 2, 7, 12, 19, 37, 42, 56, 3, 13, 23, 25, 38, 9,
    24, 30, 39, 44, 55, 58, 5, 15, 35, 40, 59,
  ]);
  const answers = Object.fromEntries(
    Array.from({ length: 60 }, (_, i) => [
      `q${String(i + 1).padStart(2, "0")}`,
      positives.has(i + 1) ? 3 : -3,
    ]),
  );
  await api(page, "/attempts/" + id + "/complete", "POST", { answers });
  await page.reload();
  await expect(page.locator(".result-outcome")).toHaveText("ENTJ-A");
  const second = await browser.newContext();
  const other = await second.newPage();
  try {
    await other.goto(base);
    const b = await api(other, "/quizzes/discover-personality/start", "POST", {
      displayName: "Shared Name",
      gender: "man",
    });
    const alternate = { ...answers };
    for (const n of [5, 15, 35, 40, 59])
      alternate[`q${String(n).padStart(2, "0")}`] = -3;
    for (const n of [10, 20, 27, 45, 47, 50, 60])
      alternate[`q${String(n).padStart(2, "0")}`] = 3;
    await api(other, "/attempts/" + b.id + "/complete", "POST", {
      answers: alternate,
    });
    await other.goto(base + "/play/" + b.id);
    const privateCommunity = await api(
      other,
      "/editions/" + b.edition + "/community",
    );
    expect(
      privateCommunity.entries.some((e: { id: string }) => e.id === b.avatar),
    ).toBe(false);
    await page.getByLabel("Na min midangte muh ding na phal dia.").check();
    await page
      .getByRole("button", { name: "Share my result", exact: true })
      .click();
    await expect(
      page.getByRole("button", { name: "Make private", exact: true }),
    ).toBeVisible();
    await other
      .getByRole("button", { name: "Refresh community results" })
      .click();
    await expect(other.locator(".community-list")).toContainText("ENTJ-A");
    await other.getByRole("button", { name: "Same personality as me" }).click();
    await expect(other.locator(".community-list")).toContainText("ENTJ-A");
    await expect(other.locator(".community-list")).not.toContainText("XXXX-X");
    await expect(other.locator(".result-outcome")).toHaveText("ENTJ-T");
    await other.getByLabel("Na min midangte muh ding na phal dia.").check();
    await other
      .getByRole("button", { name: "Share my result", exact: true })
      .click();
    const shared = await api(
      other,
      "/editions/" + b.edition + "/community?personality=ENTJ",
    );
    const sameNames = shared.entries.filter(
      (e: { name: string }) => e.name === "Shared Name",
    );
    expect(sameNames.length).toBeGreaterThanOrEqual(2);
    expect(new Set(sameNames.map((e: { id: string }) => e.id)).size).toBe(
      sameNames.length,
    );
    await page
      .getByRole("button", { name: "Make private", exact: true })
      .click();
    await expect(
      page.getByLabel("Na min midangte muh ding na phal dia."),
    ).not.toBeChecked();
    const after = await api(other, "/editions/" + b.edition + "/community");
    const owned = await api(page, "/attempts/" + id);
    expect(
      after.entries.some((e: { id: string }) => e.id === owned.avatar),
    ).toBe(false);
    expect(after.entries.some((e: { id: string }) => e.id === b.avatar)).toBe(
      true,
    );
    await other
      .getByRole("button", { name: "Make private", exact: true })
      .click();
  } finally {
    await second.close();
  }
});

test("all activity types reuse the session name and gender and keep old local progress isolated", async ({
  page,
}) => {
  await start(page, "proverbs-demo", "မင်း Zoë O’Neil-Suan");
  for (const slug of ["community-character", "discover-personality"]) {
    await page.goto("/quizzes/" + slug);
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await expect(page.locator(".session-player")).toContainText(
      "မင်း Zoë O’Neil-Suan",
    );
    await page.getByRole("button", { name: /Start (quiz|test)/ }).click();
    await expect(page).toHaveURL(/\/play\//);
    const id = page.url().split("/play/")[1];
    const attempt = await api(page, "/attempts/" + id);
    expect(attempt.displayName).toBe("မင်း Zoë O’Neil-Suan");
    expect(attempt.gender).toBe("woman");
    expect(attempt.shared).toBe(false);
    if (slug === "discover-personality") {
      await page.evaluate(
        (id) =>
          localStorage.setItem(
            "attempt:" + id,
            JSON.stringify({
              edition: "old-edition",
              version: null,
              answers: { q01: "old-option" },
              page: 6,
            }),
          ),
        id,
      );
      await page.reload();
      await expect(
        page.getByText("Page 1 of 12", { exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("radio", { checked: true })).toHaveCount(0);
    }
  }
});

test("optional popup has two gender choices, supports X/Escape, and remembers skipping", async ({
  page,
}, testInfo) => {
  await page.goto("/quizzes/proverbs-demo");
  await page.getByRole("button", { name: "Start quiz", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Na min bang?" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("radio")).toHaveCount(2);
  await expect(
    dialog.getByRole("radio", { name: "Numei", exact: true }),
  ).not.toBeChecked();
  await expect(
    dialog.getByRole("radio", { name: "Pasal", exact: true }),
  ).not.toBeChecked();
  await expect(
    dialog.getByLabel("Na min bang?", { exact: true }),
  ).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(
    dialog.getByRole("button", { name: "Close and play as guest" }),
  ).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  expect(
    await dialog.evaluate((node) => node.contains(document.activeElement)),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("player-popup.png"),
    fullPage: false,
  });
  await dialog.getByLabel("Na min bang?", { exact: true }).fill("Not saved");
  await dialog.getByRole("radio", { name: "Numei", exact: true }).check();
  await dialog.getByRole("button", { name: "Close and play as guest" }).click();
  await expect(page).toHaveURL(/\/play\//);
  let id = page.url().split("/play/")[1];
  let attempt = await api(page, "/attempts/" + id);
  expect(attempt.displayName).toBe("Guest");
  expect(attempt.gender).toBe("undisclosed");
  expect(attempt.shared).toBe(false);
  await page.goto("/quizzes/community-character");
  await page.reload();
  await page.getByRole("button", { name: "Start quiz", exact: true }).click();
  await expect(page).toHaveURL(/\/play\//);
  await expect(page.getByRole("dialog")).not.toBeVisible();
  id = page.url().split("/play/")[1];
  attempt = await api(page, "/attempts/" + id);
  expect(attempt.displayName).toBe("Guest");
  await page.evaluate(() => sessionStorage.clear());
  await page.goto("/quizzes/discover-personality");
  await page.getByRole("button", { name: "Start test", exact: true }).click();
  await expect(dialog).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page).toHaveURL(/\/play\//);
});

test("saved details survive refresh and can be changed without rewriting earlier attempts", async ({
  page,
}) => {
  const first = await start(page, "proverbs-demo", "Original Name");
  await page.goto("/quizzes/community-character");
  await page.reload();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("button", { name: "Change", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Na min bang?" });
  await expect(dialog.getByLabel("Na min bang?", { exact: true })).toHaveValue(
    "Original Name",
  );
  await expect(
    dialog.getByRole("radio", { name: "Numei", exact: true }),
  ).toBeChecked();
  await dialog.getByLabel("Na min bang?", { exact: true }).fill("Changed Name");
  await dialog.getByRole("radio", { name: "Pasal", exact: true }).check();
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByRole("button", { name: "Start quiz", exact: true }).click();
  await expect(page).toHaveURL(/\/play\//);
  const second = await api(page, "/attempts/" + page.url().split("/play/")[1]);
  expect(second.displayName).toBe("Changed Name");
  expect(second.gender).toBe("man");
  const previous = await api(page, "/attempts/" + first);
  expect(previous.displayName).toBe("Original Name");
  expect(previous.gender).toBe("woman");
});
