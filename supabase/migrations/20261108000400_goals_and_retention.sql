-- Goals and limits, plus the retention job that keeps the database small.

create table if not exists public.goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  scope text not null check (scope in ('device', 'member', 'app', 'category')),
  -- Device id, member id, package name or category, depending on scope.
  target text not null check (char_length(target) between 1 and 255),
  metric text not null check (metric in ('screen_on', 'app_time', 'keypresses')),
  period text not null check (period in ('day', 'week')),
  -- Milliseconds for time metrics, a count for keypresses.
  limit_value bigint not null check (limit_value > 0),
  created_at timestamptz not null default now(),
  check (scope in ('device', 'member') or metric = 'app_time')
);

create index if not exists goals_user_idx on public.goals (user_id);

alter table public.goals enable row level security;

drop policy if exists "goals_all_own" on public.goals;
create policy "goals_all_own" on public.goals
for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- Deletes buckets older than each user's retention and housekeeping rows that have served their purpose.
-- Called daily by the /api/cron/retention route with the service role.
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

  delete from public.pairing_codes where expires_at < now() - interval '1 day';
  delete from public.rate_limits where window_start < now() - interval '1 day';
  delete from public.share_links where expires_at < now();
$$;

revoke all on function public.apply_retention() from public, anon, authenticated;
grant execute on function public.apply_retention() to service_role;
