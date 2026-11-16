# Zomi Nest · monochrome quiz discovery

The supplied Zomi Nest birds-and-nest logo and the user's current browsing brief are the visual authority. Home and discovery are compact, quiz-first surfaces: a short welcome, accessible search, category pills, an activity-type selector, and one vertical feed of horizontal quiz cards. The API's existing featured/publication order is retained. There is no invented sorting, popularity, favorite, or engagement data.

The current interface follows the user's monochrome brief: white #FFFFFF backgrounds, near-black #171717 primary actions/text, soft gray #F4F4F4 secondary surfaces, #E6E6E6 rules, and #666666 secondary text. The decorative header stripe is removed. Selected states use dark fills or borders plus existing checkmarks and labels. The supplied logo remains in its original colors; other UI surfaces and decorative type illustrations are neutral. Original type illustrations depict a book, fictional faces, and an abstract flower, without claims about cultural symbolism or real people.

`frontend/src/feed.css` owns reusable color, spacing, radius, typography, and shadow tokens and the current presentation layer. Locally hosted Bricolage Grotesque is used with lighter heading weights and a clear hierarchy, without external font requests. Cards have 16px/12px radii, quiet borders and shadows, compact actual metadata, and one clear black Play action. Titles and descriptions wrap fully. At 390px, thumbnails are 88px and metadata/actions share a row; full attribution follows below rather than crowding the action. Desktop keeps thumbnail, details, and action side by side. Intermediate navigation collapses before it overflows.

The same surfaces, fields, focus states, restrained hover/press feedback, notices, and reduced-motion behavior carry into detail pages, quiz questions, results, profiles, accounts, contribution forms, and owner tools. Quiz questions retain large answer targets and visible progress. A result summary combines the existing score/outcome/profile with a type illustration. Leaderboards use tabular numerals and highlight the player's server-provided private rank summary without trying to infer identity from a public nickname.

The homepage's existing first-question shortcut remains available as an expandable option below the main feed. Participation remains immediate: private browser results, drafts, contribution history, and optional public nickname scores require no login. Owner access stays protected. Backend workflows, scoring, database structure, permissions, and routes are unchanged by this restyle.

## Supplied logo integration

The supplied 1536×1024 PNG is preserved byte-for-byte at `frontend/public/brand/zomi-nest-logo.png`. Header and footer display its original birds-and-nest illustration and lettering side by side using CSS viewports; neither is redrawn or regenerated. The original white source background sits naturally on the white interface. The source also supplies the browser icon. Product labels, accessible names, page titles, and recovery email branding use “Zomi Nest”. See `frontend/public/brand/README.md` for provenance.

## Verification artifacts

Before/after home screenshots at 390px, 768px, and 1440px are saved under `.local/review/feed/`. The same folder contains task-page, runner, result, actual ranked-score, and synthetic long-text layout checks. These are local review artifacts, not bundled production assets. Long-text checks alter only the browser DOM. Current cultural content remains explicitly labeled development material pending owner review.

## Community guestbook

A guestbook sits below the homepage's quiz browsing and also has a dedicated footer-linked page. It reuses the established palette, form fields, radii, focus states, and notices. The form and visitor list sit side by side on wide screens and stack on phones. Names use simple initial circles, messages preserve line breaks, and dates remain visible; there are no fabricated visits. Refresh, page navigation, author removal, and owner hide/restore are working controls. Review screenshots are in `.local/review/guestbook/`; temporary review entries are removed afterward.

## Monochrome verification

Layout screenshots and checks are in `.local/review/monochrome/`. The October 23 theme pass used isolated browser fixtures based on the existing source files, since no local API/database was running. No stored records were changed. Home, detail, runner, result, board, guestbook, contribution, account, login, profile, and owner screens were inspected at 390px, 768px, and 1440px, including neutral color and overflow checks. This verifies frontend appearance and interactions, not live backend behavior.

## Named attempts and personality results
Every activity detail page has a Start quiz/test button. On the first start in a tab session, it opens a native dialog with an optional name input, Numer/Pasal gender radios, and an upper-right X. X, Escape, and Continue as guest start an unnamed private attempt; Save & start remembers supplied details. Either choice is retained in sessionStorage, scoped to the browser visitor/account, so subsequent activities start directly. A small Change control reopens the dialog for editing. The dialog traps keyboard focus, restores focus on close, and fits phones. The homepage preview uses this same entry point. This replaces the old required name form and pre-start leaderboard opt-in.

The current personality questionnaire uses five statement fieldsets per page and seven native horizontal radio choices with endpoint/neutral labels. A checkmark supplements the selected fill; keyboard focus remains visible. Result screens lead with the attempt's name and a small colored character avatar, followed by the actual code/score and five complementary trait bars where applicable. These restrained avatar colors are the user's explicit exception to the monochrome UI.

Post-result consent starts unchecked and requires an explicit Share my result action. An owner can retract the result inline. Community results follow the personal result, with friendly empty/error states, pagination, refresh, and resolved four-letter personality matching. My results includes saved unfinished attempts as well as completed results.

## Ten-question proverb rounds
The proverb activity shows its actual round length in the feed and detail page: 10 random questions from the full bank, with the existing estimate based on those 10. The runner remains one question at a time and shows progress out of 10; results score out of 10 and offer Play another round. The selected question IDs and their order belong to the saved attempt, so returning to it keeps the same questions. Earlier full-length editions and results remain available through their existing history and archive routes.
