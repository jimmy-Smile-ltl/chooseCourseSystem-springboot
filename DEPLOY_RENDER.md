# Render 免费部署操作指南

本项目是 Spring Boot 3 + Thymeleaf + PostgreSQL 单体应用，已经在仓库根目录提供
`Dockerfile`。推荐组合：

```
GitHub → Render Free Web Service（Docker，Singapore）
                         ↓
                 Neon PostgreSQL（Singapore）
```

不需要购买域名，也不需要信用卡（以 Render 当前注册页为准）。
Render 会自动分配 `https://<服务名>.onrender.com` 的 HTTPS 地址。

> Railway 现在需要付费，仅作为备选。Render 免费档的代价是：
> 15 分钟无访问会休眠，下次访问冷启动约 30–60 秒。

---

## 0. 准备

需要：

- 已推送到 GitHub 的仓库：
  `https://github.com/jimmy-Smile-ltl/chooseCourseSystem-springboot`
- Render 账号
- Neon 账号
- 浏览器即可，不需要自己的域名

仓库根目录必须包含：

- `Dockerfile`
- `pom.xml`
- `src/`
- `DEPLOY_RENDER.md`（本文件）

---

## 1. 创建 Neon 数据库

1. 登录 [Neon](https://neon.tech)，创建 Project。
2. Region 选与 Render 相同的区域：
   - Render Singapore → Neon **Singapore / ap-southeast-1**
   - Render Ohio → Neon **US East (Ohio) / us-east-2**
3. 数据库使用默认的 `neondb` 即可。
4. 在 Connection Details 复制连接串，例如：

```text
postgresql://<user>:<password>@ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

应用运行时用 **pooled** 连接：

```text
jdbc:postgresql://ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0
```

Flyway 用 **direct** 连接，把主机名里的 `-pooler` 去掉：

```text
jdbc:postgresql://ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

注意：

- Neon 主机名里的 `us-east-2` = Ohio，`ap-southeast-1` = Singapore
- 应用和数据库尽量同区域；一个 Ohio、一个 Singapore 会增加每次查询延迟
- Neon 复制出的 URI 可能带 `channel_binding=require`，这是 libpq 参数；
  JDBC 基本部署保留 `sslmode=require` 即可
- **真实连接串包含数据库密码，只能放在环境变量里，不要写进 README、聊天或 Git**

记下：

- Neon 用户名
- Neon 密码
- pooled JDBC URL
- direct JDBC URL

---

## 2. 在 Render 创建 Web Service

1. 登录 [Render](https://render.com)。
2. 点 **New +** → **Web Service**。
3. 连接 GitHub 账号，选择仓库：

```text
jimmy-Smile-ltl/chooseCourseSystem-springboot
```

4. 填写 Service 配置：

| 项目 | 填写 |
|---|---|
| Name | `choose-course-demo`（可自定义，需全局唯一） |
| Region | 与 Neon 相同（Singapore 或 Ohio） |
| Branch | `main` |
| Root Directory | 留空（仓库根目录就是应用） |
| Runtime | **Docker** |
| Instance Type | **Free** |

5. 如果没有看到 Docker 选项，选择 **Docker** 作为 Environment，Render 会自动读取
   根目录的 `Dockerfile`。
6. 不需要填写 Build Command 和 Start Command，`Dockerfile` 已经包含：
   - Maven 构建
   - `java -XX:MaxRAMPercentage=75 -jar app.jar`
   - 读取 Render 自动注入的 `PORT`

---

## 3. 健康检查

在创建页面的 **Advanced**，或创建后的 **Settings** 中设置：

```text
Health Check Path: /actuator/health
```

其他保持默认即可。

---

## 4. 配置环境变量

在 **Environment** 中添加变量，可以逐个添加，也可以使用 **Add from .env**
粘贴下面的内容（把占位符换成自己的 Neon 值）：

```env
DATABASE_URL=jdbc:postgresql://ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0
DB_USER=<neon-user>
DB_PASSWORD=<neon-password>

SPRING_FLYWAY_URL=jdbc:postgresql://ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
SPRING_FLYWAY_USER=<neon-user>
SPRING_FLYWAY_PASSWORD=<neon-password>

SPRING_PROFILES_ACTIVE=prod
THYMELEAF_CACHE=true
DB_POOL_SIZE=3
APP_MAX_CREDITS=30
APP_CREDIT_GOAL=20
APP_PAGE_SIZE=20
```

注意：

- **不要设置 `PORT`**：Render 会自动注入，应用使用 `${PORT:8080}`
- **不要设置 `APP_DEMO_SEED` / `APP_DEMO_LOGIN`**：生产环境会拒绝启动
- Neon 密码不要拼进 `DATABASE_URL`，分别放在 `DB_PASSWORD`
- 真实连接串不要提交到 Git；一旦粘贴到公开位置，立即在 Neon 控制台重置密码
- 如果 Flyway 在 pooled 连接下报错，确认 `SPRING_FLYWAY_URL` 是 direct 地址
- `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` 与 DB 用户一致时也可以省略

保存后 Render 会自动开始部署。

---

## 5. 第一次部署

点击 **Create Web Service** 或 **Deploy**。

构建过程大致是：

1. 拉取 GitHub 仓库
2. 读取 `Dockerfile`
3. 下载 Maven 依赖并构建 jar
4. 启动 Spring Boot
5. Render 请求 `/actuator/health`

在 **Logs** 中应看到：

```text
Flyway ... Successfully applied ... migrations
Tomcat started on port ...
Started ChooseCourseApplication
```

首次构建通常需要几分钟。

---

## 6. 访问地址

部署成功后，Render 控制台顶部会显示：

```text
https://<你的服务名>.onrender.com
```

直接访问：

```text
https://<你的服务名>.onrender.com/login
```

健康检查：

```text
https://<你的服务名>.onrender.com/actuator/health
```

期望返回：

```json
{"status":"UP"}
```

**没有自己的域名完全不影响使用**，`onrender.com` 子域名带 HTTPS，已经够演示。
以后要绑自己的域名，再到 Settings → Custom Domains 配置即可。

---

## 7. 写入演示数据

Render 首次启动时数据库是空的，没有账号无法登录。推荐在本地临时连 Neon 写入一次演示数据。

PowerShell：

```powershell
cd chooseCourseSystem-master/server

$env:DATABASE_URL="jdbc:postgresql://<pooled-host>/neondb?sslmode=require&prepareThreshold=0"
$env:DB_USER="<neon-user>"
$env:DB_PASSWORD="<neon-password>"
$env:SPRING_FLYWAY_URL="jdbc:postgresql://<direct-host>/neondb?sslmode=require"
$env:APP_DEMO_SEED="true"
$env:PORT="8081"

.\mvnw.cmd spring-boot:run
```

说明：

- 本地不要设置 `SPRING_PROFILES_ACTIVE=prod`，否则演示种子会被拒绝
- 使用 `PORT=8081` 避免和本地正在运行的 8080 应用冲突
- 看到“演示数据就绪”后按 `Ctrl+C`
- 再回到 Render，确认演示开关没有配置

演示账号：

| 角色 | 账号 | 密码 |
|---|---|---|
| 学生 | `20200001` | `123456` |
| 管理员 | `admin` | `admin123` |

登录后请立即修改管理员密码，或者把演示账号删除，换成正式数据。

如果使用旧 MySQL 数据，先让应用完成 Flyway 建表，再按
[README.md](README.md) 的“旧数据迁移”运行 ETL。

---

## 8. Render 免费档限制

| 限制 | 影响 |
|---|---|
| 512MB 内存 / 0.1 CPU | 适合课程演示，不建议高并发 |
| 15 分钟无访问休眠 | 下次打开需要 30–60 秒冷启动 |
| 每月 750 实例小时 | 单个服务约 744 小时，基本够用 |
| 临时文件系统 | 数据必须保存在 Neon，不要写本地文件 |
| 免费实例不保证 SLA | 正式生产请升级付费实例 |
| 5GB/月带宽 | 登录视频 8MB，正常演示够用 |

> 第一次打开 Render 页面可能看到加载页，这是免费实例在唤醒，不是部署失败。

---

## 9. 更新与回滚

### 更新

```bash
git add .
git commit -m "feat: xxx"
git push
```

Render 默认开启 Auto Deploy，会检测 `main` 分支更新并自动部署。

### 回滚

Render → 服务 → **Deploys** → 选择历史部署 → **Redeploy**。

数据库迁移是向前执行的，回滚代码前先确认 Flyway 版本兼容；
高风险改动前建议在 Neon 创建 branch 或备份。

---

## 10. 故障排查

| 现象 | 处理 |
|---|---|
| 构建失败，找不到 Dockerfile | Root Directory 留空，确认仓库根目录有 `Dockerfile` |
| 部署失败，Healthcheck failed | 看 Logs；确认启动完成、`/actuator/health` 可访问 |
| 502 / 服务不可用 | 等待冷启动；或查看应用是否因数据库连接失败退出 |
| Flyway 在 pooler 报错 | `SPRING_FLYWAY_URL` 改 direct 连接串 |
| 启动时报演示开关错误 | 删除 `APP_DEMO_SEED` / `APP_DEMO_LOGIN` |
| `OutOfMemoryError` / 容器重启 | 512MB 不够；升级付费实例，或改用 Oracle Cloud Always Free |
| 数据库 SSL 错误 | JDBC URL 必须带 `sslmode=require` |
| 首次请求很慢 | 免费实例休眠后冷启动，属正常现象 |
| Live2D 模型加载失败 | 部署网络可能屏蔽 jsDelivr，需要自托管模型 |
| 页面静态资源 404 | 确认构建时 `src/main/resources/static` 已打进 jar |

---

## 11. 上线检查清单

- [ ] Render 服务 Region 为 Singapore
- [ ] Runtime 为 Docker，Root Directory 留空
- [ ] Instance Type 为 Free
- [ ] 健康检查路径 `/actuator/health`
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] 没有设置演示种子/演示登录开关
- [ ] Neon pooled/direct URL 都配置正确
- [ ] 首次部署日志显示应用启动成功
- [ ] `/actuator/health` 返回 `UP`
- [ ] 演示数据已写入 Neon
- [ ] 管理员密码已修改
- [ ] 登录、选课、退课、课表、CSV 导入实际验证
- [ ] 没有绑定域名的需求，直接使用 `onrender.com` 地址

---

## 12. 平台说明

- **Render Free**：本项目首选；免费、Docker、Singapore，但会休眠。
- **Railway**：部署体验好，但需要付费。
- **Vercel**：不能运行 Java。
- **Koyeb**：2026 年免费计划存在关闭新用户注册的不确定性，不建议作为首选。
- **Oracle Cloud Always Free**：不想休眠可以选择，但需要信用卡验证和自行维护 VPS。
