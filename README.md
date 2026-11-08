# 选课系统 · Spring Boot 重写版

旧项目是 Java 8 + Servlet 3.1 + JSP + 原生 JDBC + MySQL 8 + Tomcat 8，
没有 Maven/Gradle，数据访问大量字符串拼 SQL，并且运行时按学生/课程动态建表。

这个目录是重写版：**Spring Boot 3 + Thymeleaf + PostgreSQL（Neon）+ Docker**。
旧项目根目录保持不变，便于对照。

> **在线演示：** <https://choosecoursesystem-springboot.onrender.com/login>  
> Render 免费实例 15 分钟无访问会休眠，首次打开约 30–60 秒冷启动。

它最早是一个 Java Web 课程作业（JSP + Servlet + JDBC + MySQL）。后来补前端和
Canvas 时，登录页、选课清单、课表被重新做了一遍，最终保留业务重写为
Spring Boot + PostgreSQL。

---

## 技术栈

| 项 | 选型 |
|---|---|
| Java | 21 |
| 框架 | Spring Boot 3.5（Web、Security、Validation、JDBC、Actuator） |
| 视图 | Thymeleaf + `thymeleaf-extras-springsecurity6` |
| 数据访问 | `JdbcTemplate` 显式 SQL（选课事务、复合键、聚合查询更直观） |
| 数据库 | PostgreSQL 16+（Neon） |
| 迁移 | Flyway |
| 部署 | Docker → Render（当前免费实例）/ Zeabur / Railway / Fly.io |

> Vercel 不能运行 Spring Boot/Tomcat/Java Web 容器。
> 如果一定要用 Vercel，只能把前端拆出去（Next.js/Nuxt），Java API 仍要部署到上面这些容器平台。

---

## 已实现 / 待实现

已实现：
- 学生 / 管理员统一登录（账号视图 `accounts`）
- 保留原版登录页视频背景、帮助/关于页
- 登录/忘记密码页加入鼠标跟随粒子网络、浅色连线、点击粒子爆发与中二漂浮文字
- Live2D 看板娘（本地 `waifu.css`/`waifu-tips.js` + jsDelivr 模型）
- 登录/忘记密码服务端 SVG 验证码 + 单实例 IP 限流（15 次 / 5 分钟）
- `prod` profile 下检测到演示开关会拒绝启动，避免生产暴露演示账号
- 忘记密码（账号 + 密保手机 + 验证码，BCrypt 重置）
- 学生课程列表：服务端分页（默认 20 门/页）、输入即搜索（防抖/兼容中文输入法）、
  只看有空位/只看无冲突
- 课程状态一屏展示：已修/已选/先修未满足/时间冲突/余量
- 选课页：批量勾选、实时学分与冲突预检、当前页一键智能推荐
- 选课事务：学生行锁 + 课程行锁 + 先修校验 + 时间冲突 + 容量 + 学分上限 + 唯一约束
- 学分目标礼炮：选课清单累计学分达到目标（默认 20）时触发双侧礼炮/彩带 + 达成提示，
  每个会话只庆祝一次；`prefers-reduced-motion` 下只显示文字提示
- 学分星环 HUD：右下角 Canvas 实时显示学分/上限和目标刻度，达标后变金色
- 冲突闪电：勾选的课程互相冲突时，在两行之间绘制红色闪电并高亮行
- 学生 12×7 彩色课表（按 `course_sessions` 映射节次、跨节块、冲突高亮、悬停详情）
- 课表 Canvas 极光与当前节次扫描线：高亮今天列，当前节次有移动光带
- 课表导出 `.ics` 日历（周重复、Asia/Shanghai 时区），可导入手机/Outlook/Google Calendar
- 学生个人信息、修改密码、修改密保手机
- 管理员课程 CRUD、停开/恢复、输入即搜索、分页、课程花名册
- 管理员批量导入课程：CSV 模板、UTF-8/格式/业务校验、错误行号报告、整批事务回滚
- 管理员学生 CRUD、搜索、学生已选课程
- Flyway schema、Dockerfile、旧数据 ETL 脚本
- Render + Neon 在线演示（免费实例；数据库由 Neon 托管）

待实现：
- 旧 MySQL 数据迁移演练（需要完整的线上 MySQL dump）
- 管理员学生列表服务端分页（课程列表已分页）
- 多实例部署时的 Redis/数据库限流
- 管理员编辑多上课时段课程（当前编辑页编辑第一个时段，schema 已支持多时段）
- ETL 真实数据校验报告与切换演练

---

## 本地运行与测试

要求：JDK 21+、PostgreSQL（本地或 Neon）、Maven Wrapper 会自动下载 Maven。

```bash
cd server

# Windows
mvnw.cmd test          # 44 个测试，含嵌入式 PostgreSQL 集成/E2E/并发抢课
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw test
./mvnw spring-boot:run
```

测试说明：
- `mvnw test` 不需要 Docker、不需要 Neon、不需要本地 PostgreSQL。
- `PostgresIntegrationTest` 和 `AppE2ETest` 使用 Zonky 嵌入式 PostgreSQL，
  首次运行会下载 Windows/Linux 的 PostgreSQL 二进制到临时目录。
- `LoginPageTest`、`AdminPageTest`、`StudentCoursePageTest` 使用 H2 做 Web 冒烟；
  真实 schema 和 SQL 由嵌入式 PostgreSQL 测试覆盖。
- 当前 44 个测试全部通过：Flyway schema、accounts 视图、登录、验证码/限流、权限隔离、
  管理员页面、学生课程页/服务端分页、课表色块/冲突、ICS 导出、选课事务（先修/冲突/
  容量/重复/退课重选/学分上限）、CSV 解析与导入校验/事务导入、演示种子幂等性、
  管理端课程/学生 CRUD、学生改密/改手机、忘记密码重置、并发抢最后一个名额、
  生产环境演示开关保护。

默认连接 `jdbc:postgresql://localhost:5432/choose_course`，用户/密码 `postgres/postgres`。
可以用环境变量覆盖：

| 变量 | 说明 |
|---|---|
| `DATABASE_URL` | 应用运行时 JDBC URL |
| `DB_USER` / `DB_PASSWORD` | 数据库账号 |
| `SPRING_FLYWAY_URL` | Flyway 迁移 JDBC URL；不填则复用 `DATABASE_URL` |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Flyway 账号；不填则复用 DataSource 账号 |
| `PORT` | 监听端口，默认 8080 |
| `SPRING_PROFILES_ACTIVE` | 生产环境设为 `prod` |
| `THYMELEAF_CACHE` | 模板缓存，生产设为 `true` |
| `DB_POOL_SIZE` | Hikari 最大连接数，默认 5 |
| `APP_MAX_CREDITS` | 本学期选课学分上限，默认 30 |
| `APP_CREDIT_GOAL` | 学分目标（触发礼炮特效），默认 20 |
| `APP_PAGE_SIZE` | 课程列表分页大小，默认 20 |

启动时 Flyway 会自动执行 `src/main/resources/db/migration/` 下的迁移
（`V1__schema.sql` 建表，`V2__course_credits_and_indexes.sql` 增加学分和查询索引）。

### 本地演示数据

可以开启演示种子。账号只在 `students` / `admins` 都为空时写入；课程按课程号幂等补齐，
已有课程不会被修改（适合给已有演示库补充课程）：

```bash
# Windows
set APP_DEMO_SEED=true
set APP_DEMO_LOGIN=true
mvnw.cmd spring-boot:run

# macOS / Linux
APP_DEMO_SEED=true APP_DEMO_LOGIN=true ./mvnw spring-boot:run
```

执行过演示种子后可用以下账号：

| 角色 | 账号 | 密码 |
|---|---|---|
| 学生 | `20200001` | `123456` |
| 管理员 | `admin` | `admin123` |

> 线上部署如果还没有执行演示种子，Neon 数据库会是空库，需要先在本地连接 Neon
> 写入一次，或通过管理员导入真实数据。

- `APP_DEMO_SEED=true`：写入演示学生、管理员，并幂等补齐 33 门演示课程。
- `APP_DEMO_LOGIN=true`：登录页自动填充学生账号、密码和验证码，直接点提交即可。
- **这两个开关只用于本地/演示，生产环境不要开启**；尤其是 `APP_DEMO_LOGIN` 会把演示凭据放进页面。

演示课程共 33 门，覆盖专业课、数学/英语公共课、经管/通识/体育课：

- 计算机：数据结构、操作系统、数据库系统、计算机网络、计算机组成原理、软件工程、
  人工智能导论、机器学习、算法设计与分析、离散数学、编译原理、网络安全、移动开发、Web 开发
- 公共基础：高等数学（一周两次）、线性代数、大学物理（一周两次）、概率论与数理统计、
  大学英语（一周两次）、学术英语写作
- 通识/经管/体育：中国近现代史纲要、心理学导论、创新创业基础、微观经济学、金融学基础、
  音乐鉴赏、影视艺术赏析、篮球、羽毛球、太极拳、急救常识、大学生心理健康
- 先修链：`c000 → t006/t008 → t009`、`t001 → t002/t005/t010/t012`、`m001 → m004`
- 演示学生会预置 `c000 程序设计基础` 为已修，用于展示“已修”状态
- 部分课程与已选课程时间冲突，用于展示冲突检测；`docker compose up --build` 默认开启演示开关

## 课程批量导入

管理员“课程管理 → 批量导入”支持 UTF-8 CSV（页面可下载模板）。表头：

```
课程号,课程名称,教师,地点,容量,学分,星期,开始时间,结束时间,先修课程号,是否开放
```

- 同一课程号写多行 = 同一门课的多个上课时段，其他字段必须一致
- 星期支持 `1-7` 或 `周一…周日`；时间 `HH:mm`；学分 `0-12`；容量 `0-10000`
- 先修课多个用 `|`、`、` 或 `;` 分隔
- 导入前完整校验：表头、列数、UTF-8、字段范围、重复课程、时段重叠、先修课是否存在
- 任一错误显示行号并整批回滚；已存在的课程号会被拒绝，不会被覆盖

### Neon 连接注意事项

应用运行时使用 **pooled** 连接串：

```
jdbc:postgresql://<project>-pooler.<region>.aws.neon.tech/<db>?sslmode=require&prepareThreshold=0
```

如果 Flyway 在 pooler 下报错，使用 **direct** 连接串（去掉 `-pooler`）：

```
SPRING_FLYWAY_URL=jdbc:postgresql://<project>.<region>.aws.neon.tech/<db>?sslmode=require
```

`prepareThreshold=0` 用于适配 Neon pooler 的 PgBouncer 事务模式。

---

## 旧数据迁移

先让 Flyway 建好表，再运行。ETL 读取的是**运行中的 MySQL**，如果本机没有旧库，
可以先把根目录的 `sql/Dump20220616.sql` 导入 MySQL；但仓库里的 dump 不完整，
正式迁移前建议从旧线上库重新导一份完整 dump（尤其是所有 `<学号>_course` 动态表）。

```bash
cd server
python -m venv .venv
# Windows 如果 python 命令不可用，改用 py
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r tools/requirements.txt

export MYSQL_HOST=127.0.0.1
export MYSQL_USER=root
export MYSQL_PASSWORD=<your-mysql-password>
export MYSQL_DATABASE=term_project
export PG_DSN='postgresql://user:pass@host/db?sslmode=require'

python tools/etl_mysql_to_pg.py
```

脚本做这些事：
- 读取 `admintable` / `student` / `course` / 所有 `<学号>_course` 动态表
- 密码转 BCrypt
- `Sclno` 拆成 `completed_courses`
- `Ctime` 解析成 `course_sessions`
- `Cpno` 拆成 `course_prerequisites`
- 为 19 个只在历史数据中出现、但课程表里没有的课程号创建 `is_active=false` 的归档课程
- `<课程号>_student` 表丢弃（花名册由 `enrollments JOIN students` 得出）
- 最后输出账号重复、超容量、数量校验

建议先对 Neon 的测试 branch 运行，确认输出没问题再跑生产。

---

## Docker / 部署

免费部署首选 **Render**，完整操作指南见 **[DEPLOY_RENDER.md](DEPLOY_RENDER.md)**。
Railway 现在需要付费，仅作为备选，见 **[DEPLOY_RAILWAY.md](DEPLOY_RAILWAY.md)**。
下面是本地 Docker 验证命令。

```bash
cd server
docker build -t choose-course .
docker run --rm -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://<host>/<db>?sslmode=require' \
  -e DB_USER='<user>' \
  -e DB_PASSWORD='<password>' \
  -e SPRING_PROFILES_ACTIVE=prod \
  choose-course
```

平台选择（免费首选 **Render**，数据库放 Neon Singapore）：

| 平台 | 说明 |
|---|---|
| Render | **推荐（免费）**：Docker、512MB、Singapore，15 分钟无访问会休眠 |
| Zeabur | 香港/新加坡区域，国内访问相对好；按量计费 |
| Railway | Docker 部署简单，但需要付费 |
| Fly.io | 香港/新加坡区域，CLI 操作，适合愿意折腾的人 |
| Vercel | **不能跑 Java**，只能托管拆分出去的前端 |

健康检查：`/actuator/health`。

生产环境建议：
- Neon 和函数区域选近（如 Singapore / Hong Kong）
- `DB_POOL_SIZE` 按 Neon 套餐连接数调整，多个实例要算总连接数
- 登录已有服务端验证码与内存限流；忘记密码已加验证码，多实例时把限流换成 Redis/数据库
- 忘记密码仍是“账号 + 密保手机”匹配，演示够用，正式上线建议短信验证码或管理员重置
- 登录视频已压缩为 720p H.264（约 7.8MB，原 62MB），并生成 `login-poster.jpg` 首帧海报；
  忘记密码背景图已压缩到 1920px（约 300KB，原 6.3MB）。原文件仍保留在旧项目
  `src/main/webapp/resource/` 下
- Live2D 的引擎/提示文件在本地 `static/live2d/`，模型文件默认从 jsDelivr 的
  `alink/live2d-widget` 仓库加载；如果部署网络屏蔽 jsDelivr，需要把模型文件也自托管
- 粒子/点击特效在 `static/js/fx.js`，关闭 `prefers-reduced-motion` 时自动不启用

---

## 数据模型

```
students ──< enrollments >── courses
    │                           │
    │                           ├──< course_sessions
    │                           ├──< course_prerequisites
    │                           └──< completed_courses
    └──< completed_courses
admins

accounts = students ∪ admins   -- 登录视图
```

关键点：
- 不再有 `<学号>_course` / `<课程号>_student` 动态表
- 选课人数由 `count(*)` 实时统计，不维护 `selperson`
- 选课事务使用 `SELECT ... FOR UPDATE` 锁学生行和课程行
- 唯一约束 `(sno, cno)` 兜底重复选课

---

## 目录

```
server/
├── src/main/java/com/example/choosecourse/
│   ├── auth/          账号加载、登录成功跳转、SVG 验证码、IP 限流守卫
│   ├── config/        Spring Security
│   ├── service/       选课事务、课程/学生查询、管理员 CRUD、账号、课表、密码重置
│   ├── web/           登录、验证码、学生、管理员 Controller
│   ├── web/form/      课程/学生表单对象
│   └── web/dto/       页面展示对象
├── src/main/resources/
│   ├── db/migration/  Flyway schema
│   ├── templates/     Thymeleaf 页面
│   └── static/css/    样式
├── src/test/
│   ├── resources/     H2 冒烟测试 schema
│   └── java/          单元测试 + H2 Web 测试 + 嵌入式 PostgreSQL 集成/E2E
├── tools/             MySQL → PostgreSQL ETL
├── Dockerfile
├── compose.yaml       本地 PostgreSQL + 应用（需要 Docker）
└── pom.xml
```
