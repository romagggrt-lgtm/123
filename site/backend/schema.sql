-- Run as the project owner in the Supabase SQL Editor. Never place an owner password in this file.
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists zs_private;
revoke all on schema zs_private from public, anon, authenticated;

create table if not exists zs_private.users (
  id uuid primary key default gen_random_uuid(),
  nickname text not null check (nickname ~ '^[A-Za-z0-9_]{3,16}$'),
  password_hash text not null,
  role_name text,
  role_color text,
  is_owner boolean not null default false,
  created_at timestamptz not null default now(),
  check ((role_name is null and role_color is null) or (char_length(role_name) between 1 and 24 and role_color ~ '^#[0-9A-Fa-f]{6}$'))
);
create unique index if not exists zs_users_nickname_ci on zs_private.users (lower(nickname));
create table if not exists zs_private.sessions (
  token_hash text primary key,
  user_id uuid not null references zs_private.users(id) on delete cascade,
  expires_at timestamptz not null
);
create table if not exists zs_private.login_attempts (
  name_key text primary key,
  attempts integer not null default 0,
  blocked_until timestamptz,
  updated_at timestamptz not null default now()
);
create table if not exists zs_private.rate_limits (
  key text primary key,
  window_start timestamptz not null,
  count integer not null
);
create table if not exists zs_private.messages (
  id bigint generated always as identity primary key,
  user_id uuid not null references zs_private.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 500),
  created_at timestamptz not null default now()
);
create index if not exists zs_messages_created_at on zs_private.messages(created_at);
revoke all on all tables in schema zs_private from public, anon, authenticated;
revoke all on all sequences in schema zs_private from public, anon, authenticated;

create or replace function zs_private.today_start() returns timestamptz
language sql stable set search_path = pg_catalog
as $$ select date_trunc('day', now() at time zone 'Europe/Moscow') at time zone 'Europe/Moscow' $$;

create or replace function zs_private.user_for_token(p_token text) returns uuid
language sql stable security definer set search_path = pg_catalog, zs_private, extensions
as $$
  select user_id from zs_private.sessions
  where length(p_token) = 64 and token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex') and expires_at > now()
$$;

create or replace function zs_private.profile(p_id uuid) returns jsonb
language sql stable security definer set search_path = pg_catalog, zs_private
as $$
  select jsonb_build_object('nickname', nickname, 'role', role_name, 'color', role_color, 'owner', is_owner)
  from zs_private.users where id = p_id
$$;

-- Run this manually once in the SQL editor, never through the website.
-- Example: select zs_private.create_owner('REPLACE_WITH_A_NEW_PRIVATE_PASSWORD');
create or replace function zs_private.create_owner(p_password text) returns text
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$
begin
  if p_password is null or octet_length(p_password) < 8 or octet_length(p_password) > 72 then raise exception 'Password must be 8–72 bytes'; end if;
  if exists(select 1 from zs_private.users where lower(nickname) = 'sqrxxx' or is_owner) then raise exception 'Owner already exists'; end if;
  insert into zs_private.users(nickname, password_hash, role_name, role_color, is_owner)
  values ('sqrxxx', extensions.crypt(p_password, extensions.gen_salt('bf', 12)), 'Владелец', '#ad71ff', true);
  return 'Owner account created';
end $$;
revoke all on all functions in schema zs_private from public, anon, authenticated;

create or replace function public.zs_register(p_nickname text, p_password text) returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$
declare u uuid; t text;
begin
  if p_nickname is null or p_nickname !~ '^[A-Za-z0-9_]{3,16}$' then return jsonb_build_object('error','nickname'); end if;
  if lower(p_nickname) = 'sqrxxx' then return jsonb_build_object('error','reserved'); end if;
  if p_password is null or octet_length(p_password) < 8 or octet_length(p_password) > 72 then return jsonb_build_object('error','password'); end if;
  insert into zs_private.rate_limits(key, window_start, count) values('registrations',now(),1)
  on conflict (key) do update set
    count = case when zs_private.rate_limits.window_start < now()-interval '5 minutes' then 1 else zs_private.rate_limits.count+1 end,
    window_start = case when zs_private.rate_limits.window_start < now()-interval '5 minutes' then now() else zs_private.rate_limits.window_start end;
  if (select count from zs_private.rate_limits where key='registrations') > 20 then return jsonb_build_object('error','signup_rate'); end if;
  begin
    insert into zs_private.users(nickname, password_hash) values (p_nickname, extensions.crypt(p_password, extensions.gen_salt('bf', 12))) returning id into u;
  exception when unique_violation then return jsonb_build_object('error','taken'); end;
  t := encode(extensions.gen_random_bytes(32),'hex');
  insert into zs_private.sessions(token_hash, user_id, expires_at) values (encode(extensions.digest(t, 'sha256'),'hex'), u, now()+interval '30 days');
  return jsonb_build_object('token',t,'user',zs_private.profile(u));
end $$;

create or replace function public.zs_login(p_nickname text, p_password text) returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$
declare u zs_private.users%rowtype; a zs_private.login_attempts%rowtype; t text; k text;
begin
  if p_nickname is null or p_nickname !~ '^[A-Za-z0-9_]{3,16}$' or p_password is null or octet_length(p_password) > 72 then return jsonb_build_object('error','credentials'); end if;
  k := lower(p_nickname);
  insert into zs_private.login_attempts(name_key) values(k) on conflict (name_key) do nothing;
  select * into a from zs_private.login_attempts where name_key=k for update;
  if a.blocked_until > now() then return jsonb_build_object('error','rate'); end if;
  select * into u from zs_private.users where lower(nickname)=k;
  if u.id is null or extensions.crypt(p_password,u.password_hash) <> u.password_hash then
    update zs_private.login_attempts set attempts = case when updated_at < now()-interval '15 minutes' then 1 else attempts+1 end,
      blocked_until = case when updated_at >= now()-interval '15 minutes' and attempts+1 >= 5 then now()+interval '15 minutes' else null end,
      updated_at = now() where name_key=k;
    return jsonb_build_object('error','credentials');
  end if;
  delete from zs_private.login_attempts where name_key=k;
  delete from zs_private.sessions where expires_at <= now();
  t := encode(extensions.gen_random_bytes(32),'hex');
  insert into zs_private.sessions(token_hash, user_id, expires_at) values(encode(extensions.digest(t, 'sha256'),'hex'),u.id,now()+interval '30 days');
  return jsonb_build_object('token',t,'user',zs_private.profile(u.id));
end $$;

create or replace function public.zs_me(p_token text) returns jsonb
language sql stable security definer set search_path = pg_catalog, zs_private, extensions
as $$ select coalesce(zs_private.profile(zs_private.user_for_token(p_token)),jsonb_build_object('error','session')) $$;

create or replace function public.zs_logout(p_token text) returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$ begin
  if length(p_token)=64 then delete from zs_private.sessions where token_hash=encode(extensions.digest(p_token,'sha256'),'hex'); end if;
  return jsonb_build_object('ok',true);
end $$;

create or replace function public.zs_messages() returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private
as $$
begin
  delete from zs_private.messages where created_at < zs_private.today_start();
  return coalesce((select jsonb_agg(jsonb_build_object('id',m.id,'nickname',u.nickname,'role',u.role_name,'color',u.role_color,'owner',u.is_owner,'body',m.body,'created_at',m.created_at) order by m.id)
    from (select * from zs_private.messages where created_at >= zs_private.today_start() order by id desc limit 100) m join zs_private.users u on u.id=m.user_id),'[]'::jsonb);
end $$;

create or replace function public.zs_send(p_token text, p_body text) returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$
declare u uuid; b text;
begin
  u := zs_private.user_for_token(p_token);
  if u is null then return jsonb_build_object('error','session'); end if;
  b := btrim(regexp_replace(coalesce(p_body,''), '[[:cntrl:]]', ' ', 'g'));
  if char_length(b)<1 or char_length(b)>500 then return jsonb_build_object('error','message'); end if;
  -- Serialize each user's requests so concurrent sends cannot bypass the cooldown.
  perform 1 from zs_private.users where id=u for update;
  if exists(select 1 from zs_private.messages where user_id=u and created_at > now()-interval '3 seconds') then return jsonb_build_object('error','slow'); end if;
  delete from zs_private.messages where created_at < zs_private.today_start();
  insert into zs_private.messages(user_id,body) values(u,b);
  return jsonb_build_object('ok',true);
end $$;

create or replace function public.zs_set_role(p_token text, p_nickname text, p_role text, p_color text) returns jsonb
language plpgsql security definer set search_path = pg_catalog, zs_private, extensions
as $$
declare u uuid; target uuid; r text;
begin
  u := zs_private.user_for_token(p_token);
  if not exists(select 1 from zs_private.users where id=u and is_owner) then return jsonb_build_object('error','forbidden'); end if;
  if p_nickname is null or p_nickname !~ '^[A-Za-z0-9_]{3,16}$' then return jsonb_build_object('error','not_found'); end if;
  select id into target from zs_private.users where lower(nickname)=lower(p_nickname) and not is_owner;
  if target is null then return jsonb_build_object('error','not_found'); end if;
  r := btrim(coalesce(p_role,''));
  if r = '' then update zs_private.users set role_name=null, role_color=null where id=target;
  else
    if char_length(r)>24 or r ~ '[[:cntrl:]]' or p_color is null or p_color !~ '^#[0-9A-Fa-f]{6}$' then return jsonb_build_object('error','role'); end if;
    update zs_private.users set role_name=r, role_color=lower(p_color) where id=target;
  end if;
  return jsonb_build_object('ok',true);
end $$;

revoke all on function public.zs_register(text,text), public.zs_login(text,text), public.zs_me(text), public.zs_logout(text), public.zs_messages(), public.zs_send(text,text), public.zs_set_role(text,text,text,text) from public;
grant execute on function public.zs_register(text,text), public.zs_login(text,text), public.zs_me(text), public.zs_logout(text), public.zs_messages(), public.zs_send(text,text), public.zs_set_role(text,text,text,text) to anon, authenticated;
