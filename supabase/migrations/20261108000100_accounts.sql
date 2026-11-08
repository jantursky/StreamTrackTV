-- Accounts: one profile per auth user, their settings and the people in their household.
-- Every statement is idempotent so the file can be applied again safely.

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.settings (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  timezone text not null default 'UTC' check (char_length(timezone) between 1 and 64),
  locale text not null default 'en' check (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
  retention_days integer not null default 365 check (retention_days between 7 and 3650),
  digest_enabled boolean not null default false,
  digest_sent_at timestamptz
);

create table if not exists public.household_members (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  created_at timestamptz not null default now(),
  -- Target of composite foreign keys, so rows can only point at members of the same user.
  unique (id, user_id)
);

create index if not exists household_members_user_idx on public.household_members (user_id);

-- Every new auth user gets a profile and default settings.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id) on conflict do nothing;
  insert into public.settings (user_id) values (new.id) on conflict do nothing;
  return new;
end;
$$;

create or replace trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Users who signed up before this migration ran.
insert into public.profiles (id) select id from auth.users on conflict do nothing;
insert into public.settings (user_id) select id from public.profiles on conflict do nothing;

-- Row level security: a user only ever sees and changes their own rows.
alter table public.profiles enable row level security;
alter table public.settings enable row level security;
alter table public.household_members enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
for select to authenticated using (id = (select auth.uid()));

drop policy if exists "settings_select_own" on public.settings;
create policy "settings_select_own" on public.settings
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "settings_update_own" on public.settings;
create policy "settings_update_own" on public.settings
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "members_all_own" on public.household_members;
create policy "members_all_own" on public.household_members
for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- Profiles and settings rows are created by the trigger only; the digest bookkeeping column is server-side.
revoke insert, update, delete on public.profiles from anon, authenticated;
revoke insert, update, delete on public.settings from anon, authenticated;
grant update (timezone, locale, retention_days, digest_enabled) on public.settings to authenticated;
