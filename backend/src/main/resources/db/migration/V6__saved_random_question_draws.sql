-- NULL preserves the complete original questionnaire for all earlier attempts.
-- New random rounds store their ordered, unique question IDs once at start.
alter table attempt add column question_ids text;
