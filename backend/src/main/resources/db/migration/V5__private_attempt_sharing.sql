-- Consent belongs to an individual completed attempt. Historical attempts start private.
alter table attempt add column display_name varchar(60);
update attempt a set display_name=coalesce(u.name,'Guest') from users u where u.id=a.user_id;
update attempt set display_name='Guest' where display_name is null;
alter table attempt alter column display_name set not null;
alter table attempt add column gender text not null default 'undisclosed';
alter table attempt add column language varchar(12) not null default 'en';
alter table attempt add column current_page integer not null default 0 check(current_page>=0);
alter table attempt add column shared boolean not null default false;
alter table attempt add column public_id uuid not null default gen_random_uuid() unique;
alter table attempt add constraint completed_before_sharing check(not shared or finished_at is not null);
create index attempt_shared_edition on attempt(edition_id,finished_at desc) where shared;
