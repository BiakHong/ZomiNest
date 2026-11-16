# Zomi Nest
## Platform
Web, responsive and keyboard accessible.
## Stack
User specified React/TypeScript/Vite, Spring Boot Java 21, PostgreSQL/Flyway, server sessions, Docker Compose.
## Product truth
Teens and adults browse, play and contribute knowledge, outcome and original personality activities. Owner reviews immutable submissions. Public editions preserve past results. Zomi is intended primary language; English fallback pending verified translations. Development content must be labeled. Local implementation only; no deployment, commits or paid services.
## Decisions delegated by brief
Friendly daylight interface; configuration-based title. No additional interview because the user explicitly asks for reasonable implementation decisions without routine confirmation.

## Updated direction — user request
Participation is open without login: contribute, save private browser results and join public boards under a nickname. Owner login remains protected. Browser identity is remembered by this browser for up to 30 days; it is not verified identity or cross-device recovery. Current visual direction: elegant, clean, modern, and minimal, using white backgrounds with black actions and neutral gray surfaces.

## Brand commitment
Use the user's supplied Zomi Nest birds-and-nest illustration and wordmark as the logo. The current original asset is frontend/public/brand/zomi-nest-logo.png; preserve its artwork and original colors. Use “Zomi Nest” throughout the app and recovery emails. The interface surrounding the logo is now monochrome; no-login participation remains.

## Current browsing brief
Frontend-only restyle: a compact welcome and search lead directly to one vertical feed of horizontal illustrated quiz cards. Category and activity-type filters retain real data mappings; the server's existing ordering remains. The latest theme request replaces cream/red/green UI accents and stripe decoration with white, black, and neutral gray. Keep long titles/descriptions visible, retain all routes and workflows, and carry the presentation system into task pages. No backend, scoring, database, permission, or publication-rule changes.

## Public guestbook addition
Visitors can leave a public name and message without login, and read who has stopped by. A homepage section and `/guestbook` page share the same persisted messages. This feature adds its own database migration and API; quiz scoring, contribution review, and account behavior stay intact. Authors can remove their messages from the same browser or account, and owners can hide or restore entries.
