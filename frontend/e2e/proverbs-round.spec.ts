import { test, expect } from "@playwright/test";

test("proverbs uses ten saved random questions, resumes, and offers another round", async ({
  page,
}, testInfo) => {
  await page.goto("/quizzes/proverbs-demo");
  const detail = await (
    await page.request.get("/api/quizzes/proverbs-demo")
  ).json();
  expect(detail.questionsPerAttempt).toBe(10);
  expect(detail.randomQuestions).toBe(true);
  expect(detail.questions.length).toBeGreaterThan(10);
  await expect(
    page.getByText(
      `10 random questions from ${detail.questionBankSize} proverbs · About 3 minutes · Edition ${detail.number}`,
      { exact: true },
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "Start quiz", exact: true }).click();
  await page.getByRole("button", { name: "Close and play as guest" }).click();
  await expect(page).toHaveURL(/\/play\//);
  const id = page.url().split("/play/")[1];
  const attempt = await (await page.request.get("/api/attempts/" + id)).json();
  const ids = attempt.quiz.questions.map((q: { id: string }) => q.id);
  expect(ids).toHaveLength(10);
  expect(new Set(ids).size).toBe(10);
  expect(attempt.quiz.questionBankSize).toBe(detail.questions.length);
  expect(JSON.stringify(attempt.quiz)).not.toContain('"correct":');
  await expect(
    page.getByText("Question 1 of 10", { exact: true }),
  ).toBeVisible();
  await page.locator(".option").first().click();
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await page.reload();
  await expect(
    page.getByText("Question 2 of 10", { exact: true }),
  ).toBeVisible();
  const resumed = await (await page.request.get("/api/attempts/" + id)).json();
  expect(resumed.quiz.questions.map((q: { id: string }) => q.id)).toEqual(ids);
  await page.goto("/results");
  await page
    .locator(".result-row")
    .filter({ hasText: "Resume attempt" })
    .click();
  await expect(
    page.getByText("Question 2 of 10", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.locator(".option.selected")).toHaveCount(1);
  await expect(page.locator(".runner-title")).toBeInViewport();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("ten-question-round.png"),
    fullPage: false,
  });
  for (let question = 1; question <= 10; question++) {
    await expect(
      page.getByText(`Question ${question} of 10`, { exact: true }),
    ).toBeVisible();
    await page.locator(".option").first().click();
    await page
      .getByRole("button", {
        name: question === 10 ? "See my results" : "Next",
        exact: true,
      })
      .click();
  }
  await expect(page.locator(".result-outcome")).toHaveText(/^\d+ \/ 10$/);
  const completed = await (
    await page.request.get("/api/attempts/" + id)
  ).json();
  expect(completed.result.maximum).toBe(10);
  expect(completed.result.explanations).toHaveLength(10);
  expect(completed.shared).toBe(false);
  await page.getByRole("link", { name: "Play another round" }).click();
  await page.getByRole("button", { name: "Start quiz", exact: true }).click();
  await expect(page).toHaveURL(/\/play\//);
  const nextId = page.url().split("/play/")[1];
  expect(nextId).not.toBe(id);
  const next = await (await page.request.get("/api/attempts/" + nextId)).json();
  expect(next.quiz.questions).toHaveLength(10);
  expect(Object.keys(next.answers)).toHaveLength(0);
  expect(
    new Set(next.quiz.questions.map((q: { id: string }) => q.id)).size,
  ).toBe(10);
});
