# Railway 部署操作手册

本项目是 **Spring Boot 3 + Thymeleaf + PostgreSQL** 单体应用，已经带 `Dockerfile`，
推荐部署组合：

```
GitHub → Railway（Docker，Singapore）→ Spring Boot
                         ↓
                 Neon PostgreSQL（Singapore）
```

Vercel 不能运行 Java，不要选 Vercel。

---

## 0. 部署前准备

需要：

- GitHub 账号
- Railway 账号（Hobby 套餐即可）
- Neon 账号
- 一个能访问 Railway / Neon 的网络环境

当前构建产物约 **42MB**，登录视频已经压缩到约 8MB，不需要对象存储。

### 推荐仓库结构

只把 `server/` 推送到一个独立仓库，最简单：

```bash
cd chooseCourseSystem-master/server
git init
git add .
git commit -m "deploy: choose course"
git branch -M main
git remote add origin https://github.com/<你的账号>/choose-course.git
git push -u origin main
```

`.gitignore` 已排除 `target/`、`.local-pg/`、`.env`，不要提交这些内容。

如果坚持把整个项目推上去，Railway 里必须把 **Root Directory** 设为 `server`。

---

## 1. 创建 Neon 数据库

1. 登录 Neon，创建 Project。
2. Region 选择 **Singapore / ap-southeast-1**，和 Railway 区域保持一致。
3. 创建数据库，默认库名通常是 `neondb`。
4. 在 Connection Details 复制连接串，Neon 会给出类似：

```text
postgresql://<user>:<password>@ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

应用运行时使用 **pooled** 连接串，转成 JDBC：

```text
jdbc:postgresql://ep-xxxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&prepareThreshold=0
```

Flyway 迁移建议使用 **direct** 连接串，避免 pooler 的 prepared statement 兼容问题：

```text
jdbc:postgresql://ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

注意：direct 地址通常要去掉主机名里的 `-pooler`。

---

## 2. 创建 Railway 服务

1. 登录 Railway，新建 Project。
2. 选择 **Deploy from GitHub repo**，授权并选择刚才的仓库。
3. 如果仓库根目录是项目根目录而不是 `server/`：
   - Service → Settings → Source → **Root Directory** 填 `server`
4. Service → Settings → Build：
   - Builder 选择 **Dockerfile**
   - Dockerfile Path 填 `Dockerfile`
5. Service → Settings → Deploy：
   - Region：**Singapore**
   - Healthcheck Path：`/actuator/health`
   - Healthcheck Timeout：`120` 秒
   - Restart Policy：`On Failure`
   - Memory：512MB 起步，出现 OOM 再调到 1GB
6. Settings → Networking → **Generate Domain**，先拿到一个 `*.up.railway.app` 域名。

> Railway 已经把旧的 config-as-code 标记为弃用，推荐直接在面板里配置，
> 不要依赖 `railway.json`。

### 可选：Railway CLI

不想连 GitHub 时，可以在 Windows PowerShell 里用 CLI 上传：

```powershell
npm install -g @railway/cli
railway login
cd chooseCourseSystem-master/server
railway init
railway up
```

如果项目已经在面板创建好，先用 `railway link` 绑定，再 `railway up`。
变量仍然建议在 Railway 面板里填写：

```powershell
railway variable set KEY=value
```

---

## 3. 配置环境变量

在 Railway Service → **Variables** 中添加：

| 变量 | 值 |
|---|---|
| `DATABASE_URL` | Neon pooled JDBC URL，含 `sslmode=require&prepareThreshold=0` |
| `DB_USER` | Neon 用户名 |
| `DB_PASSWORD` | Neon 密码 |
| `SPRING_FLYWAY_URL` | Neon direct JDBC URL |
| `SPRING_FLYWAY_USER` | 不填则复用 `DB_USER` |
| `SPRING_FLYWAY_PASSWORD` | 不填则复用 `DB_PASSWORD` |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `THYMELEAF_CACHE` | `true` |
| `DB_POOL_SIZE` | `3` |
| `APP_MAX_CREDITS` | `30` |
| `APP_CREDIT_GOAL` | `20` |
| `APP_PAGE_SIZE` | `20` |

**不要设置：**

- `APP_DEMO_SEED`
- `APP_DEMO_LOGIN`

`prod` profile 会检查演示开关，误开会导致应用拒绝启动。
Railway 会自动注入 `PORT`，应用已经读取 `${PORT:8080}`，不要手动设置端口。

如果数据库密码包含 `@`、`:`、`/` 等字符，需要 URL 编码后再放进 JDBC URL。

---

## 4. 第一次部署

保存变量后 Railway 会自动重新部署，也可以手动 Redeploy。

查看 Deploy Logs，正常情况下会看到：

- `Flyway ... Migrating schema ... to version v1`
- `Flyway ... version v2`
- `Tomcat started on port ...`
- `Started ChooseCourseApplication`

健康检查：

```bash
curl https://<你的 railway 域名>/actuator/health
```

期望返回：

```json
{"status":"UP"}
```

然后访问：

- `/login`：登录页
- `/admin/courses`：管理员课程管理
- `/student/courses`：学生选课

---

## 5. 写入数据

### 5.1 演示数据

演示种子包含学生 `20200001/123456`、管理员 `admin/admin123` 和 33 门课程。

**不要在 Railway 的生产服务里打开演示开关。** 推荐在本地临时连 Neon 写入一次：

PowerShell：

```powershell
cd chooseCourseSystem-master/server

$env:DATABASE_URL="jdbc:postgresql://<pooled-host>/neondb?sslmode=require&prepareThreshold=0"
$env:DB_USER="<neon-user>"
$env:DB_PASSWORD="<neon-password>"
$env:SPRING_FLYWAY_URL="jdbc:postgresql://<direct-host>/neondb?sslmode=require"
$env:APP_DEMO_SEED="true"
$env:APP_DEMO_LOGIN="true"
.\mvnw.cmd spring-boot:run
```

等待日志出现“演示数据就绪”，确认数据库里有数据后按 `Ctrl+C` 停止。
然后回到 Railway，确保演示开关没有设置，重新部署。

> 注意：本地运行只用于写数据；不要在本地长期保留 Neon 密码，可把环境变量写在
> 临时脚本里，用完关闭终端。

### 5.2 旧 MySQL 数据

先让应用完成 Flyway 建表，再运行 ETL：

```bash
cd server
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r tools/requirements.txt

export MYSQL_HOST=127.0.0.1
export MYSQL_USER=root
export MYSQL_PASSWORD=<your-mysql-password>
export MYSQL_DATABASE=term_project
export PG_DSN='postgresql://<neon-user>:<neon-password>@<direct-host>/neondb?sslmode=require'

python tools/etl_mysql_to_pg.py
```

详细说明见 [README.md](README.md) 的“旧数据迁移”章节。

---

## 6. 自定义域名

1. Railway Service → Settings → Networking → Custom Domain。
2. 按提示在域名服务商添加 CNAME。
3. HTTPS 证书由 Railway 自动签发。
4. `prod` profile 已启用安全 Cookie 和 `forward-headers-strategy: framework`，反代场景无需额外改代码。

如果面向国内访问，Railway 的 Singapore 节点延迟通常可以接受；
对国内访问要求更高可以换 Zeabur 香港/新加坡节点，部署方式同样是 Docker。

---

## 7. 更新与回滚

日常更新：

```bash
git add .
git commit -m "feat: xxx"
git push
```

Railway 会自动构建部署，健康检查通过后切流量。

回滚：

- Railway → Deployments → 选择历史部署 → **Redeploy**
- 数据库迁移是向前执行的，回滚代码前先确认 Flyway 版本兼容
- 高风险改动前在 Neon 创建 branch 或手动备份

---

## 8. 故障排查

| 现象 | 处理 |
|---|---|
| 构建失败：找不到 Dockerfile | Root Directory 设为 `server`，Builder 选 Dockerfile |
| 502 / Healthcheck failed | 看 Deploy Logs；确认启动时间、`PORT`、数据库连接 |
| Flyway 在 pooler 下报错 | 设置 `SPRING_FLYWAY_URL` 为 direct 连接串 |
| 应用启动即退出，提示演示开关 | 删除 `APP_DEMO_SEED` / `APP_DEMO_LOGIN`，或不要用 `prod` profile |
| `OutOfMemoryError` | 内存调到 1GB，Dockerfile 已设置 `-XX:MaxRAMPercentage=75` |
| 数据库连接数不足 | 降低 `DB_POOL_SIZE`，确认使用 Neon pooled URL |
| Live2D 模型加载失败 | jsDelivr 被屏蔽；把模型文件下载到 `static/live2d/` 自托管 |
| 静态资源 404 | 确认构建时 `src/main/resources/static` 已打入 jar |
| 页面中文乱码 | 确认数据库/文件均为 UTF-8，浏览器无强制编码 |

---

## 9. 上线检查清单

- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] 没有设置演示种子/演示登录开关
- [ ] `/actuator/health` 返回 `UP`
- [ ] Neon 使用 SSL，pooled/direct 连接串都验证过
- [ ] 管理员默认密码已修改
- [ ] 自定义域名 HTTPS 正常
- [ ] 登录、选课、退课、课表、CSV 导入、ICS 导出实际走一遍
- [ ] Neon branch/备份已创建
- [ ] Live2D 模型在目标网络下可加载，或已自托管
- [ ] 旧 MySQL 数据迁移数量校验通过（如果使用 ETL）

---

## 10. 成本参考

- Neon：免费额度足够课程演示；生产按量升级。
- Railway Hobby：约 $5/月起步，含一定用量；512MB 小服务通常够用，1GB 更稳。
- 自定义域名：域名费用；Railway 证书免费。
