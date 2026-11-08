-- Devices (paired TVs), the app catalog per user and the hourly buckets the TVs sync.

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  member_id uuid,
  name text not null check (char_length(name) between 1 and 60),
  model text check (char_length(model) <= 80),
  -- SHA-256 of the device token; the token itself is never stored. Null until the TV collects it.
  token_hash text unique check (token_hash ~ '^[0-9a-f]{64}$'),
  timezone text check (char_length(timezone) <= 64),
  last_seen_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  unique (id, user_id),
  foreign key (member_id, user_id) references public.household_members (id, user_id) on delete set null (member_id)
);

create index if not exists devices_user_idx on public.devices (user_id);

-- Revoking is final and drops the token hash, so a leaked token is useless afterwards.
create or replace function public.devices_guard_revocation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if old.revoked_at is not null then
    new.revoked_at := old.revoked_at;
  end if;
  if new.revoked_at is not null then
    new.token_hash := null;
  end if;
  return new;
end;
$$;

create or replace trigger devices_guard_revocation
before update on public.devices
for each row execute function public.devices_guard_revocation();

create table if not exists public.apps (
  user_id uuid not null references public.profiles (id) on delete cascade,
  package_name text not null check (char_length(package_name) between 1 and 255),
  label text check (char_length(label) between 1 and 120),
  category text check (category in ('streaming', 'video', 'music', 'live-tv', 'games', 'kids', 'system', 'other')),
  primary key (user_id, package_name)
);

-- Primary keys match bucketKey() of the schema package per device, which makes re-sending idempotent.
create table if not exists public.usage_buckets (
  device_id uuid not null references public.devices (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  hour smallint not null check (hour between 0 and 23),
  package_name text not null check (char_length(package_name) between 1 and 255),
  foreground_ms integer not null check (foreground_ms between 0 and 3600000),
  primary key (device_id, day, hour, package_name)
);

create table if not exists public.key_buckets (
  device_id uuid not null references public.devices (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  hour smallint not null check (hour between 0 and 23),
  key_class text not null check (key_class in ('dpad', 'select', 'back', 'home', 'media', 'volume', 'other')),
  count integer not null check (count between 0 and 1000000),
  primary key (device_id, day, hour, key_class)
);

create table if not exists public.screen_buckets (
  device_id uuid not null references public.devices (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  hour smallint not null check (hour between 0 and 23),
  screen_on_ms integer not null check (screen_on_ms between 0 and 3600000),
  primary key (device_id, day, hour)
);

-- Dashboard queries read a day range for one user; device filters use the primary key prefix.
create index if not exists usage_buckets_user_day_idx on public.usage_buckets (user_id, day);
create index if not exists key_buckets_user_day_idx on public.key_buckets (user_id, day);
create index if not exists screen_buckets_user_day_idx on public.screen_buckets (user_id, day);

alter table public.devices enable row level security;
alter table public.apps enable row level security;
alter table public.usage_buckets enable row level security;
alter table public.key_buckets enable row level security;
alter table public.screen_buckets enable row level security;

drop policy if exists "devices_select_own" on public.devices;
create policy "devices_select_own" on public.devices
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "devices_update_own" on public.devices;
create policy "devices_update_own" on public.devices
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "devices_delete_own" on public.devices;
create policy "devices_delete_own" on public.devices
for delete to authenticated using (user_id = (select auth.uid()));

drop policy if exists "apps_select_own" on public.apps;
create policy "apps_select_own" on public.apps
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "apps_update_own" on public.apps;
create policy "apps_update_own" on public.apps
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "usage_buckets_select_own" on public.usage_buckets;
create policy "usage_buckets_select_own" on public.usage_buckets
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "usage_buckets_delete_own" on public.usage_buckets;
create policy "usage_buckets_delete_own" on public.usage_buckets
for delete to authenticated using (user_id = (select auth.uid()));

drop policy if exists "key_buckets_select_own" on public.key_buckets;
create policy "key_buckets_select_own" on public.key_buckets
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "key_buckets_delete_own" on public.key_buckets;
create policy "key_buckets_delete_own" on public.key_buckets
for delete to authenticated using (user_id = (select auth.uid()));

drop policy if exists "screen_buckets_select_own" on public.screen_buckets;
create policy "screen_buckets_select_own" on public.screen_buckets
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "screen_buckets_delete_own" on public.screen_buckets;
create policy "screen_buckets_delete_own" on public.screen_buckets
for delete to authenticated using (user_id = (select auth.uid()));

-- Devices and buckets are only created by the ingest and pairing functions (service role).
-- Users may rename, assign and revoke a device and pick an app category, nothing else.
revoke insert, update on public.devices from anon, authenticated;
grant update (name, member_id, revoked_at) on public.devices to authenticated;
revoke insert, update, delete on public.apps from anon, authenticated;
grant update (category) on public.apps to authenticated;
revoke insert, update on public.usage_buckets, public.key_buckets, public.screen_buckets from anon, authenticated;

-- Applies one validated sync payload in a single transaction. Rows are absolute values, so the upsert
-- simply replaces what an earlier sync of the same bucket sent.
create or replace function public.ingest_sync(
  p_device_id uuid,
  p_timezone text,
  p_usage jsonb,
  p_keys jsonb,
  p_screen jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
begin
  select user_id into v_user from public.devices where id = p_device_id and revoked_at is null;
  if v_user is null then
    raise exception 'unknown or revoked device %', p_device_id;
  end if;

  insert into public.usage_buckets (device_id, user_id, day, hour, package_name, foreground_ms)
  select p_device_id, v_user, r.day, r.hour, r."packageName", r."foregroundMs"
  from jsonb_to_recordset(p_usage) as r(day date, hour smallint, "packageName" text, "foregroundMs" integer)
  on conflict (device_id, day, hour, package_name) do update set foreground_ms = excluded.foreground_ms;

  insert into public.apps (user_id, package_name, label)
  select distinct on (r."packageName") v_user, r."packageName", r.label
  from jsonb_to_recordset(p_usage) as r("packageName" text, label text)
  order by r."packageName", r.label nulls last
  on conflict (user_id, package_name) do update set label = coalesce(excluded.label, public.apps.label);

  insert into public.key_buckets (device_id, user_id, day, hour, key_class, count)
  select p_device_id, v_user, r.day, r.hour, r."keyClass", r.count
  from jsonb_to_recordset(p_keys) as r(day date, hour smallint, "keyClass" text, count integer)
  on conflict (device_id, day, hour, key_class) do update set count = excluded.count;

  insert into public.screen_buckets (device_id, user_id, day, hour, screen_on_ms)
  select p_device_id, v_user, r.day, r.hour, r."screenOnMs"
  from jsonb_to_recordset(p_screen) as r(day date, hour smallint, "screenOnMs" integer)
  on conflict (device_id, day, hour) do update set screen_on_ms = excluded.screen_on_ms;

  update public.devices set last_seen_at = now(), timezone = p_timezone where id = p_device_id;
end;
$$;

revoke all on function public.ingest_sync(uuid, text, jsonb, jsonb, jsonb) from public, anon, authenticated;
grant execute on function public.ingest_sync(uuid, text, jsonb, jsonb, jsonb) to service_role;
