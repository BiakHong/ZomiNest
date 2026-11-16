import { test, expect, type Page } from "@playwright/test";
const base = process.env.TEST_BASE_URL || "http://localhost:5173";
async function startQuiz(page: Page, name = "Visitor O’Neil") {
  await page.getByRole("button", { name: /Start (quiz|test)/ }).click();
  const dialog = page.getByRole("dialog", { name: "Na min bang?" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Na min bang?", { exact: true }).fill(name);
  await dialog.getByRole("button", { name: "Save & start" }).click();
}

async function call(page: Page, path: string, method = "GET", data?: unknown) {
  const s = await page.request.get("/api/session");
  const session = await s.json();
  return page.request.fetch("/api" + path, {
    method,
    data,
    headers: { "X-CSRF-TOKEN": session.csrf },
  });
}
async function register(page: Page) {
  const name = "Curious " + Date.now();
  await page.goto("/register");
  await page.getByLabel("Public display name").fill(name);
  await page
    .getByLabel("Email", { exact: true })
    .fill("member" + Date.now() + "@example.test");
  await page.getByLabel(/^Password/).fill("a-long-local-test-password");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(
    page.getByRole("heading", { name: /Dam maw Zomi/ }),
  ).toBeVisible();
  return name;
}
async function finish(page: Page, count: number) {
  for (let i = 0; i < count; i++) {
    await expect(
      page.getByText("Question " + (i + 1) + " of " + count, { exact: true }),
    ).toBeVisible();
    await page.locator(".option").first().click();
    await page
      .getByRole("button", {
        name: i === count - 1 ? "See my results" : "Next",
        exact: true,
      })
      .click();
  }
}
test("guest quiz, refresh recovery, keys hidden and privacy", async ({
  page,
  browser,
}, testInfo) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Dam maw Zomi/ }),
  ).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: testInfo.outputPath("home.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  const quizzes = await (await page.request.get("/api/quizzes")).json();
  const quiz = quizzes.find(
    (q: { slug: string }) => q.slug === "proverbs-demo",
  );
  const questionCount = quiz.questionsPerAttempt || quiz.questions.length;
  expect(JSON.stringify(quizzes)).not.toContain('"correct":');
  expect(JSON.stringify(quizzes)).not.toContain('"weights":');
  await page.goto("/quizzes/proverbs-demo");
  await startQuiz(page);
  await expect(
    page.getByText("Question 1 of " + questionCount, { exact: true }),
  ).toBeVisible();
  const url = page.url();
  await page.locator(".option").first().click();
  await page.reload();
  await expect(page.locator(".option.selected")).toHaveCount(1);
  const stranger = await browser.newContext();
  const a = await stranger.request.get(url.replace("/play/", "/api/attempts/"));
  expect(a.status()).toBe(403);
  await stranger.close();
  await finish(page, questionCount);
  await expect(page.locator(".result-outcome")).toHaveText(
    new RegExp("^\\d+ / " + questionCount + "$"),
  );
  await expect(
    page.getByRole("heading", { name: "Community results" }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("results.png"),
    fullPage: false,
  });
});
test("nickname ranked score, private browser history and withdrawal without login", async ({
  page,
  browser,
}) => {
  const name = "Open player " + Date.now();
  const quiz = await (
    await page.request.get("/api/quizzes/proverbs-demo")
  ).json();
  await page.goto("/quizzes/proverbs-demo");
  await startQuiz(page, name);
  await finish(page, quiz.questionsPerAttempt || quiz.questions.length);
  await expect(
    page.getByRole("heading", { name: "Your result is private" }),
  ).toBeVisible();
  await page.getByLabel("Na min midangte muh ding na phal dia.").check();
  await page
    .getByRole("button", { name: "Share my result", exact: true })
    .click();
  await expect(page.locator(".community-list")).toContainText(name);
  await page.goto("/results");
  await expect(page.locator(".result-row")).toHaveCount(1);
  const session = await (await page.request.get("/api/session")).json();
  expect(session.user.visitor).toBe(true);
  expect(session.user).not.toHaveProperty("email");
  const cookies = await page.context().cookies();
  expect(cookies.find((c) => c.name === "SESSION")?.expires).toBeGreaterThan(
    Date.now() / 1000,
  );
  const returning = await browser.newContext({
    storageState: await page.context().storageState(),
  });
  const rp = await returning.newPage();
  await rp.goto(base + "/results");
  await expect(rp.locator(".result-row")).toHaveCount(1);
  await returning.close();
  await page.goto("/account");
  await page
    .getByRole("button", { name: "Make all my results private" })
    .click();
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: "All your results are now private" }),
  ).toBeVisible();
  await page.goto("/boards/proverbs-demo");
  await expect(page.locator("td").filter({ hasText: name })).toHaveCount(0);
  await call(page, "/logout", "POST");
  const privateResults = await page.request.get("/api/results");
  expect(privateResults.status()).toBe(200);
  expect(await privateResults.json()).toEqual([]);
});
test("contributor revision, owner changes request and exact publication", async ({
  page,
  browser,
}) => {
  const title = "An open contribution " + Date.now();
  await page.goto("/contribute");
  await page.getByLabel("Title", { exact: true }).fill(title);
  await page
    .getByRole("textbox", { name: "Description", exact: true })
    .fill("A complete community contribution created through the editor.");
  await page
    .getByLabel("Question wording")
    .fill("Which action helps a new volunteer?");
  await page.getByLabel("Answer 1", { exact: true }).fill("Explain the task");
  await page
    .getByLabel("Answer 2", { exact: true })
    .fill("Leave expectations unclear");
  await page
    .getByLabel("Answer explanation")
    .fill(
      "Clear expectations make it easier to participate. Original English demonstration.",
    );
  await page.getByRole("button", { name: "Submit for review" }).click();
  await expect(
    page.getByRole("heading", { name: "Ideas you’ve shared" }),
  ).toBeVisible();
  const stranger = await browser.newContext();
  const hidden = await stranger.request.get(base + "/api/submissions");
  expect(await hidden.json()).toEqual([]);
  expect((await stranger.request.get(base + "/api/owner")).status()).toBe(401);
  await stranger.close();
  const owner = await browser.newContext();
  const op = await owner.newPage();
  await op.goto(base + "/login");
  await op
    .getByLabel("Email", { exact: true })
    .fill(process.env.OWNER_EMAIL || "owner@zomi.test");
  await op
    .getByLabel(/^Password/)
    .fill(process.env.OWNER_PASSWORD || "local-owner-passphrase");
  await op.getByRole("button", { name: "Log in", exact: true }).click();
  await expect(op.getByRole("heading", { name: /Dam maw Zomi/ })).toBeVisible();
  await op.goto(base + "/owner");
  let review = op.locator(".submission").filter({ hasText: title });
  await review.getByLabel(/Review note/).fill("Please add a welcoming phrase.");
  await review.getByRole("button", { name: "Request changes" }).click();
  await expect(review).toHaveCount(0);
  await page.reload();
  await expect(
    page.getByText("Owner note: Please add a welcoming phrase."),
  ).toBeVisible();
  await page.getByRole("link", { name: "Edit a new revision" }).click();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue(title);
  await page
    .getByRole("textbox", { name: "Description", exact: true })
    .fill("Welcome! A complete original question for our community.");
  await page.getByRole("button", { name: "Submit for review" }).click();
  await expect(
    page.getByRole("heading", { name: "Ideas you’ve shared" }),
  ).toBeVisible();
  await op.reload();
  review = op.locator(".submission").filter({ hasText: title });
  await review.getByText(/Preview exact revision/).click();
  await expect(
    review.getByText(
      "Welcome! A complete original question for our community.",
    ),
  ).toBeVisible();
  await review.getByRole("button", { name: "Approve & publish" }).click();
  await expect(review).toHaveCount(0);
  await page.goto("/explore");
  await page.getByRole("link").filter({ hasText: title }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await startQuiz(page);
  await finish(page, 1);
  await expect(page.getByRole("heading", { name: "1 / 1" })).toBeVisible();
  await owner.close();
});

test("homepage preview confirms a name before starting an attempt", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByText("Or, try a quick question", { exact: true }).click();
  await page.locator(".sample-answer").first().click();
  await expect(
    page.getByRole("button", { name: "Start quiz", exact: true }),
  ).toBeVisible();
  const before = await (await page.request.get("/api/results")).json();
  expect(before).toHaveLength(0);
  await startQuiz(page);
  await expect(page.locator(".option.selected")).toHaveCount(0);
});
test("discovery search, category and type filters keep real quiz navigation", async ({
  page,
}) => {
  await page.goto("/explore");
  const quizzes = await (await page.request.get("/api/quizzes")).json();
  const quiz = quizzes.find(
    (q: { slug: string }) => q.slug === "proverbs-demo",
  );
  const rows = page.locator(".quiz-row");
  await expect(rows).toHaveCount(quizzes.length);
  const search = page.getByRole("searchbox");
  await search.fill(quiz.title);
  await expect(rows).toHaveCount(1);
  await expect(rows.first().getByRole("heading")).toHaveText(quiz.title);
  await page.getByRole("button", { name: "Clear search", exact: true }).click();
  await expect(rows).toHaveCount(quizzes.length);
  await page
    .getByRole("group", { name: "Category" })
    .getByRole("button", { name: quiz.category, exact: true })
    .click();
  await expect(rows).toHaveCount(
    quizzes.filter((q: { category: string }) => q.category === quiz.category)
      .length,
  );
  await expect(
    page.getByRole("button", { name: quiz.category, exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page
    .getByRole("button", { name: "All activities", exact: true })
    .click();
  await page
    .getByLabel("Activity type", { exact: true })
    .selectOption("PERSONALITY");
  await expect(rows).toHaveCount(
    quizzes.filter((q: { type: string }) => q.type === "PERSONALITY").length,
  );
  await search.fill("no-quiz-matches-this-search");
  await expect(
    page.getByRole("heading", { name: "Nothing quite like that yet." }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Clear filters", exact: true })
    .click();
  await expect(search).toHaveValue("");
  await expect(page.getByLabel("Activity type", { exact: true })).toHaveValue(
    "",
  );
  await expect(rows).toHaveCount(quizzes.length);
  await page.goto("/explore?category=" + encodeURIComponent(quiz.category));
  await expect(
    page.getByRole("button", { name: quiz.category, exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page
    .getByRole("link", { name: "Play quiz: " + quiz.title, exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: quiz.title, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /Start (quiz|test)/ }),
  ).toBeVisible();
});
test("optional existing accounts still work", async ({ page }) => {
  await register(page);
  const s = await (await page.request.get("/api/session")).json();
  expect(s.user.visitor).toBe(false);
  await page.goto("/contribute");
  await expect(
    page.getByRole("heading", { name: "Bring something to the table." }),
  ).toBeVisible();
});
