-- 仅用于 H2 的 Web 冒烟测试；真实 schema 由 Flyway 管理。
drop view if exists accounts;
drop table if exists students;
drop table if exists admins;

create table students (
    sno           varchar(20) primary key,
    name          varchar(50) not null,
    grade         varchar(20),
    major         varchar(50),
    college       varchar(50),
    password_hash varchar(100) not null,
    phone         varchar(20)
);

create table admins (
    admin_id      varchar(20) primary key,
    name          varchar(50) not null,
    password_hash varchar(100) not null,
    phone         varchar(20) not null
);

create view accounts as
select sno as username, password_hash, 'STUDENT' as user_role
from students
union all
select admin_id as username, password_hash, 'ADMIN' as user_role
from admins;
