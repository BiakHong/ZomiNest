create table mail_outbox(id uuid primary key, recipient text not null, body text not null, created_at timestamptz not null default now());
