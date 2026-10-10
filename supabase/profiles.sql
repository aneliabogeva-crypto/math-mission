-- Math Mission: learner accounts (e-mail + password) in Supabase.
-- Passwords are handled only by Supabase Auth (stored hashed in auth.users) — never in this table.
-- Project "math-mission" (acdzoowaixgccjaffzza). Applied 2026-10-10 as migration "create_profiles".

create table if not exists public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  nickname    text not null check (char_length(nickname) between 2 and 24),
  avatar      text not null default 'fox',
  age_band    text check (age_band in ('UNDER_14', 'FROM_14')),
  goal        text check (goal in ('CLASSROOM', 'IMPROVE', 'ASSESSMENT')),
  confidence  smallint check (confidence between 1 and 5),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.profiles is 'Math Mission learner profile: nickname and avatar only (no real name). One row per account.';

-- Row-level security: every account can see and change only its own profile.
alter table public.profiles enable row level security;

drop policy if exists "profiles: read own" on public.profiles;
drop policy if exists "profiles: insert own" on public.profiles;
drop policy if exists "profiles: update own" on public.profiles;
drop policy if exists "profiles: delete own" on public.profiles;

create policy "profiles: read own"   on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profiles: insert own" on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles: update own" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "profiles: delete own" on public.profiles for delete to authenticated using ((select auth.uid()) = id);

grant select, insert, update, delete on public.profiles to authenticated;
revoke all on public.profiles from anon;
