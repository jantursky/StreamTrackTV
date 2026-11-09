-- Security events the TV records at its lock screen and in its PIN settings (app opened, wrong PIN, Forgot PIN, ...).
-- The TV uploads them with its device token; the owner reads them on the dashboard. Nothing typed is ever stored:
-- an event is a type, a time and at most one small number.

create table if not exists public.security_events (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  -- Made on the TV, so a batch that is sent twice stores each event once.
  event_id uuid not null,
  type text not null check (
    type in (
      'app_opened', 'lock_shown', 'pin_wrong', 'pin_wait_started', 'unlocked',
      'forgot_pin_opened', 'pin_reset_confirmed', 'pin_enabled', 'pin_changed', 'pin_disabled'
    )
  ),
  -- The TV's clock; received_at is the server's, so a TV with a wrong clock stays visible.
  occurred_at timestamptz not null,
  value integer check (value is null or (value >= 0 and value <= 100000)),
  received_at timestamptz not null default now(),
  unique (device_id, event_id)
);

create index if not exists security_events_user_time_idx on public.security_events (user_id, occurred_at desc);
create index if not exists security_events_device_time_idx on public.security_events (device_id, occurred_at desc);

alter table public.security_events enable row level security;

-- The owner reads and deletes their own events. Inserts go through ingest_events with the service role.
drop policy if exists "security_events_select_own" on public.security_events;
create policy "security_events_select_own" on public.security_events
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "security_events_delete_own" on public.security_events;
create policy "security_events_delete_own" on public.security_events
for delete to authenticated using (user_id = (select auth.uid()));

revoke insert, update on public.security_events from anon, authenticated;
revoke all on public.security_events from anon;

-- Stores one validated batch. Returns how many events were new. Events more than a day ahead of the server
-- clock are refused as a whole by the API before this runs.
create or replace function public.ingest_events(p_device_id uuid, p_events jsonb)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
  v_count integer;
begin
  select user_id into v_user from public.devices where id = p_device_id and revoked_at is null;
  if v_user is null then
    raise exception 'unknown or revoked device %', p_device_id;
  end if;

  insert into public.security_events (device_id, user_id, event_id, type, occurred_at, value)
  select p_device_id, v_user, r."eventId", r.type, r.at, r.value
  from jsonb_to_recordset(p_events) as r("eventId" uuid, type text, at timestamptz, value integer)
  on conflict (device_id, event_id) do nothing;
  get diagnostics v_count = row_count;

  return v_count;
end;
$$;

revoke all on function public.ingest_events(uuid, jsonb) from public, anon, authenticated;
grant execute on function public.ingest_events(uuid, jsonb) to service_role;

-- Adds events to the daily job: they follow the user's retention like the usage rows do.
create or replace function public.apply_retention()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.usage_buckets b using public.settings s
  where b.user_id = s.user_id and b.day < current_date - s.retention_days;

  delete from public.key_buckets b using public.settings s
  where b.user_id = s.user_id and b.day < current_date - s.retention_days;

  delete from public.screen_buckets b using public.settings s
  where b.user_id = s.user_id and b.day < current_date - s.retention_days;

  delete from public.security_events e using public.settings s
  where e.user_id = s.user_id and e.occurred_at < now() - make_interval(days => s.retention_days);

  delete from public.devices where last_seen_at is null and created_at < now() - interval '1 day';

  delete from public.pairing_codes where expires_at < now() - interval '1 day';
  delete from public.rate_limits where window_start < now() - interval '1 day';
  delete from public.share_links where expires_at < now();
$$;
