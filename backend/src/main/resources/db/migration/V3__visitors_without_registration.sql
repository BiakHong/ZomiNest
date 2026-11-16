-- Real visitors are created on demand, with no email, password or registration.
alter table users alter column email drop not null;
alter table users alter column password drop not null;
alter table users add column visitor boolean not null default false;
alter table users add constraint registered_credentials check (visitor or (email is not null and password is not null));
