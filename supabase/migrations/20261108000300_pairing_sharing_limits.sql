-- Pairing codes, share links and the rate-limit counters used by the API.

create table if not exists public.pairing_codes (
  id uuid primary key default gen_random_uuid(),
  -- SHA-256 of the six-character code shown on the TV and of the poll token the TV keeps.
  code_hash text not null unique check (code_hash ~ '^[0-9a-f]{64}$'),
  poll_token_hash text not null unique check (poll_token_hash ~ '^[0-9a-f]{64}$'),
  device_name text not null check (char_length(device_name) between 1 and 60),
  model text check (char_length(model) <= 80),
  expires_at timestamptz not null,
  claimed_by uuid references public.profiles (id) on delete cascade,
  device_id uuid references public.devices (id) on delete cascade,
  delivered_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists pairing_codes_expires_idx on public.pairing_codes (expires_at);

create table if not exists public.share_links (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  token_hash text not null unique check (token_hash ~ '^[0-9a-f]{64}$'),
  -- Null shares every device of the user.
  device_id uuid,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  foreign key (device_id, user_id) references public.devices (id, user_id) on delete cascade
);

create index if not exists share_links_user_idx on public.share_links (user_id);

create table if not exists public.rate_limits (
  key text not null,
  window_start timestamptz not null,
  hits integer not null default 0,
  primary key (key, window_start)
);

alter table public.pairing_codes enable row level security;
alter table public.share_links enable row level security;
alter table public.rate_limits enable row level security;

-- Pairing codes and rate limits have no policies: only the service role touches them.
revoke all on public.pairing_codes, public.rate_limits from anon, authenticated;

drop policy if exists "share_links_select_own" on public.share_links;
create policy "share_links_select_own" on public.share_links
for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists "share_links_insert_own" on public.share_links;
create policy "share_links_insert_own" on public.share_links
for insert to authenticated with check (user_id = (select auth.uid()));

drop policy if exists "share_links_delete_own" on public.share_links;
create policy "share_links_delete_own" on public.share_links
for delete to authenticated using (user_id = (select auth.uid()));

revoke update on public.share_links from anon, authenticated;

-- Fixed-window counter. Returns false once the window holds more than p_limit hits.
create or replace function public.hit_rate_limit(p_key text, p_limit integer, p_window_seconds integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_window timestamptz := to_timestamp(floor(extract(epoch from now()) / p_window_seconds) * p_window_seconds);
  v_hits integer;
begin
  insert into public.rate_limits as r (key, window_start, hits)
  values (p_key, v_window, 1)
  on conflict (key, window_start) do update set hits = r.hits + 1
  returning r.hits into v_hits;
  return v_hits <= p_limit;
end;
$$;

-- Creates the device for the user who confirmed the code, once. Returns null if the code is gone.
create or replace function public.claim_pairing(p_pairing_id uuid, p_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_pairing public.pairing_codes;
  v_device uuid;
begin
  select * into v_pairing
  from public.pairing_codes
  where id = p_pairing_id and device_id is null and expires_at > now()
  for update;
  if not found then
    return null;
  end if;

  insert into public.devices (user_id, name, model)
  values (p_user_id, v_pairing.device_name, v_pairing.model)
  returning id into v_device;

  update public.pairing_codes set claimed_by = p_user_id, device_id = v_device where id = p_pairing_id;
  return v_device;
end;
$$;

-- Stores the hash of the token handed to the TV and consumes the pairing. The TV must collect it within
-- one more code lifetime (600 s) after the code expired.
create or replace function public.deliver_pairing(p_pairing_id uuid, p_token_hash text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_device uuid;
begin
  update public.pairing_codes
  set delivered_at = now()
  where id = p_pairing_id
    and device_id is not null
    and delivered_at is null
    and expires_at + interval '600 seconds' > now()
  returning device_id into v_device;
  if v_device is null then
    return false;
  end if;

  update public.devices set token_hash = p_token_hash where id = v_device and revoked_at is null;
  return found;
end;
$$;

revoke all on function public.hit_rate_limit(text, integer, integer) from public, anon, authenticated;
revoke all on function public.claim_pairing(uuid, uuid) from public, anon, authenticated;
revoke all on function public.deliver_pairing(uuid, text) from public, anon, authenticated;
grant execute on function public.hit_rate_limit(text, integer, integer) to service_role;
grant execute on function public.claim_pairing(uuid, uuid) to service_role;
grant execute on function public.deliver_pairing(uuid, text) to service_role;
