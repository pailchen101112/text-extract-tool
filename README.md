# 文本提取与检索工具

基于 **Java 8 + Spring Boot + Apache Tika + MySQL** 的后端，配合 **Vue 3 + Vite + Element Plus** 前端。

支持：
- 传入**服务器文件路径**或**上传文件流**提取文本（Word doc/docx、PDF、txt、html、rtf 等，基于 Tika）
- **按文件名缓存**提取结果，并用 SHA-256 校验内容变化实现缓存命中
- 传入**多个文本**，判断它们是否出现在某个目标文本中（目标文本可来自缓存文件或内联输入）

## 目录结构

```
text-extract-tool/
├── backend/          Spring Boot 后端 (Maven)
├── frontend/         Vue3 前端 (Vite)
├── init.sql          MySQL 建库建表脚本
└── README.md
```

## 环境要求

- JDK 8
- Maven 3.6+
- MySQL 8
- Node 18+ / npm

## 一、数据库

```bash
# 启动 MySQL 后(brew 安装默认 root 无密码)
mysql -u root < init.sql
```

如你的 MySQL 有密码，修改 `backend/src/main/resources/application.yml` 中的 `username/password`。

## 二、后端

```bash
cd backend
mvn clean package
java -jar target/extract-tool-1.0.0.jar
# 默认端口 8080
```

## 三、前端

```bash
cd frontend
npm install
npm run dev      # 开发模式, 默认端口 5173, /api 自动代理到 8080
# 或
npm run build    # 生产构建, 产物在 dist/
```

浏览器打开 http://localhost:5173

## 接口说明

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/extract/path` | body `{ "filePath": "...", "useCache": true }` 按路径提取 |
| POST | `/api/extract/upload` | multipart `file` + `useCache` 上传流提取 |
| POST | `/api/search/match` | body `{ "fileName"?, "haystack"?, "needles": [], "caseSensitive": false }` 多文本命中判断 |
| GET | `/api/documents` | 列出已缓存文档 |
| GET | `/api/documents/{id}` | 查看某缓存文档全文 |

### 缓存策略
按 `file_name` 查表，用 `content_hash`(SHA-256) 校验：
- 命中且 hash 一致 → 直接返回缓存（`cacheHit=true`）
- 命中但 hash 不同 → 重新提取并更新
- 未命中 → 提取并新增

### 检索接口示例
```bash
curl -X POST http://localhost:8080/api/search/match \
  -H 'Content-Type: application/json' \
  -d '{
    "fileName": "test.pdf",
    "needles": ["合同金额", "甲方签字", "不存在的词"],
    "caseSensitive": false
  }'
```
返回每个 needle 的 `found / count / firstIndex`。

## 安全管控
`/api/extract/path` 和 `/api/extract/upload` 均启用了安全校验, 全局还启用了限流与审计日志。

### 1. 文件校验 (`PathSecurityService`)
| 检查项 | 说明 | 触发拒绝时 HTTP |
|---|---|---|
| 绝对路径 | 路径必须以 `/` 开头, 拒绝相对路径 | 400 |
| 文件存在 + 普通文件 | 拒绝目录、缺失文件、设备文件 | 400 |
| 大小上限 | 默认 100MB, 可配置 | 400 |
| 扩展名白名单 | 默认 `pdf,doc,docx,txt,html,htm,rtf,md,xls,xlsx,ppt,pptx,odt,csv,xml` | 400 |
| **MIME 嗅探** | Apache Tika 读取文件头检测实际类型, 与扩展名不符则拒绝(防伪扩展名) | 400 |
| 路径白名单 | 基于 canonical 路径(防 `../` 与符号链接逃逸), 文件必须位于允许根目录下 | 400 |

### 2. 接口限流 (`RateLimitInterceptor` + `RateLimitService`)
- 按客户端 IP 固定窗口(每分钟)限流, 内存实现
- 默认 60 次/分钟, 可配置; 超限返回 **HTTP 429** + `Retry-After` 头 + JSON 错误体
- 优先使用 `X-Forwarded-For` 头(代理场景), 否则取 `remoteAddr`

### 3. 操作审计日志 (`AuditLogService`)
- 每次文本提取操作(成功/失败)均记录一条结构化日志
- 字段: `source`(path/upload) / `fileName` / `contentHash` / `fileSize` / `clientIp` / `result`(ok/error) / `error`
- 持久化到 `logs/audit.log`(滚动, 按天+大小切分, gzip 压缩, 保留 30 天), 同时输出到控制台

### 配置示例 (`application.yml`)
```yaml
app:
  security:
    # 允许读取的服务器根目录(绝对路径), 多个用英文逗号分隔
    allowed-base-paths: /Users/pailchen/work/text-extract-tool
    # 允许的文件扩展名(小写, 不含点), 逗号分隔
    allowed-extensions: pdf,doc,docx,txt,html,htm,rtf,md,xls,xlsx,ppt,pptx,odt,csv,xml
    # 单文件最大字节数
    max-file-size-bytes: 104857600
    rate-limit:
      enabled: true
      requests-per-minute: 60
```

如需临时禁用按路径提取, 将 `allowed-base-paths` 留空即可。
