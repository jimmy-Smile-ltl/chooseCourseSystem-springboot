-- 选课系统规范化 schema（PostgreSQL）
-- 旧库的 <学号>_course / <课程号>_student 动态表全部由 enrollments 替代。

create table admins (
    admin_id      text primary key,
    name          text not null,
    password_hash text not null,
    phone         text not null,
    created_at    timestamptz not null default now()
);

create table students (
    sno           text primary key,
    name          text not null,
    grade         text,
    major         text,
    college       text,
    password_hash text not null,
    phone         text,
    created_at    timestamptz not null default now()
);

create table courses (
    cno        text primary key,
    name       text not null,
    teacher    text,
    location   text,
    capacity   integer not null default 0 check (capacity >= 0),
    is_active  boolean not null default true,
    created_at timestamptz not null default now()
);

-- 一门课可以有多个上课时段；旧数据的一条 Ctime 迁移为一行。
create table course_sessions (
    id          bigserial primary key,
    cno         text not null references courses(cno) on delete cascade,
    day_of_week smallint not null check (day_of_week between 1 and 7), -- 1=周一
    start_time  time not null,
    end_time    time not null,
    constraint course_sessions_time_ck check (start_time < end_time),
    constraint course_sessions_unique_ck unique (cno, day_of_week, start_time)
);

create index course_sessions_conflict_idx
    on course_sessions (day_of_week, start_time, end_time);

-- 旧库 Cpno 是单值；新库支持多门先修课。
create table course_prerequisites (
    cno              text not null references courses(cno) on delete cascade,
    prerequisite_cno text not null references courses(cno),
    primary key (cno, prerequisite_cno),
    constraint course_prerequisites_not_self_ck check (cno <> prerequisite_cno)
);

-- 替代 student.Sclno 的“、”分隔字符串。
create table completed_courses (
    sno text not null references students(sno) on delete cascade,
    cno text not null references courses(cno),
    primary key (sno, cno)
);

-- 替代所有 <学号>_course 和 <课程号>_student 表。
create table enrollments (
    id         bigserial primary key,
    sno        text not null references students(sno) on delete cascade,
    cno        text not null references courses(cno) on delete cascade,
    created_at timestamptz not null default now(),
    constraint enrollments_unique_ck unique (sno, cno)
);

create index enrollments_cno_idx on enrollments (cno);

-- Spring Security 登录账号视图：学生和管理员共用一套登录流程。
-- 迁移前请确认学号与管理员 ID 没有重复。
create view accounts as
select sno as username, password_hash, 'STUDENT' as user_role
from students
union all
select admin_id as username, password_hash, 'ADMIN' as user_role
from admins;
