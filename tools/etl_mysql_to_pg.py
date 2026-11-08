#!/usr/bin/env python3
"""
旧 MySQL(term_project) -> Neon/PostgreSQL 一次性迁移脚本。

前置条件：
  1. 新库已经执行过 Flyway V1__schema.sql（表、视图已存在）。
  2. 能同时连接旧 MySQL 和新 PostgreSQL。
  3. 安装依赖：pip install -r requirements.txt

用法示例：
  export MYSQL_HOST=127.0.0.1
  export MYSQL_USER=root
  export MYSQL_PASSWORD=<your-mysql-password>
  export MYSQL_DATABASE=term_project
  export PG_DSN='postgresql://user:pass@host/db?sslmode=require'
  python etl_mysql_to_pg.py

建议先在 Neon 的 branch/测试库上跑一遍，检查最后的校验输出，再对生产库执行。
脚本是幂等的：重复执行不会产生重复数据，也不会覆盖已有账号密码。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from datetime import time
from typing import Any, Iterable

import bcrypt
import pymysql
import pymysql.cursors
import psycopg


DAY_MAP = {
    "一": 1,
    "二": 2,
    "三": 3,
    "四": 4,
    "五": 5,
    "六": 6,
    "日": 7,
    "天": 7,
}

TIME_RE = re.compile(
    r"(?:星期|周)\s*([一二三四五六日天])\s*"
    r"(\d{1,2}:\d{2})\s*[-~至]\s*(\d{1,2}:\d{2})"
)

CODE_SPLIT_RE = re.compile(r"[、,，;；\s]+")


def log(message: str) -> None:
    print(message, flush=True)


def clean(value: Any) -> str:
    if value is None:
        return ""
    return str(value).replace("\r", " ").replace("\n", " ").strip()


def parse_codes(value: Any) -> list[str]:
    text = clean(value)
    if not text or text == "-":
        return []
    result: list[str] = []
    for part in CODE_SPLIT_RE.split(text):
        code = part.strip()
        if code and code != "-" and code not in result:
            result.append(code)
    return result


def parse_sessions(value: Any) -> list[tuple[int, time, time]]:
    text = clean(value)
    sessions: list[tuple[int, time, time]] = []
    for day_char, start_text, end_text in TIME_RE.findall(text):
        try:
            start = time.fromisoformat(start_text.zfill(5))
            end = time.fromisoformat(end_text.zfill(5))
        except ValueError:
            log(f"  [WARN] 无法解析上课时间: {text!r}")
            continue
        if start >= end:
            log(f"  [WARN] 开始时间不早于结束时间: {text!r}")
            continue
        sessions.append((DAY_MAP[day_char], start, end))
    if text and not sessions:
        log(f"  [WARN] 没有识别出上课时间: {text!r}")
    return sessions


def hash_password(raw: Any) -> str:
    password = clean(raw) or "123456"
    if password.startswith("$2"):
        return password
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("utf-8")


def quote_ident(name: str) -> str:
    return "`" + name.replace("`", "``") + "`"


@dataclass
class CourseSource:
    cno: str
    name: str
    teacher: str
    location: str
    capacity: int
    prerequisite_codes: list[str]
    sessions: list[tuple[int, time, time]]


@dataclass
class DynamicEnrollment:
    sno: str
    cno: str
    course_name: str
    table_name: str


def connect_mysql(args: argparse.Namespace) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_database,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def read_admins(mysql) -> list[dict[str, Any]]:
    with mysql.cursor() as cur:
        cur.execute("SELECT adminID, adminPW, adminPH FROM admintable")
        return list(cur.fetchall())


def read_students(mysql) -> list[dict[str, Any]]:
    with mysql.cursor() as cur:
        cur.execute("""
            SELECT Sno, Sname, Sgrade, Sdept, Saca, Spswd, Sphone, Sclno
            FROM student
        """)
        return list(cur.fetchall())


def read_courses(mysql) -> list[CourseSource]:
    with mysql.cursor() as cur:
        cur.execute("""
            SELECT teacher, Cname, Cno, Cpno, Ctime, Cloc, plim, selperson
            FROM course
        """)
        rows = list(cur.fetchall())

    result: list[CourseSource] = []
    for row in rows:
        cno = clean(row.get("Cno"))
        if not cno:
            log("  [WARN] 跳过没有课程号的课程行")
            continue
        try:
            capacity = int(clean(row.get("plim")) or 0)
        except ValueError:
            capacity = 0
            log(f"  [WARN] 课程 {cno} 的 plim 不是整数，按 0 处理")
        result.append(CourseSource(
            cno=cno,
            name=clean(row.get("Cname")),
            teacher=clean(row.get("teacher")),
            location=clean(row.get("Cloc")),
            capacity=max(capacity, 0),
            prerequisite_codes=parse_codes(row.get("Cpno")),
            sessions=parse_sessions(row.get("Ctime")),
        ))
    return result


def read_dynamic_enrollments(mysql, database: str) -> list[DynamicEnrollment]:
    with mysql.cursor() as cur:
        cur.execute("""
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = %s
              AND TABLE_NAME REGEXP '_course$'
            ORDER BY TABLE_NAME
        """, (database,))
        tables = [clean(row["TABLE_NAME"]) for row in cur.fetchall()]

    enrollments: list[DynamicEnrollment] = []
    for table_name in tables:
        if table_name == "course":
            continue
        sno = table_name[: -len("_course")].strip()
        if not sno:
            continue
        try:
            with mysql.cursor() as cur:
                cur.execute(f"SELECT Cno, Cname FROM {quote_ident(table_name)}")
                rows = list(cur.fetchall())
        except pymysql.MySQLError as exc:
            log(f"  [WARN] 读取动态表 {table_name} 失败: {exc}")
            continue
        log(f"  动态表 {table_name}: {len(rows)} 条")
        for row in rows:
            cno = clean(row.get("Cno"))
            if not cno:
                continue
            enrollments.append(DynamicEnrollment(
                sno=sno,
                cno=cno,
                course_name=clean(row.get("Cname")),
                table_name=table_name,
            ))
    return enrollments


def ensure_database(pg) -> None:
    with pg.cursor() as cur:
        cur.execute("SELECT to_regclass('public.courses')")
        exists = cur.fetchone()[0]
    if exists is None:
        raise SystemExit("目标库还没有表结构，请先运行 Spring Boot/Flyway 生成 V1__schema.sql。")


def insert_archived_courses(pg, missing: Iterable[str]) -> None:
    rows = [(cno, f"历史课程（{cno}）") for cno in sorted(set(missing))]
    if not rows:
        return
    with pg.cursor() as cur:
        cur.executemany("""
            INSERT INTO courses (cno, name, teacher, location, capacity, is_active)
            VALUES (%s, %s, '', '', 0, false)
            ON CONFLICT (cno) DO NOTHING
        """, rows)
    log(f"  已补齐 {len(rows)} 个历史课程占位行")


def insert_accounts(pg, admins: list[dict[str, Any]], students: list[dict[str, Any]]) -> None:
    admin_rows = [
        (
            clean(row.get("adminID")),
            "管理员",
            hash_password(row.get("adminPW")),
            clean(row.get("adminPH")),
        )
        for row in admins
        if clean(row.get("adminID"))
    ]
    student_rows = [
        (
            clean(row.get("Sno")),
            clean(row.get("Sname")) or "未命名",
            clean(row.get("Sgrade")),
            clean(row.get("Sdept")),
            clean(row.get("Saca")),
            hash_password(row.get("Spswd")),
            clean(row.get("Sphone")),
        )
        for row in students
        if clean(row.get("Sno"))
    ]
    with pg.cursor() as cur:
        cur.executemany("""
            INSERT INTO admins (admin_id, name, password_hash, phone)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (admin_id) DO NOTHING
        """, admin_rows)
        cur.executemany("""
            INSERT INTO students (sno, name, grade, major, college, password_hash, phone)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (sno) DO NOTHING
        """, student_rows)
    log(f"  写入 admins {len(admin_rows)} 行，students {len(student_rows)} 行（已存在的不覆盖）")


def insert_courses(pg, courses: list[CourseSource]) -> None:
    course_rows = [
        (c.cno, c.name or c.cno, c.teacher, c.location, c.capacity, True)
        for c in courses
    ]
    session_rows = [
        (c.cno, day, start, end)
        for c in courses
        for day, start, end in c.sessions
    ]
    prerequisite_rows = [
        (c.cno, prerequisite)
        for c in courses
        for prerequisite in c.prerequisite_codes
    ]

    with pg.cursor() as cur:
        cur.executemany("""
            INSERT INTO courses (cno, name, teacher, location, capacity, is_active)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (cno) DO UPDATE SET
                name = EXCLUDED.name,
                teacher = EXCLUDED.teacher,
                location = EXCLUDED.location,
                capacity = EXCLUDED.capacity,
                is_active = EXCLUDED.is_active
        """, course_rows)
        cur.executemany("""
            INSERT INTO course_sessions (cno, day_of_week, start_time, end_time)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (cno, day_of_week, start_time) DO NOTHING
        """, session_rows)
        cur.executemany("""
            INSERT INTO course_prerequisites (cno, prerequisite_cno)
            VALUES (%s, %s)
            ON CONFLICT (cno, prerequisite_cno) DO NOTHING
        """, prerequisite_rows)
    log(f"  写入 courses {len(course_rows)} 行，sessions {len(session_rows)} 行，prerequisites {len(prerequisite_rows)} 行")


def insert_completed_courses(pg, students: list[dict[str, Any]], known_courses: set[str]) -> int:
    rows: list[tuple[str, str]] = []
    skipped: set[str] = set()
    for student in students:
        sno = clean(student.get("Sno"))
        if not sno:
            continue
        for cno in parse_codes(student.get("Sclno")):
            if cno in known_courses:
                rows.append((sno, cno))
            else:
                skipped.add(cno)
    with pg.cursor() as cur:
        cur.executemany("""
            INSERT INTO completed_courses (sno, cno)
            VALUES (%s, %s)
            ON CONFLICT (sno, cno) DO NOTHING
        """, rows)
    if skipped:
        log(f"  [WARN] 以下已修课程代码仍不存在，已跳过: {sorted(skipped)}")
    log(f"  写入 completed_courses {len(rows)} 行")
    return len(rows)


def insert_enrollments(pg, enrollments: list[DynamicEnrollment], known_students: set[str]) -> int:
    rows: list[tuple[str, str]] = []
    skipped_students: set[str] = set()
    for enrollment in enrollments:
        if enrollment.sno not in known_students:
            skipped_students.add(enrollment.sno)
            continue
        rows.append((enrollment.sno, enrollment.cno))
    with pg.cursor() as cur:
        cur.executemany("""
            INSERT INTO enrollments (sno, cno)
            VALUES (%s, %s)
            ON CONFLICT (sno, cno) DO NOTHING
        """, rows)
    if skipped_students:
        log(f"  [WARN] 以下学号在 student 表中不存在，已跳过其选课记录: {sorted(skipped_students)}")
    log(f"  写入 enrollments {len(rows)} 行")
    return len(rows)


def validate(pg, old_courses: list[CourseSource], old_enrollment_count: int) -> None:
    with pg.cursor() as cur:
        cur.execute("SELECT count(*) FROM students")
        student_count = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM admins")
        admin_count = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM courses")
        course_count = cur.fetchone()[0]
        cur.execute("SELECT count(*) FROM enrollments")
        enrollment_count = cur.fetchone()[0]
        cur.execute("""
            SELECT username
            FROM (
                SELECT sno AS username FROM students
                UNION ALL
                SELECT admin_id AS username FROM admins
            ) t
            GROUP BY username
            HAVING count(*) > 1
        """)
        duplicate_accounts = [row[0] for row in cur.fetchall()]
        cur.execute("""
            SELECT c.cno, c.capacity, count(e.id) AS selected
            FROM courses c
            JOIN enrollments e ON e.cno = c.cno
            GROUP BY c.cno, c.capacity
            HAVING count(e.id) > c.capacity
        """)
        over_capacity = list(cur.fetchall())

    log("")
    log("=== 迁移校验 ===")
    log(f"  students={student_count}, admins={admin_count}, courses={course_count}, enrollments={enrollment_count}")
    log(f"  源动态表选课行数={old_enrollment_count}（与 enrollments 的差异来自重复/孤儿数据）")
    log(f"  账号重复={duplicate_accounts if duplicate_accounts else '无'}")
    log(f"  超容量课程={over_capacity if over_capacity else '无'}")

    old_sel = {c.cno: c.capacity for c in old_courses}
    log(f"  源课程数={len(old_sel)}，目标课程数（含历史占位）={course_count}")


def main() -> int:
    parser = argparse.ArgumentParser(description="MySQL term_project -> PostgreSQL/Neon ETL")
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--mysql-port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--mysql-password", default=os.getenv("MYSQL_PASSWORD", ""))
    parser.add_argument("--mysql-database", default=os.getenv("MYSQL_DATABASE", "term_project"))
    parser.add_argument("--pg-dsn", default=os.getenv("PG_DSN", ""))
    parser.add_argument("--dry-run", action="store_true", help="执行全部步骤后回滚")
    args = parser.parse_args()

    if not args.pg_dsn:
        log("缺少 PG_DSN。示例：postgresql://user:pass@host/db?sslmode=require")
        return 2

    log("连接旧 MySQL...")
    mysql = connect_mysql(args)
    try:
        log("读取 admintable / student / course ...")
        admins = read_admins(mysql)
        students = read_students(mysql)
        courses = read_courses(mysql)
        dynamic_enrollments = read_dynamic_enrollments(mysql, args.mysql_database)
    finally:
        mysql.close()

    known_students = {clean(row.get("Sno")) for row in students if clean(row.get("Sno"))}
    known_courses = {c.cno for c in courses}
    referenced_codes: set[str] = set()
    for course in courses:
        referenced_codes.update(course.prerequisite_codes)
    for student in students:
        referenced_codes.update(parse_codes(student.get("Sclno")))
    for enrollment in dynamic_enrollments:
        referenced_codes.add(enrollment.cno)
    missing_codes = referenced_codes - known_courses

    log("连接目标 PostgreSQL...")
    with psycopg.connect(args.pg_dsn) as pg:
        ensure_database(pg)
        try:
            insert_archived_courses(pg, missing_codes)
            insert_accounts(pg, admins, students)
            insert_courses(pg, courses)
            insert_completed_courses(pg, students, known_courses | missing_codes)
            insert_enrollments(pg, dynamic_enrollments, known_students)
            if args.dry_run:
                log("--dry-run: 回滚")
                pg.rollback()
            else:
                pg.commit()
                validate(pg, courses, len(dynamic_enrollments))
        except Exception:
            pg.rollback()
            raise

    log("完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
