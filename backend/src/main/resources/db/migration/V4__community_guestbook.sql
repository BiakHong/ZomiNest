create table guestbook_entry (
  id uuid primary key,
  user_id uuid not null references users,
  name varchar(60) not null check (length(trim(name)) between 1 and 60),
  message varchar(1000) not null check (length(trim(message)) between 1 and 1000),
  created_at timestamptz not null default now(),
  hidden boolean not null default false
);
create index guestbook_visible_newest on guestbook_entry(created_at desc, id desc) where not hidden;
