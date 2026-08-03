# 文澜智析 · 文本提取与权限管理平台

基于 Java 8、Spring Boot 2.7、MySQL 8、Vue 3、Element Plus 与 Three.js 的管理平台。项目在原文本提取与检索工具之上新增了账号安全、RBAC 权限管理、组织岗位管理和浙江省三维展示。

## 功能

- 账号登录：JWT Bearer 认证、BCrypt（cost 12）、首次登录强制改密、90 天密码到期、连续 5 次失败锁定 30 分钟、30 分钟令牌有效期。
- RBAC：用户—角色—菜单/权限点，多角色授权，后端方法级权限校验，超级管理员保护。
- 组织管理：公司层级、岗位、用户组织归属、账号启停与解锁。
- 附件处理：原有服务器路径提取、上传提取、缓存文档检索均归入“附件处理”子菜单。
- 浙江省 3D：使用 Vue 3 + Three.js 渲染 11 个地级市 GeoJSON，可旋转、缩放、悬停高亮和城市聚焦。
- 安全审计：登录、失败锁定、改密以及用户/角色/公司/岗位/菜单变更写入独立滚动日志。

## 环境要求

- JDK 8（也可使用更高版本构建）
- Maven 3.6+
- MySQL 8
- Node.js 20.19+ 或 22.12+

## 快速启动

```bash
mysql -u root < init.sql

cd backend
export APP_JWT_SECRET="$(openssl rand -base64 48)"
mvn clean package
java -jar target/extract-tool-1.0.0.jar

# 新终端
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

初始账号：`admin` / `Admin@123456`。首次登录必须修改密码。应用未配置至少 32 字节的 `APP_JWT_SECRET` 时会拒绝启动，避免误用公开默认密钥。

```bash
export APP_JWT_SECRET='至少32字节的高熵随机字符串'
export APP_ALLOWED_BASE_PATHS='/data/approved-documents'
export APP_TRUST_FORWARDED_FOR='false'
```

如 MySQL 账号不同，请修改 `backend/src/main/resources/application.yml` 或使用部署平台的配置覆盖机制。

## 菜单结构

```text
附件处理
├── 文本提取
└── 文本检索
浙江省 3D
系统管理
├── 用户管理
├── 角色管理
├── 公司管理
├── 岗位管理
└── 菜单管理
```

## 核心接口

除登录外，所有 `/api/**` 接口都要求 `Authorization: Bearer <token>`。

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/auth/login` | 公开 |
| GET | `/api/auth/me` | 已登录 |
| PUT | `/api/auth/change-password` | 已登录 |
| POST | `/api/extract/path`、`/api/extract/upload` | `attachment:extract` |
| POST | `/api/search/match` | `attachment:search` |
| GET | `/api/documents`、`/api/documents/{id}` | `attachment:search` |
| CRUD | `/api/admin/users` | `system:user:list/write` |
| CRUD | `/api/admin/roles` | `system:role:list/write` |
| CRUD | `/api/admin/companies` | `system:company:list/write` |
| CRUD | `/api/admin/positions` | `system:position:list/write` |
| CRUD | `/api/admin/menus` | `system:menu:list/write` |

## 安全与等保说明

代码侧已实现身份鉴别、访问控制、失败处理、会话时限、安全审计、文件白名单/MIME 检测、路径隔离和接口限流等应用安全基线。详细控制项与上线要求见 [docs/SECURITY_BASELINE.md](docs/SECURITY_BASELINE.md)。

“等保三级”是覆盖定级备案、网络与主机、应用与数据、建设管理、运维管理和测评整改的完整体系。本仓库不能单独代表系统已通过等保三级测评；部署时仍需 HTTPS、密钥托管、数据库最小权限、集中日志、防护设备、备份恢复、漏洞管理和制度流程等配套措施。

## 数据与第三方组件

浙江省 GeoJSON 来自 `china-map-geojson`（ISC License），以 npm 依赖随构建打包，运行时不请求外部地图服务。Three.js 页面按路由懒加载。

```bash
cd frontend
npm audit
npm run build

cd ../backend
mvn clean package
```
