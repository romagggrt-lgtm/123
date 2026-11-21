-- Run once in the Supabase SQL Editor after schema.sql to delete yesterday's chat
-- at midnight Moscow time (21:00 UTC). The cleanup functions on every chat request too.
create extension if not exists pg_cron;
select cron.schedule('zalupa-chat-daily-cleanup', '0 21 * * *', $$delete from zs_private.messages where created_at < zs_private.today_start()$$);
