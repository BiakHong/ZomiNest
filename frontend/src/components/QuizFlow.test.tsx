import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { ActivityResult } from "./QuizFlow";

describe("result privacy and name rendering", () => {
  it("escapes legacy display names as text and starts with sharing unchecked", () => {
    const name = '<script>alert("name")</script>';
    const markup = renderToStaticMarkup(
      <MemoryRouter>
        <ActivityResult
          attempt={{
            id: "private-attempt",
            edition: "edition",
            slug: "quiz",
            displayName: name,
            avatar: "public-avatar",
            shared: false,
          }}
          result={{ type: "KNOWLEDGE", score: 1, maximum: 2, title: "Quiz" }}
        />
      </MemoryRouter>,
    );
    expect(markup).toContain("&lt;script&gt;");
    expect(markup).not.toContain("<script>");
    expect(markup).toContain("Your result is private");
    expect(markup).toContain("Na min midangte muh ding na phal dia.");
    expect(markup).not.toContain('checked=""');
    expect(markup).toMatch(/<button disabled="">/);
  });
});
