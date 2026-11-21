-- Disposable test database only. Tests are rolled back; no real account is created.
begin;
do $$
declare owner_result text; alice jsonb; bob jsonb; boss jsonb; a text; b text; o text; r jsonb;
begin
  if exists(select 1 from zs_private.users) then raise exception 'Run tests only in an empty disposable database'; end if;
  if public.zs_register('SqRxXx','12345678')->>'error' <> 'reserved' then raise exception 'owner reservation failed'; end if;
  owner_result := zs_private.create_owner('testing-password');
  alice := public.zs_register('Alice_12','testing-password'); a := alice->>'token';
  bob := public.zs_register('Bob_345','testing-password'); b := bob->>'token';
  boss := public.zs_login('sqrxxx','testing-password'); o := boss->>'token';
  if a is null or alice->'user'->>'nickname' <> 'Alice_12' then raise exception 'registration/profile failed'; end if;
  if public.zs_register('alice_12','testing-password')->>'error' <> 'taken' then raise exception 'nickname must be case insensitive'; end if;
  if public.zs_login('Alice_12','wrong')->>'error' <> 'credentials' then raise exception 'wrong password accepted'; end if;
  if public.zs_me(a)->>'nickname' <> 'Alice_12' then raise exception 'session failed'; end if;
  if public.zs_set_role(a,'Bob_345','Builder','#ff00ff')->>'error' <> 'forbidden' then raise exception 'non-owner assigned role'; end if;
  if public.zs_set_role(o,'sqrxxx','Fake','#ff00ff')->>'error' <> 'not_found' then raise exception 'owner role changed'; end if;
  if public.zs_set_role(o,'Bob_345','Builder','#ff00ff')->>'ok' <> 'true' then raise exception 'role grant failed'; end if;
  if public.zs_me(b)->>'role' <> 'Builder' then raise exception 'role missing'; end if;
  if public.zs_set_role(o,'Bob_345','','#ff00ff')->>'ok' <> 'true' then raise exception 'role removal failed'; end if;
  if public.zs_me(b)->>'role' is not null then raise exception 'role was not removed'; end if;
  if public.zs_set_role(o,'Bob_345','Builder','red')->>'error' <> 'role' then raise exception 'invalid role color accepted'; end if;
  if public.zs_send('invalid','hello')->>'error' <> 'session' then raise exception 'anonymous message allowed'; end if;
  if public.zs_send(a,'hello')->>'ok' <> 'true' then raise exception 'message failed'; end if;
  if public.zs_send(a,'again')->>'error' <> 'slow' then raise exception 'message cooldown failed'; end if;
  insert into zs_private.messages(user_id,body,created_at) select id,'yesterday',zs_private.today_start()-interval '1 second' from zs_private.users where nickname='Alice_12';
  r := public.zs_messages();
  if jsonb_array_length(r) <> 1 or r->0->>'body' <> 'hello' then raise exception 'daily cleanup failed: %',r; end if;
  for i in 1..4 loop perform public.zs_login('Alice_12','wrong'); end loop;
  if public.zs_login('Alice_12','testing-password')->>'error' <> 'rate' then raise exception 'login lockout failed'; end if;
  perform public.zs_logout(a);
  if public.zs_me(a)->>'error' <> 'session' then raise exception 'logout failed'; end if;
end $$;
set local role anon;
do $$ begin
  if public.zs_login('unknown_player','wrong')->>'error' <> 'credentials' then raise exception 'public rpc failed'; end if;
  begin perform * from zs_private.users; raise exception 'private users readable'; exception when insufficient_privilege then null; end;
  begin perform zs_private.create_owner('fake-password'); raise exception 'owner bootstrap accessible'; exception when insufficient_privilege then null; end;
end $$;
rollback;
