# Editorial readiness
Software and editorial readiness are separate.
- [ ] Owner verifies the 58 supplied Zomi proverb questions, meanings, distractors, attributions, and explanations. The current source is owner-provided; importing it does not constitute independent language or source review. Earlier saved editions retain their original 15 English placeholders.
- [ ] Owner supplies six real celebrity names, suitable permissions and reviewed public descriptions. Current six outcomes are fictional community characters, never real Zomi celebrities.
- [ ] Owner reviews 32 original English personality items and all 16 original profile drafts. These are self-reflection content, not a validated assessment.
- [ ] Fluent reviewers supply Zomi UI/content translations and English optional fields.
- [ ] Review accessibility with native language speakers and teens/adults.
Development seeds are idempotent and require DEMO_CONTENT=true. No users or scores are seeded. Disable demo seeding for production; previously inserted demo records must also be unpublished or removed through an explicitly planned editorial migration.
An explicit local `scripts/dev.sh --refresh-demo proverbs-demo` imports changed source content as a new immutable edition. Ordinary restarts do not overwrite existing editions, and historical results remain attached to their original content.

Translation inventory: frontend/src/i18n.ts and frontend/src/copy.ts hold shared actions and extended English fallback copy. All generated interface phrases are centralized; short technical codes/status enums and dynamically composed labels still need a fluent reviewer’s localization pass. No Zomi translations have been invented. Quiz content supports optional English title/description, tags, and question source/English explanation fields. Plain text is used throughout.
