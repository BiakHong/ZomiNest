import { test, expect, type Page } from "@playwright/test";
import { copy } from "../src/copy";
const base = process.env.TEST_BASE_URL || "http://localhost:5173";

async function call(page: Page, path: string, method = "GET", data?: unknown) {
  const session = await (await page.request.get("/api/session")).json();
  return page.request.fetch("/api" + path, {
    method,
    data,
    headers: { "X-CSRF-TOKEN": session.csrf },
  });
}

test("visitors sign the homepage guestbook, read public messages and remove their own", async ({
  page,
  browser,
}, testInfo) => {
  const name = "Guestbook visitor " + Date.now();
  const message =
    'Hello from a visitor.\n<script>throw new Error("Guestbook HTML must stay text")</script>';
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  const guestbook = page.getByRole("region", { name: copy.guestbookTitle });
  await guestbook.getByLabel(copy.guestbookName, { exact: true }).fill(name);
  await guestbook
    .getByLabel(copy.guestbookMessage, { exact: true })
    .fill(message);
  const posted = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/guestbook") &&
      response.request().method() === "POST",
  );
  await guestbook
    .getByRole("button", { name: copy.guestbookSign, exact: true })
    .click();
  const entry = await (await posted).json();
  const stranger = await browser.newContext();
  try {
    await expect(guestbook.getByRole("status")).toContainText(
      copy.guestbookPosted,
    );
    await expect(
      guestbook.getByLabel(copy.guestbookMessage, { exact: true }),
    ).toHaveValue("");
    await expect(
      guestbook.locator(".guestbook-entry").filter({ hasText: name }),
    ).toContainText(message);
    expect(
      (await (await page.request.get("/api/session")).json()).user.visitor,
    ).toBe(true);
    const publicPage = await stranger.newPage();
    await publicPage.goto(base + "/guestbook");
    const publicEntry = publicPage
      .locator(".guestbook-entry")
      .filter({ hasText: name });
    await expect(
      publicEntry.getByRole("heading", { name, exact: true }),
    ).toBeVisible();
    await expect(publicEntry).toContainText(message);
    await expect(
      publicEntry.getByRole("button", { name: copy.guestbookRemove }),
    ).toHaveCount(0);
    await expect(publicEntry.locator("script")).toHaveCount(0);
    await page.goto("/guestbook");
    const ownedEntry = page
      .locator(".guestbook-entry")
      .filter({ hasText: name });
    await expect(
      ownedEntry.getByRole("button", { name: copy.guestbookRemove }),
    ).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBeTruthy();
    await page.screenshot({
      path: testInfo.outputPath("guestbook.png"),
      fullPage: true,
    });
    await ownedEntry
      .getByRole("button", { name: copy.guestbookRemove })
      .click();
    await expect(ownedEntry).toHaveCount(0);
    await publicPage.reload();
    await expect(
      publicPage.locator(".guestbook-entry").filter({ hasText: name }),
    ).toHaveCount(0);
    expect(errors).toEqual([]);
  } finally {
    if (entry.id) await call(page, "/guestbook/" + entry.id, "DELETE");
    await stranger.close();
  }
});

test("owner can hide and restore guestbook messages without changing the original greeting", async ({
  page,
  browser,
}) => {
  const name = "Guestbook moderation " + Date.now();
  await page.goto("/guestbook");
  await page.getByLabel(copy.guestbookName, { exact: true }).waitFor();
  const response = await call(page, "/guestbook", "POST", {
    name,
    message: "A greeting for the community.",
  });
  expect(response.ok()).toBeTruthy();
  const entry = await response.json();
  const owner = await browser.newContext();
  try {
    const op = await owner.newPage();
    await op.goto(base + "/login");
    await op
      .getByLabel("Email", { exact: true })
      .fill(process.env.OWNER_EMAIL || "owner@zomi.test");
    await op
      .getByLabel(/^Password/)
      .fill(process.env.OWNER_PASSWORD || "local-owner-passphrase");
    await op.getByRole("button", { name: "Log in", exact: true }).click();
    await expect(
      op.getByRole("heading", { name: /Dam maw Zomi/ }),
    ).toBeVisible();
    await op.goto(base + "/owner");
    await op.getByText(copy.guestbookModeration, { exact: true }).click();
    const moderation = op
      .locator(".guestbook-moderation .guestbook-entry")
      .filter({ hasText: name });
    await moderation
      .getByRole("button", { name: copy.guestbookHide, exact: true })
      .click();
    await expect(moderation.locator(".tag")).toHaveText(copy.guestbookHidden);
    await page.reload();
    await expect(
      page.locator(".guestbook-entry").filter({ hasText: name }),
    ).toHaveCount(0);
    await moderation
      .getByRole("button", { name: copy.guestbookRestore, exact: true })
      .click();
    await expect(moderation.locator(".tag")).toHaveText(copy.guestbookVisible);
    await page.reload();
    await expect(
      page.locator(".guestbook-entry").filter({ hasText: name }),
    ).toContainText("A greeting for the community.");
  } finally {
    await call(page, "/guestbook/" + entry.id, "DELETE");
    await owner.close();
  }
});
