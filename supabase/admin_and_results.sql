-- Math Mission: administrator and test results (project "math-mission", acdzoowaixgccjaffzza).
-- * admins: who may see every learner's results. Rows are added only by the project owner (SQL), never from the app.
-- * test_attempts: every submitted test with each question, the learner's answer, the correct answer and the mistake.
-- Row-level security: a learner reads and writes only their own rows; the administrator can read everything.

create table if not exists public.admins (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security;
revoke all on public.admins from anon, authenticated;

create or replace function public.is_admin() returns boolean
  language sql stable security definer set search_path = ''
as $$ select exists (select 1 from public.admins a where a.user_id = (select auth.uid())) $$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

alter table public.profiles add column if not exists email text;
update public.profiles p set email = u.email from auth.users u where u.id = p.id and p.email is null;

drop policy if exists "profiles: admin reads all" on public.profiles;
create policy "profiles: admin reads all" on public.profiles for select to authenticated using ((select public.is_admin()));

create table if not exists public.test_attempts (
  id             text not null,
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  test_key       text not null,
  test_title     text not null,
  started_at     timestamptz,
  submitted_at   timestamptz not null,
  points         numeric,
  max_points     numeric,
  percent        numeric,
  grade          smallint,
  grade_label    text,
  items          jsonb not null default '[]'::jsonb,
  misconceptions jsonb not null default '[]'::jsonb,
  created_at     timestamptz not null default now(),
  primary key (user_id, id)
);
create index if not exists test_attempts_test_key_idx on public.test_attempts (test_key);
create index if not exists test_attempts_submitted_idx on public.test_attempts (submitted_at desc);
alter table public.test_attempts enable row level security;

drop policy if exists "attempts: insert own" on public.test_attempts;
drop policy if exists "attempts: read own or admin" on public.test_attempts;
create policy "attempts: insert own" on public.test_attempts for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "attempts: read own or admin" on public.test_attempts for select to authenticated
  using ((select auth.uid()) = user_id or (select public.is_admin()));

grant select, insert on public.test_attempts to authenticated;
revoke all on public.test_attempts from anon;

-- The administrator (owner of the project): Анелия Богева.
insert into public.admins (user_id)
  select id from auth.users where email = 'aneliabogeva@gbg.bg'
  on conflict do nothing;
