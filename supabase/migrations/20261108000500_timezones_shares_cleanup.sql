-- Account time zones that follow the browser or the TV, tighter share links, the apps of one device, and
-- cleanup of devices whose token never reached the TV.

-- A null time zone means the user has not chosen one yet: the dashboard then uses the zone of the device
-- that synced last, and sets the browser's zone on the first visit.
alter table public.settings alter column timezone drop not null;
alter table public.settings alter column timezone drop default;
-- Before this migration every account got 'UTC' without being asked; treat it as not chosen.
update public.settings set timezone = null where timezone = 'UTC';

-- Sign-up passes the browser's zone as user metadata. It is user input, so only known zone names are kept.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_timezone text := new.raw_user_meta_data ->> 'timezone';
begin
  if v_timezone is not null and not exists (select 1 from pg_catalog.pg_timezone_names where name = v_timezone) then
    v_timezone := null;
  end if;
  insert into public.profiles (id) values (new.id) on conflict do nothing;
  insert into public.settings (user_id, timezone) values (new.id, v_timezone) on conflict do nothing;
  return new;
end;
$$;

-- Share links last at most 30 days. Users insert them directly, so created_at is not theirs to set.
alter table public.share_links drop constraint if exists share_links_lifetime;
alter table public.share_links
  add constraint share_links_lifetime
  check (expires_at > created_at and expires_at <= created_at + interval '30 days');
revoke insert on public.share_links from anon, authenticated;
grant insert (user_id, token_hash, device_id, expires_at) on public.share_links to authenticated;

-- Packages that have usage on a device. A share link for one device names only these apps.
create or replace view public.device_apps
with (security_invoker = true)
as select distinct user_id, device_id, package_name from public.usage_buckets;

revoke all on public.device_apps from anon;
grant select on public.device_apps to authenticated, service_role;

-- Adds one rule to the daily job: a device that never synced a day after pairing is dropped. That covers a
-- token lost on its way to the TV, which nobody holds; the TV pairs again with a new code.
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

  delete from public.devices where last_seen_at is null and created_at < now() - interval '1 day';

  delete from public.pairing_codes where expires_at < now() - interval '1 day';
  delete from public.rate_limits where window_start < now() - interval '1 day';
  delete from public.share_links where expires_at < now();
$$;
