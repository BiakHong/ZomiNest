# Modular monolith
One Spring Boot process, one PostgreSQL database, one same-origin React application.
- AccountsController: registration, server sessions, display names, opt-in, reset flow.
- CatalogController: discovery, edition list, reporting and publication visibility.
- QuizExecutionController: opaque owner-bound attempts, progress, finalization and private history.
- Content: validated immutable content records, template scoring and public redaction.
- catalog/EditionRepository: Spring Data JPA reads of immutable published editions.
- LeaderboardController: PostgreSQL window ranking and owner entry moderation.
- ProfilesController: public directory and owner editing.
- ContributionsController: member drafts, immutable revisions and withdrawals.
- ReviewController: exact-revision decisions, question bank, transactional publication and reports.
- Api: shared persistence/authorization/serialization support and publication primitive. Controllers inherit these stateless helpers; each module owns its endpoints. JDBC expresses locking/upserts/window functions alongside JPA edition reads.
- MailOutbox: durable queued SMTP delivery with generic recovery responses, no token logs.
- GuestbookController: public paginated visitor messages, no-login posting, author removal, and protected owner visibility controls. V4 adds the guestbook table without changing quiz or contribution data.

Edition contents use validated JSON documents in text columns. Questions, options, profile descriptions and configuration are stored together as an immutable aggregate; attempts and submissions reference aggregate IDs with foreign keys. Results store their description snapshots. This avoids mutable live-question joins. Adding another activity means adding a template schema, scorer and runner display, while retaining accounts/catalog/review.

Bootstrap sources are insert-only during normal startup. An explicit `app.demo-refresh` names one supported source while `app.demo=true`; changed content is validated and published as a new edition under the quiz row lock. Semantic equality prevents duplicate editions, visibility is preserved, and other collections and earlier edition content remain untouched. `scripts/dev.sh --refresh-demo <source>` supplies the flag for local development.

Finalization locks the attempt row; response retry returns the stored result. The unique (edition,user) leaderboard constraint and conditional upsert prevent duplicates and keep the earliest achievement time for equal/lower retries. Hidden/restricted players are filtered before rank() is evaluated; rank considers only score while display ties use achievement time and UUID.

Contribution edits cannot touch submitted/published rows. Each submission copies content into a unique immutable revision. Approval locks the submission and verifies status+revision before reading that snapshot. Publication locks the quiz identity and allocates an edition number inside the same transaction.

Recovery stores only token hashes in the token table. The short-lived mail outbox necessarily contains the email link until successful delivery or expiry; it is never returned by APIs. Reset deletes sessions and all tokens for the account. HTML is rendered as plain text by React.

## No-login participation
`users.visitor=true` represents a real on-demand browser visitor with NULL email/password, not an automatically registered account or synthetic seed. `visitorId` is a server-only session attribute; `uid` still identifies an authenticated optional account and is required for owner privileges. Effective ownership uses the authenticated ID when present and otherwise the visitor ID. Contributions, results and ranked attempts accept either identity. Visitor creation uses a separate transaction so an invalid request cannot roll back the identity while retaining its cookie. Persistent session cookies remember the browser for up to 30 days. Clearing a cookie does not transfer ownership or expose another browser’s data; it creates a new identity.

## Guestbook
`guestbook_entry` stores an immutable name/message snapshot, owning user, creation time, and owner-controlled hidden flag. Public reads filter hidden entries and restricted users, return 10 messages per page ordered by creation time and UUID, and expose only the entry ID, name, text, timestamp, and whether the current browser owns it. Internal user IDs, credentials, and moderation flags are excluded from public payloads. React renders all content as text, preserving line breaks without accepting HTML. Author deletion checks server-side ownership; hide/restore and the moderation feed require an authenticated owner. Publishing a guestbook message does not change profile names or public-score opt-in.
