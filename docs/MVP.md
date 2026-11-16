# Current change request (supersedes login requirements below)
Participation, contributions, private history and public nickname scores must work without login. Only owner administration requires authentication. Browser visitors can opt into public scores and manage drafts/results from their browser space. The visual system should remain minimal but feel more playful and expressive.

The original build brief is retained below for reference; its mandatory member-login rules no longer apply.

Build a complete working MVP for Zomi Nest, a Zomi community quiz platform. Implement the application in this repository, run it, test the important flows, and fix failures. Deliver a usable product with a real backend and database.

WORKING APPROACH

- First inspect the repository and read applicable AGENTS.md instructions. Preserve unrelated files and existing work. Reuse compatible foundations; if the folder contains an unrelated project, create an isolated project subdirectory.
- Make reasonable implementation decisions and record them. Ask only about a genuine blocker that cannot be resolved from this specification or the repository.
- Create docs/MVP.md and docs/BUILD_STATUS.md to preserve the requirements, milestones, decisions, verification results, and remaining work.
- Work through the milestones below without pausing for routine confirmation. Do not stop after creating a plan, scaffold, or attractive frontend.
- Work locally. Public deployment, paid services, commits, and pushes are outside this task.
- If a dependency, credential, tool, or content review is unavailable, continue independent work and report the exact limitation. Do not claim that unrun checks passed.

1. PRODUCT AND STACK

Create one expandable website containing multiple activities, with shared accounts, catalog, review tools, and result history.

Use the configurable title “Zomi Nest” (updated by the user's branding request).

For a new project, use:

- React, TypeScript, and Vite for the frontend.
- Spring Boot with Java 21, Maven, Spring Security, and Spring Data JPA for the backend.
- PostgreSQL and Flyway migrations.
- Cookie-based server sessions.
- Docker Compose for the local application, database, and a development mail catcher.

Select mutually compatible stable dependency versions from official documentation. Follow compatible existing repository conventions rather than replacing a functioning architecture unnecessarily.

Keep this a modular monolith. Separate catalog, accounts, quiz execution, scoring, profiles, contributions, review, and leaderboard concerns. Avoid microservices.

Use a development proxy and a production reverse proxy so frontend and backend can operate under one origin.

Defer audio/song guessing, live multiplayer, Pictionary, weekly leaderboard seasons, global rankings across quizzes, comments, messaging, payments, native mobile apps, and automatic AI publication. Keep the architecture ready for future activity types without implementing those features now.

2. LAUNCH ACTIVITIES AND CONTENT

Support these three reusable quiz templates and prepare these launch collections:

A. Knowledge quiz: Zomi proverbs and meanings

- Target 15 questions.
- Multiple choice, exactly one correct answer per question, one point per correct answer.
- Show answer explanations after completion.
- Include a named leaderboard.

B. Outcome quiz: Which Zomi celebrity am I?

- Target 12 questions and six possible outcomes.
- Each answer contributes configured weights to outcomes.
- Show the highest-scoring outcome and a reviewed description.
- Resolve equal outcome totals using a fixed order stored in the quiz edition.
- No competitive leaderboard.

C. Personality test

- Target 32 original questions, eight per trait across four bipolar traits.
- Five-point response scale, with explicitly configured reverse-scored items.
- Define original trait labels and descriptions.
- Reverse a response r using 6 − r where applicable. Normalize each trait using 25 times its adjusted mean minus 25, producing 0 to 100.
- Values above 50 select one pole; values at or below 50 select the other. The four selections map to one of 16 profiles.
- Mark trait values from 45 through 55 as a close balance.
- Provide all 16 profile pages with an overview, strengths, common difficulties, communication, friendships and relationships, and work preferences.
- Present this as an original self-reflection activity. Use original questions and descriptions.
- No competitive leaderboard.

Content handling matters:

- Do not invent authentic Zomi proverbs, cultural meanings, or personal facts about real celebrities.
- Provide clearly labeled demonstration content for local development. Use fictional outcome names where real descriptions are unavailable, and never present fictional people as actual Zomi celebrities.
- Original English personality questions and profile drafts are acceptable development content.
- Demo seeds must be idempotent, development-only, and distinguishable from owner-approved content. No fabricated users or scores in production.
- Put unverified cultural content and translations in drafts for owner review. Record missing launch content in a content-readiness checklist.
- Finish the working software despite missing editorial input, but distinguish software completion from public-launch readiness.

3. DESIGN AND PUBLIC PAGES

Make the site friendly, modern, and playful for Zomi teens and adults. Use readable typography, colorful activity cards, generous spacing, large touch controls, accessible contrast, and restrained motion. Support reduced motion and keyboard use.

Zomi is the primary intended language. Centralize UI strings and support optional English content fields. Use verified Zomi text where available and English fallback elsewhere. Track translation gaps rather than inventing fluent translations or scattering hardcoded strings.

Implement:

- Home with featured activities, categories, and recently published quizzes.
- Catalog with title/tag search and category/type filters.
- Quiz details with description, question count, estimated duration, optional contributor credit, and start button.
- Quiz runner.
- Results.
- Leaderboard for each scored quiz.
- Public personality profile directory and individual profile pages.
- Registration, login, logout, password recovery, account settings, and My Results.
- Contribution editor and My Submissions.
- Protected owner dashboard.

Use stable shareable URLs and proper page titles. Provide loading, empty, validation, and error states. Every visible action should work.

4. ACCOUNTS AND QUIZ EXECUTION

Visitors can browse, read profiles, view leaderboards, and play as guests.

Members can save results, submit content, and choose to appear under a public display name. Public names may be nicknames; never expose account emails.

For ranked knowledge attempts, require login and public-score opt-in before starting. Explain this before play. Guest attempts remain unranked and cannot be converted into ranked attempts afterward.

The runner must:

- Show one question at a time with progress and back/next controls.
- Allow answer changes until final submission.
- Require all answers before final submission.
- Recover in-progress work after refresh in the same tab.
- Bind each attempt to its starting quiz edition.
- Calculate scores and outcomes on the server.
- Return the same finalized result when a completion request is retried.
- Reject invalid options, incomplete submissions, duplicate answer records, and attempts owned by a different person.
- Bind guest attempts to an opaque guest session; knowing an attempt ID must not grant access.
- Keep drafts, answer keys, and scoring weights out of public pre-completion APIs.

Results show scores with explanations or an outcome/profile as appropriate. Share buttons copy a public quiz/profile link and optional result text, without exposing private attempt URLs or answers.

My Results is private to the account owner. Clear cached private information on logout or account switching.

5. LEADERBOARDS

For knowledge quizzes:

- Maintain a separate leaderboard per published edition.
- Show rank, display name, score, and maximum possible points.
- Show the Top 10 at the end of a quiz, with a paginated full board.
- Show the eligible player’s rank even outside the Top 10.
- Keep one best score per account per edition. A lower or equal later score does not add another entry.
- Equal scores share rank using competition ranking: 1, 1, 3.
- Order tied entries by when their best score was first achieved, then a stable ID. This display ordering must not change their shared rank.
- Keep ranked attempts untimed for this release.
- Prevent duplicate leaderboard entries under concurrent completion requests.
- Let members withdraw from public boards. Owners can hide an entry with a recorded reason. Recalculate visible ranks and prevent an account-level moderation restriction from being bypassed by retrying.
- Do not mix scores from different quiz editions. Provide access to archived boards.

Server validation protects scoring integrity, but repeat attempts can improve through learning. Do not claim this is a cheat-proof competition system.

6. CONTRIBUTION EDITOR AND REVIEW

Members can create:

- An individual knowledge question for an existing collection.
- A complete quiz using any of the three templates.

Support draft saving, question reordering, answer editing, explanations, outcome/profile descriptions, scoring configuration, preview, and server-side validation.

An individual question includes wording, options, correct answer, explanation, category, target collection, optional source, optional English explanation, and optional public contributor credit.

Validate required content and scoring configuration before allowing submission.

Review workflow:

- Full quiz: Draft → Submitted → Published, Changes requested, or Rejected.
- Individual question: Draft → Submitted → Approved in bank, Changes requested, or Rejected; show Published in quiz after its destination edition is published.
- Contributors can withdraw pending submissions.
- Submitted revisions are frozen snapshots. Requested changes produce a new revision and resubmission.
- The owner previews the exact snapshot, requests changes with a note, rejects with a reason, or approves and publishes a full quiz in one transaction.
- Approved single questions join a reusable question bank. The owner selects them, previews a complete destination edition, and publishes it.
- Contributor edits cannot modify the snapshot being reviewed or alter live content.
- Approval must verify that the reviewed revision is still the current submitted revision and has not been withdrawn.
- Record reviewer, timestamp, decision, notes, and publication references.
- Contributors see only their own drafts and review notes.

Owner tools include review filters, feature/unfeature, publish/unpublish, profile editing, question-bank management, display-name moderation, leaderboard moderation, and a basic content-report queue.

Public quiz pages have a report button. Reports go to the owner dashboard. Submission updates appear in My Submissions; email notifications are unnecessary for MVP.

7. PUBLICATION AND PERSISTENCE

Use stable quiz identities with immutable published editions.

Changing questions, correct answers, weights, or result descriptions creates a new edition. Preserve old results and their descriptions. In-progress attempts can finish against their original edition after a normal update. Unpublished content must disappear from public discovery and new starts.

At minimum persist:

- Users, roles, sessions, and password reset tokens.
- Quizzes, editions, questions, options, scoring configuration, and profiles.
- Question-bank entries.
- Attempts, responses, and finalized results.
- Submission revisions, review decisions, and publication history.
- Content reports and moderation records.

Use foreign keys, appropriate uniqueness constraints, and transactions. Server authorization must cover every private and owner operation.

8. SECURITY AND LOCAL OPERATIONS

Use password hashing, HttpOnly cookies, CSRF protection, secure production cookie settings, request validation, and reasonable login/submission rate limits.

Use safe plain text or sanitized supported markup for contributed content. Never trust frontend role checks or submitted score totals.

Provide password recovery through a local mail catcher in development and configurable SMTP in production. Use expiring, single-use tokens and avoid account enumeration. Do not expose reset tokens through production APIs or logs.

Create the owner through an explicit setup command or environment-controlled bootstrap. No hardcoded production credentials.

Include:

- .env.example without real secrets.
- Persistent PostgreSQL storage and health checks.
- Frontend and backend Dockerfiles and Docker Compose.
- Reverse-proxy configuration with documented HTTPS setup.
- Database backup and restore scripts, verified against a temporary database.
- Basic error logging that avoids passwords, tokens, and private answer contents.
- Small aggregate usage counters for starts, completions, share-button use, and pending review time. No third-party analytics service is required.

9. IMPLEMENTATION MILESTONES

Implement and verify in this order:

1. Database, accounts, catalog, knowledge quiz runner, and server-scored results.
2. Leaderboards, display-name controls, result history, and guest/member separation.
3. Outcome and personality templates, profile pages, and development content.
4. Contribution editor, frozen submission revisions, owner review, and publication.
5. Responsive/accessibility polish, local operations, end-to-end verification, and documentation.

Update BUILD_STATUS.md as work progresses. Run relevant checks at each milestone and fix failures before proceeding. Keep documentation proportional to the implementation.

10. REQUIRED VERIFICATION

Use meaningful automated tests for:

- Knowledge scoring and rejection of forged totals or invalid answers.
- Outcome ties and representative results for all 16 personality profiles, including reverse scoring and midpoint rules.
- Best-score replacement, lower/equal retries, shared ranks, a rank outside the Top 10, moderation, and concurrent finalization.
- Guest/member ownership, logout/account switching, and unauthorized draft/owner access.
- Exact-revision approval, stale approval rejection, withdrawal, changes requested, and publication.
- Edition changes preserving previous results and active attempts.
- Password recovery and public-score withdrawal.

Use PostgreSQL for persistence integration checks; do not claim an H2-only test establishes PostgreSQL compatibility.

Use browser end-to-end tests for guest play, ranked play, personality results, submission/review/publication, and privacy. Inspect the UI on desktop and a phone-sized viewport.

Run frontend type checking, linting, tests, and production build; backend tests and build; and a local application smoke test. Report checks that cannot run and the reason.

11. DELIVERABLES AND COMPLETION

Deliver the implemented repository, migrations, development seed data, owner setup, repeatable local startup, automated tests, backup/restore tools, and a README with exact setup and verification commands.

The product must demonstrate:

- A guest finishing a quiz and seeing explanations and existing high scores.
- A member finishing a ranked quiz and seeing their name, best score, and correct rank.
- A personality result opening its complete profile page.
- A contributor submitting content and an owner requesting changes, then approving the exact resubmitted version.
- A new quiz created and published through the editor without code changes.
- A new edition preserving earlier results and separating leaderboard scores.
- No private drafts, emails, answers, reset tokens, or owner operations leaking to other users.

Do not leave core flows behind TODOs, fake network responses, or localStorage-only persistence.

Finish with a concise report of what works, commands actually run and their results, startup instructions, and remaining blockers. Clearly distinguish working demo software, verified production configuration, and cultural content still awaiting owner approval.
