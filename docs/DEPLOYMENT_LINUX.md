# Linux 服务器生产部署手册

本文给出单机生产部署基线：Nginx 托管 Vue 静态文件并反向代理 `/api`，Spring Boot 只监听内网端口 8080，MySQL 8 使用专用最小权限账号。适用于 Ubuntu 22.04/24.04、Rocky Linux 9 等主流发行版。

```text
浏览器 ──HTTPS──> Nginx :443
                    ├── /        -> Vue dist
                    └── /api/**  -> Spring Boot 127.0.0.1:8080
                                         └── MySQL 127.0.0.1:3306
```

生产环境必须结合 [应用安全基线](SECURITY_BASELINE.md) 完成 TLS、主机加固、集中审计、备份恢复和权限复核。本手册不代表系统已通过等保三级测评。

## 1. 服务器规划

最低建议配置：

- 2 核 CPU、4GB 内存、20GB 可用磁盘
- OpenJDK 17 运行时
- MySQL 8
- Nginx 1.20+
- 仅开放 SSH、HTTP、HTTPS；禁止公网开放 8080 和 3306
- 系统时钟通过 NTP/chrony 同步

推荐目录：

```text
/opt/text-extract-tool/
├── app/extract-tool.jar
├── logs/
└── uploads/
/var/www/text-extract-tool/       # Vue dist
/etc/text-extract-tool/app.env    # 运行环境变量
/etc/systemd/system/text-extract-tool.service
```

## 2. 安装基础软件

以下命令仅作发行版示例；正式环境应使用组织批准的软件源和补丁版本。

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mysql-server nginx openssl
```

### Rocky Linux / RHEL

```bash
sudo dnf install -y java-17-openjdk-headless mysql-server nginx openssl
sudo systemctl enable --now mysqld nginx
```

验证：

```bash
java -version
mysql --version
nginx -v
```

推荐在开发机或 CI 构建产物，生产服务器不安装 Maven、Node.js 和 npm。

## 3. 在构建机生成发布产物

```bash
git clone https://github.com/pailchen101112/text-extract-tool.git
cd text-extract-tool

cd backend
mvn clean package

cd ../frontend
npm ci
npm audit --audit-level=moderate
npm run build
```

需要上传到服务器的内容：

- `backend/target/extract-tool-1.0.0.jar`
- `frontend/dist/` 中的全部文件
- 首次部署所需的 `init.sql`

示例：

```bash
scp backend/target/extract-tool-1.0.0.jar ops@SERVER:/tmp/extract-tool.jar
scp -r frontend/dist ops@SERVER:/tmp/text-extract-dist
scp init.sql ops@SERVER:/tmp/text-extract-init.sql
```

请将 `SERVER` 替换为实际主机名或 IP；正式环境建议由 CI/CD 或受控运维通道传输并校验制品哈希。

## 4. 创建系统用户和目录

```bash
sudo useradd --system --home-dir /opt/text-extract-tool --shell /usr/sbin/nologin text-extract
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/app
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/logs
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/uploads
sudo install -d -o root -g text-extract -m 750 /etc/text-extract-tool
sudo install -d -o root -g root -m 755 /var/www/text-extract-tool
```

部署后端制品：

```bash
sudo install -o text-extract -g text-extract -m 640 /tmp/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar
```

部署前端制品：

```bash
sudo cp -a /tmp/text-extract-dist/. /var/www/text-extract-tool/
sudo chown -R root:root /var/www/text-extract-tool
sudo find /var/www/text-extract-tool -type d -exec chmod 755 {} \;
sudo find /var/www/text-extract-tool -type f -exec chmod 644 {} \;
```

## 5. 初始化数据库和最小权限账号

首次部署先执行建库建表脚本：

```bash
sudo mysql < /tmp/text-extract-init.sql
```

进入 MySQL 创建运行账号。请替换强密码：

```sql
CREATE USER 'text_extract_app'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON text_extract_tool.* TO 'text_extract_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

应用运行账号不需要 `CREATE`、`ALTER`、`DROP`、`GRANT` 等权限。后续结构升级应由单独的受控数据库变更账号执行。

初始化脚本会创建 `admin / Admin@123456`。部署完成后必须立即登录并修改初始密码。

## 6. 配置运行环境变量

先生成 JWT 密钥：

```bash
openssl rand -base64 48
```

创建 `/etc/text-extract-tool/app.env`：

```dotenv
APP_JWT_SECRET=替换为上一步生成的随机密钥
APP_ALLOWED_BASE_PATHS=/opt/text-extract-tool/uploads
APP_TRUST_FORWARDED_FOR=true
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example.com
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/text_extract_tool?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=text_extract_app
SPRING_DATASOURCE_PASSWORD=替换为数据库强密码
SERVER_PORT=8080
```

设置权限：

```bash
sudo chown root:text-extract /etc/text-extract-tool/app.env
sudo chmod 640 /etc/text-extract-tool/app.env
```

注意：

- JWT 密钥至少 32 字节，缺失时应用会拒绝启动。
- 环境文件不得进入 Git、工单附件或普通聊天记录。
- 只有 Nginx 会清洗并重写 `X-Forwarded-For` 时才启用 `APP_TRUST_FORWARDED_FOR=true`。
- 若 MySQL 通过 TLS 连接，应按实际证书配置修改 JDBC URL，不要照搬示例中的 `useSSL=false`。

## 7. 配置 systemd

创建 `/etc/systemd/system/text-extract-tool.service`：

```ini
[Unit]
Description=Text Extract Tool Backend
After=network-online.target mysql.service
Wants=network-online.target

[Service]
Type=simple
User=text-extract
Group=text-extract
WorkingDirectory=/opt/text-extract-tool
EnvironmentFile=/etc/text-extract-tool/app.env
ExecStart=/usr/bin/java -Xms512m -Xmx1536m -jar /opt/text-extract-tool/app/extract-tool.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
UMask=0027

NoNewPrivileges=true
PrivateTmp=true
PrivateDevices=true
ProtectHome=true
ProtectSystem=strict
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
ReadWritePaths=/opt/text-extract-tool/logs /opt/text-extract-tool/uploads

[Install]
WantedBy=multi-user.target
```

加载并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now text-extract-tool
sudo systemctl status text-extract-tool --no-pager
sudo journalctl -u text-extract-tool -n 100 --no-pager
```

验证后端只监听本机端口：

```bash
ss -lntp | grep 8080
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/auth/login
```

第二条命令使用 GET 探测 POST 登录端点，正常可返回 `405`；返回 `000` 表示连接失败。

## 8. 配置 Nginx

准备 TLS 证书后创建 `/etc/nginx/conf.d/text-extract-tool.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.example.com;

    ssl_certificate     /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/private.key;
    ssl_protocols TLSv1.2 TLSv1.3;

    root /var/www/text-extract-tool;
    index index.html;
    client_max_body_size 100m;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy no-referrer always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'" always;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }
}
```

`X-Forwarded-For` 使用 `$remote_addr` 覆盖客户端传入值，避免伪造来源 IP 绕过限流或污染审计日志。

检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

访问 `https://your-domain.example.com`，使用初始管理员账号登录并立即修改密码。

## 9. 防火墙建议

以 UFW 为例：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

不要放行公网 8080 和 3306。若数据库位于独立服务器，只允许应用服务器的固定内网地址访问。

## 10. 日志

| 日志 | 默认位置 |
|---|---|
| systemd 标准输出 | `journalctl -u text-extract-tool` |
| 文件提取审计 | `/opt/text-extract-tool/logs/audit.log` |
| 账号和权限安全审计 | `/opt/text-extract-tool/logs/security-audit.log` |
| Nginx 访问/错误日志 | 发行版默认的 `/var/log/nginx/` |

应用日志会滚动压缩。等保场景应将安全日志同步到集中日志或 SIEM，并对登录失败、越权、账号与角色变更配置告警。

## 11. 升级发布

升级前备份数据库和当前制品：

```bash
sudo mysqldump --single-transaction --routines --triggers text_extract_tool > /var/backups/text_extract_tool_$(date +%Y%m%d_%H%M%S).sql
sudo cp /opt/text-extract-tool/app/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar.bak
sudo tar -C /var/www -czf /var/backups/text-extract-frontend_$(date +%Y%m%d_%H%M%S).tar.gz text-extract-tool
```

部署新版本：

```bash
sudo systemctl stop text-extract-tool
sudo install -o text-extract -g text-extract -m 640 /tmp/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar
sudo cp -a /tmp/text-extract-dist/. /var/www/text-extract-tool/
sudo chown -R root:root /var/www/text-extract-tool
sudo systemctl start text-extract-tool
sudo systemctl status text-extract-tool --no-pager
sudo nginx -t
sudo systemctl reload nginx
```

若新版本包含数据库变更，必须先阅读版本说明、备份，并通过受控变更流程执行迁移脚本。

## 12. 回滚

应用代码回滚：

```bash
sudo systemctl stop text-extract-tool
sudo cp /opt/text-extract-tool/app/extract-tool.jar.bak /opt/text-extract-tool/app/extract-tool.jar
sudo chown text-extract:text-extract /opt/text-extract-tool/app/extract-tool.jar
sudo chmod 640 /opt/text-extract-tool/app/extract-tool.jar
sudo systemctl start text-extract-tool
```

前端使用第 11 节生成的压缩包恢复。数据库只有在确认新版本写入了不兼容数据、且业务负责人批准后才能恢复备份；数据库恢复会覆盖部署后的业务数据。

## 13. 常见故障

### 服务启动失败

```bash
sudo systemctl status text-extract-tool --no-pager
sudo journalctl -u text-extract-tool -n 200 --no-pager
```

重点检查 JWT 密钥长度、数据库连接、环境文件权限和 Java 路径。

### Nginx 返回 502

确认后端正在监听 127.0.0.1:8080，并检查 systemd 与 Nginx 错误日志。

### 刷新页面返回 404

确认 Nginx 的 `location /` 包含 `try_files $uri $uri/ /index.html;`。

### 上传返回 413

确认 Nginx 的 `client_max_body_size`、Spring multipart 限制和应用文件大小限制一致；当前默认都是 100MB。

### 登录全部失败

确认 MySQL 中存在启用的账号，并检查服务器时间是否准确。若 JWT 密钥被轮换，旧令牌会失效，用户需要重新登录。

