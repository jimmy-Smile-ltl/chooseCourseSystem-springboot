-- 课程学分 + 外键列索引
alter table courses
    add column credits integer not null default 2;

alter table courses
    add constraint courses_credits_ck check (credits >= 0 and credits <= 12);

create index course_prerequisites_prereq_idx
    on course_prerequisites (prerequisite_cno);

create index completed_courses_cno_idx
    on completed_courses (cno);
