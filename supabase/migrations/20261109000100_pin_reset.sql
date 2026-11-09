-- PIN reset requests: a paired TV that shows "Forgot PIN" asks for a code, the signed-in owner of that TV
-- confirms it on the web, and the TV collects the answer once and clears its PIN.

create table if not exists public.pin_reset_requests (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices (id) on delete cascade,
  -- SHA-256 of the six-character code shown on the TV and of the poll token the TV keeps.
  code_hash text not null unique check (code_hash ~ '^[0-9a-f]{64}$'),
  poll_token_hash text not null unique check (poll_token_hash ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz not null,
  confirmed_by uuid references public.profiles (id) on delete cascade,
  confirmed_at timestamptz,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists pin_reset_requests_device_idx on public.pin_reset_requests (device_id);
create index if not exists pin_reset_requests_expires_idx on public.pin_reset_requests (expires_at);

alter table public.pin_reset_requests enable row level security;

-- No policies: only the service role touches the table.
revoke all on public.pin_reset_requests from anon, authenticated;

-- Opens a request for the device and drops its earlier ones, so only the newest code can be confirmed.
-- Returns false when the code hash is already used by a live request.
create or replace function public.start_pin_reset(
  p_device_id uuid,
  p_code_hash text,
  p_poll_token_hash text,
  p_expires_at timestamptz
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.pin_reset_requests where device_id = p_device_id and consumed_at is null;
  delete from public.pin_reset_requests where code_hash = p_code_hash and expires_at < now();
  insert into public.pin_reset_requests (device_id, code_hash, poll_token_hash, expires_at)
  values (p_device_id, p_code_hash, p_poll_token_hash, p_expires_at);
  return true;
exception
  when unique_violation then
    return false;
end;
$$;

-- Marks the request confirmed, once, and only for the user who owns the device behind it.
create or replace function public.confirm_pin_reset(p_request_id uuid, p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.pin_reset_requests r
  set confirmed_by = p_user_id, confirmed_at = now()
  where r.id = p_request_id
    and r.confirmed_by is null
    and r.consumed_at is null
    and r.expires_at > now()
    and exists (
      select 1 from public.devices d
      where d.id = r.device_id and d.user_id = p_user_id and d.revoked_at is null
    );
  return found;
end;
$$;

-- Hands the confirmation to the TV exactly once. The TV must collect it within one more code lifetime
-- (600 s) after the code expired.
create or replace function public.consume_pin_reset(p_request_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.pin_reset_requests
  set consumed_at = now()
  where id = p_request_id
    and confirmed_by is not null
    and consumed_at is null
    and expires_at + interval '600 seconds' > now();
  return found;
end;
$$;

revoke all on function public.start_pin_reset(uuid, text, text, timestamptz) from public, anon, authenticated;
revoke all on function public.confirm_pin_reset(uuid, uuid) from public, anon, authenticated;
revoke all on function public.consume_pin_reset(uuid) from public, anon, authenticated;
grant execute on function public.start_pin_reset(uuid, text, text, timestamptz) to service_role;
grant execute on function public.confirm_pin_reset(uuid, uuid) to service_role;
grant execute on function public.consume_pin_reset(uuid) to service_role;
