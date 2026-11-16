# Zomi Nest
A local MVP for knowledge quizzes, fictional outcome quizzes, and original personality self-reflection, with no-login participation, server scoring, edition-specific leaderboards, contributions and protected owner review.

## Start with Docker
Requires Docker Compose. No paid services.

```sh
cp .env.example .env
# Edit DATABASE_PASSWORD, OWNER_EMAIL and OWNER_PASSWORD (12+ characters).
docker compose up --build
```

Open http://localhost:8088; development mail is at http://localhost:8025. The owner is created only when both owner environment values are supplied and the email does not already exist. Subsequent restarts never overwrite passwords. Remove bootstrap credentials after first setup. There are no production default credentials.

DEMO_CONTENT defaults to false in the app. The example environment enables it explicitly. Development sources create no accounts or scores. The current proverbs bank contains 58 owner-supplied Zomi questions, with 10 randomly selected for each new round; the other sources include 12 questions with six **fictional** character outcomes and the 60-statement “Discover your personality” self-reflection questionnaire. Earlier databases can still contain the initial 15 English placeholder questions until explicitly refreshed. Editorial review remains separate from importing content: see [content readiness](docs/CONTENT_READINESS.md).

## Native development
Requires Java 21, Maven 3.9+, Node 22.12+ (tested with Node 24), PostgreSQL 16 and an SMTP catcher. Example for a fresh local database:

```sh
createdb -h localhost -p 5433 -U zomi zomi
export JAVA_HOME=/path/to/jdk-21
export DATABASE_URL=jdbc:postgresql://localhost:5433/zomi
export DATABASE_USER=zomi DATABASE_PASSWORD=your-local-password
export DEMO_CONTENT=true
export OWNER_EMAIL=your-owner@example.test
export OWNER_PASSWORD='your-private-owner-passphrase'
export BASE_URL=http://localhost:5173
npm --prefix frontend ci
scripts/dev.sh
```

Use PostgreSQL's `initdb` and `pg_ctl` if no server exists, or expose the Compose database on loopback in a local override. Vite runs at http://localhost:5173 and proxies `/api` to Spring Boot at port 8080. For a native mail catcher, run `python3 scripts/mail-catcher.py` in another terminal and open http://localhost:8025. Mail defaults to localhost:1025. Flyway applies migrations automatically. The working title can be configured through frontend `VITE_SITE_TITLE` before building.

Saving frontend files updates the browser automatically. After backend or environment changes, press Control+C in the terminal running `scripts/dev.sh`, then run it again. The script checks that ports 8080 and 5173 are free before starting; Vite uses a strict port so it cannot silently move the frontend to 5174. If another copy is running, stop it in its original terminal first. `lsof -nP -iTCP:8080 -iTCP:5173 -sTCP:LISTEN` identifies listeners when a port conflict occurs.

## Updating quiz JSON files
The website reads published editions from PostgreSQL. Files in `backend/src/main/resources/demo/` seed new collections; saving a file or restarting normally does not replace an existing edition.

After editing `proverbs-demo.json`, stop the current app with Control+C and run:

```sh
./scripts/dev.sh --refresh-demo proverbs-demo
```

This validates the file and imports changed content as a new edition. Unchanged content creates no duplicate edition. Earlier attempts, results, and leaderboard editions stay intact; an unpublished collection stays unpublished. Open the activity page and **start a new attempt** to use the new questions. Refreshing an old attempt keeps its original questions.

The same option accepts `community-character` or `discover-personality`. This is an explicit local development import with `DEMO_CONTENT=true`; ordinary restarts and production defaults leave published content alone. Direct backend launches can use `DEMO_CONTENT=true DEMO_REFRESH=proverbs-demo` instead. In Docker, rebuild the backend after source changes and explicitly pass `DEMO_REFRESH` to that container when importing.

## Verify
Use a separate PostgreSQL database, never the application database, for backend tests. Test fixtures create real records in that database.

```sh
createdb -h localhost -p 5433 -U zomi zomi_test
TEST_DATABASE_URL=jdbc:postgresql://localhost:5433/zomi_test mvn -f backend/pom.xml test
mvn -f backend/pom.xml package
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run test
npm --prefix frontend run build
cd frontend
npx playwright install chromium
# App and database must be running with DEMO_CONTENT=true and the owner below.
OWNER_EMAIL=your-owner@example.test OWNER_PASSWORD='your-private-owner-passphrase' npm run test:e2e
```

For an existing Chromium binary, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/absolute/path/to/chromium`. Tests use real APIs and database persistence; no network mocks. Chromium checks both 1440px desktop and iPhone 13-sized touch viewports. Screenshots/traces are under frontend/test-results (ignored by git). Backend tests use PostgreSQL locking, migrations and SQL ranking; no H2 dependency is used.

## Walkthrough
- Select Start quiz or Start test. The first start in a tab session opens an optional name popup with an X at the upper-right. Gender uses only Numei (female) and Pasal (male) radio buttons and can be left unselected. Save & start remembers your details; the X, Escape, or Continue as guest skips them. Later quizzes start immediately using that session choice. Change lets you update it. Gender is never public or used in scoring; no registration is required.
- Complete the activity to see your named, private result. To publish it, check “Na min midangte muh ding na phal dia.” and select **Share my result**. **Make private** removes it from community results and boards. My space also offers **Make all my results private**. Entering a name, finishing, or enabling an older account setting never publishes a result.
- Take **Discover your personality**: 60 statements, five per page, seven horizontal choices from −3 to +3. All-neutral answers produce a balanced profile. Completed results show five independent dimensions, a four-letter type with A/T/X suffix, explanations, and optional community matching.
- Leave an unfinished activity and use **My results** to resume it. Its original name, answers, page, edition, and questionnaire version remain attached to the attempt.
- Open Contribute immediately, without an email or password. Save drafts or submit. On the owner dashboard preview the submitted revision and request changes with a note. The contributor edits a new revision and resubmits; approve and publish that exact revision.
- To publish an updated edition, choose an existing destination collection in the editor. Earlier attempts/results remain attached to their old edition; archived boards are available from the leaderboard edition selector.
- Individual questions are approved into a question bank. Select bank entries and preview the destination edition before publishing.

## Participation without login
Playing, explicitly shared results, private attempt history, contributions and revision tracking work without registration. A random server-side visitor identity is saved in a persistent HttpOnly session cookie; drafts, scores and results are stored in PostgreSQL, not in browser-only storage. The browser remembers this space for up to 30 days. Clearing cookies, changing browsers, or letting the cookie expire starts a new space; there is no email-based recovery for an anonymous visitor. Optional existing accounts still work; owner administration requires authenticated owner login.

Best-score uniqueness applies per browser visitor identity or optional account, not per verified person. Nicknames are unverified and need not be unique. Clearing cookies can create a new identity, so boards are casual community rankings. Owners can moderate entries and restrict the current visitor identity. Per-result consent, server scoring, CSRF and private attempt/draft ownership remain enforced. Public community payloads contain only a public avatar identifier, display name, and the relevant score/outcome/code; answers and gender remain private.

## Ten-question proverb rounds
“Paunak leh a khiatna” draws 10 unique questions from its published bank for every new attempt. The draw and its order are saved in PostgreSQL, so refresh, Back/Next, and resume keep the same questions. Completing the round scores only those 10 answers and shows 10 explanations. Play another round starts a new draw, using the player details already remembered for the tab session.

On backend restart, V6 adds saved draws. An existing proverb collection is upgraded to a new edition with the round-size setting while preserving its current question text, answer keys, credits and publication controls. Older attempts and results remain attached to their earlier editions and can still be resumed or viewed. Archived full-quiz scores remain on their earlier boards; new rounds use the new edition’s board and community list. Begin a new attempt to use the 10-question flow.

The source setting is `questionsPerAttempt: 10` in `backend/src/main/resources/demo/proverbs-demo.json`. Other quizzes and tests keep their current question counts.

## Personality versions and sharing
The current questionnaire has its own quiz slug, `discover-personality`, and scoring version, `zomi-personality-60-v1`. Stable IDs `q01`–`q60` identify numeric answers; neutral `0` is distinct from missing. Scoring uses the supplied fixed direction key and equal weights, independently for each of five dimensions. Raw zero stays balanced (`X`); percentages are descriptive, not probabilities or population rankings. This is Zomi Nest’s proposed self-reflection scoring system.

On the next backend startup, an installed Community Rhythm sample is replaced in discovery; old entry links lead to the new questionnaire. Its original editions, attempts, and stored results are retained and use their historical scorer. New answers cannot populate old attempts or vice versa. Existing attempts all start private under migration V5, including previously ranked scores. Publishing now requires the owner’s explicit action after completion.

Community lists are scoped to an exact edition. Personality matching compares the four resolved main letters and ignores the A/T suffix; the suffix remains visible. Profiles with an `X` among the first four letters appear only in the all-results list. The browser/account session owns an attempt; display names are unverified, need not be unique, and grant no access. Attempt name snapshots and sharing status live in PostgreSQL. Optional player details and the choice to skip are remembered in sessionStorage for the current tab session, scoped to the current visitor/account; logout clears them. A new tab session asks again. Unnamed attempts use Guest, still stay private, and use the same ownership checks. Progress also has an edition/version-checked browser cache to recover unsent selections.

For isolated browser checks, `TEST_BASE_URL` can point Playwright to a temporary frontend, and `API_PROXY_TARGET` can point Vite to its temporary backend. Default development ports remain 5173 and 8080.

## Community guestbook
The homepage includes a public guestbook, also available at `/guestbook` through the footer. Visitors can post a name (up to 60 characters) and a plain-text message (up to 1,000 characters) without registering. Messages appear immediately, newest first, with visit dates, refresh, and pagination. PostgreSQL stores entries so other visitors and returning browsers can read them.

The posting browser or account can remove its own messages. Owner tools include a **Guestbook messages** section with hide/restore controls; entries from restricted visitors are excluded from the public list. Guestbook names are snapshots and do not change the visitor's leaderboard nickname or score preferences. Names are unverified. Clearing browser cookies loses anonymous removal access. CSRF, server validation, and per-process posting limits (3 per visitor and 12 per remote address per minute) apply.

## Security and operations
Cookies are HttpOnly and SameSite=Lax. CSRF is required for mutations. Production must set COOKIE_SECURE=true behind HTTPS. Backend controllers check private browser/account ownership and owner roles independently of the UI. Answers, weights, drafts and emails are excluded from public quiz payloads. Passwords are BCrypt hashes; reset tokens expire after 30 minutes, are single use and invalidate account sessions. Recovery responses do not enumerate accounts; SMTP uses a durable expiring outbox.

For HTTPS, place a TLS-terminating proxy (Caddy/nginx) in front of loopback port 8088, configure its certificate for your domain, set BASE_URL to the HTTPS URL and COOKIE_SECURE=true, and configure authenticated SMTP with SMTP_TLS=true. Keep the application/database inaccessible directly from the internet. The included nginx configuration proxies `/api` under one origin and sets basic response security headers. This configuration has **not been deployed or validated with real TLS or production SMTP**.

Aggregate counters track starts, completions and share use; the dashboard computes average pending review hours. Rate limits are per application process; this release is a single-instance monolith. Repeat play can improve results through learning; this is not a cheat-proof contest.

Backup uses PostgreSQL client tools and environment variables, with a new database required for restore:

```sh
PGHOST=localhost PGPORT=5433 PGUSER=zomi PGDATABASE=zomi scripts/backup.sh backup.dump
PGHOST=localhost PGPORT=5433 PGUSER=zomi scripts/restore.sh backup.dump new_empty_database
scripts/verify-backup.sh
```

For a Compose-only database, run `docker compose exec -T db pg_dump -U zomi -d zomi -Fc > backup.dump`; restore into a separate database with pg_restore. Store archives securely: they contain private account and answer data. The verification script restores into a disposable database, compares core table counts, then removes it.

See [build status](docs/BUILD_STATUS.md), [requirements](docs/MVP.md) and [architecture](docs/ARCHITECTURE.md) for actual verification results and limits. No deployment, commits, or pushes are part of this task.
