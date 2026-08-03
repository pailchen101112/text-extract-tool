# Linux 服务器生产部署手册

本文给出单机生产部署基线：Nginx 托管 Vue 静态文件并反向代理 `/api`，Spring Boot 只监听本机端口 8080，同机 MySQL 8 使用专用最小权限账号和 TLS。适用于 Ubuntu 22.04/24.04、Rocky Linux 9 等主流发行版。

```text
浏览器 ──HTTPS──> Nginx :443
                    ├── /        -> Vue dist
                    └── /api/**  -> Spring Boot 127.0.0.1:8080
                                         └──TLS──> MySQL 127.0.0.1:3306
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
/var/www/text-extract-tool/
├── releases/<版本号>/            # 各版本 Vue dist
└── current -> releases/<版本号>  # Nginx 当前版本软链接
/etc/text-extract-tool/app.env    # 运行环境变量
/etc/systemd/system/text-extract-tool.service
```

## 2. 安装基础软件

以下命令仅作发行版示例；正式环境应使用组织批准的软件源和补丁版本。

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless mysql-server nginx openssl jq ufw
```

### Rocky Linux / RHEL

```bash
sudo dnf install -y java-17-openjdk-headless mysql-server nginx openssl jq policycoreutils firewalld
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
sudo groupadd --system text-extract
sudo useradd --system --gid text-extract --home-dir /opt/text-extract-tool --shell /usr/sbin/nologin text-extract
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/app
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/logs
sudo install -d -o text-extract -g text-extract -m 750 /opt/text-extract-tool/uploads
sudo install -d -o root -g text-extract -m 750 /etc/text-extract-tool
sudo install -d -o root -g root -m 755 /var/www/text-extract-tool/releases
sudo install -d -o root -g root -m 700 /var/backups/text-extract-tool
```

部署后端制品：

```bash
sudo install -o text-extract -g text-extract -m 640 /tmp/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar
```

部署前端制品：

```bash
set -euo pipefail
FRONTEND_RELEASE="$(date +%Y%m%d_%H%M%S)"
sudo install -d -o root -g root -m 755 "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}"
sudo cp -a /tmp/text-extract-dist/. "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}/"
sudo chown -R root:root "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}"
sudo find "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" -type d -exec chmod 755 {} \;
sudo find "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" -type f -exec chmod 644 {} \;
test -s "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}/index.html"
sudo ln -sfn "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" /var/www/text-extract-tool/.current-next
sudo mv -Tf /var/www/text-extract-tool/.current-next /var/www/text-extract-tool/current
```

Rocky Linux / RHEL 启用了 SELinux 时，再恢复静态文件安全上下文：

```bash
sudo restorecon -RFv /var/www/text-extract-tool
```

## 5. 初始化数据库和最小权限账号

首次部署先执行建库建表脚本：

```bash
sudo mysql < /tmp/text-extract-init.sql
```

进入 MySQL 创建运行账号。请替换强密码：

```sql
SHOW VARIABLES LIKE 'have_ssl';
CREATE USER 'text_extract_app'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON text_extract_tool.* TO 'text_extract_app'@'127.0.0.1';
CREATE USER 'text_extract_backup'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_ANOTHER_STRONG_PASSWORD' REQUIRE SSL;
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON text_extract_tool.* TO 'text_extract_backup'@'127.0.0.1';
FLUSH PRIVILEGES;
```

`have_ssl` 必须为 `YES`；否则先按发行版的 MySQL 文档配置服务端证书并重启数据库。应用运行账号不需要 `CREATE`、`ALTER`、`DROP`、`GRANT` 等权限，备份账号也不得供应用使用。后续结构升级应由单独的受控数据库变更账号执行。

将 MySQL CA 证书安装到应用专用信任库；`keytool` 会交互式要求设置一个新的信任库密码：

```bash
sudo install -o root -g text-extract -m 640 /var/lib/mysql/ca.pem /etc/text-extract-tool/mysql-ca.pem
sudo keytool -importcert -noprompt -alias text-extract-mysql-ca \
  -file /etc/text-extract-tool/mysql-ca.pem \
  -keystore /etc/text-extract-tool/mysql-truststore.p12 \
  -storetype PKCS12
sudo chown root:text-extract /etc/text-extract-tool/mysql-truststore.p12
sudo chmod 640 /etc/text-extract-tool/mysql-truststore.p12
```

`/var/lib/mysql/ca.pem` 是常见默认路径；若 DBA 使用组织签发的 CA，请将命令中的来源路径替换为实际 CA 文件。

若数据库独立部署，不要混用本节命令：应由 DBA 在目标库执行初始化、限制账号来源为应用服务器固定内网 IP，并提供受信 CA；备份命令也必须显式连接同一个目标库。

初始化脚本会创建 `admin / Admin@123456`。必须按第 8 节在开放 Nginx 前完成初始改密。

## 6. 配置运行环境变量

先生成 JWT 密钥：

```bash
openssl rand -base64 48
```

创建 `/etc/text-extract-tool/app.env`：

```dotenv
APP_JWT_SECRET="替换为上一步生成的随机密钥"
APP_ALLOWED_BASE_PATHS="/opt/text-extract-tool/uploads"
APP_TRUST_FORWARDED_FOR=true
APP_CORS_ALLOWED_ORIGINS="https://your-domain.example.com"
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/text_extract_tool?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=file:/etc/text-extract-tool/mysql-truststore.p12&trustCertificateKeyStorePassword=替换为信任库密码&trustCertificateKeyStoreType=PKCS12&serverTimezone=UTC&characterEncoding=utf8"
SPRING_DATASOURCE_USERNAME=text_extract_app
SPRING_DATASOURCE_PASSWORD="替换为数据库强密码"
SERVER_ADDRESS=127.0.0.1
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
- 密码等值若包含空格或 `#`，必须像示例一样使用双引号包围。
- 只有 Nginx 会清洗并重写 `X-Forwarded-For` 时才启用 `APP_TRUST_FORWARDED_FOR=true`。
- 示例使用 `sslMode=VERIFY_CA` 校验同机 MySQL 的证书链。信任库密码若含 URL 保留字符，须先进行百分号编码；为减少配置错误，建议使用不含保留字符的高熵随机值。
- 禁止在生产环境降级为 `DISABLED`、`PREFERRED` 或 `useSSL=false`。连接远程数据库时应改用 `VERIFY_IDENTITY`，并使用与证书 DNS 名称一致的主机名。

## 7. 配置 systemd

创建 `/etc/systemd/system/text-extract-tool.service`：

```ini
[Unit]
Description=Text Extract Tool Backend
After=network-online.target mysql.service mysqld.service
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
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'Content-Type: application/json' \
  -d '{}' \
  http://127.0.0.1:8080/api/auth/login
```

第二条命令向登录端点发送空的 JSON，正常应返回参数校验错误 `400`；返回 `000` 表示连接失败。

## 8. 上线前修改初始管理员密码

此时后端仅监听 `127.0.0.1`，尚未开放 Nginx。先在服务器本地修改默认密码：

```bash
umask 077
ADMIN_SECRET_DIR="$(mktemp -d)"
trap 'rm -rf -- "${ADMIN_SECRET_DIR}"' EXIT
read -r -s -p '新的 admin 密码: ' ADMIN_NEW_PASSWORD
echo
printf '%s' '{"username":"admin","password":"Admin@123456"}' > "${ADMIN_SECRET_DIR}/login.json"
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  --data-binary "@${ADMIN_SECRET_DIR}/login.json" \
  http://127.0.0.1:8080/api/auth/login > "${ADMIN_SECRET_DIR}/login-response.json"
ADMIN_TOKEN="$(jq -er '.token' "${ADMIN_SECRET_DIR}/login-response.json")"
ADMIN_NEW_PASSWORD_JSON="$(printf '%s' "${ADMIN_NEW_PASSWORD}" | jq -Rs .)"
printf '{"oldPassword":"Admin@123456","newPassword":%s}' "${ADMIN_NEW_PASSWORD_JSON}" > "${ADMIN_SECRET_DIR}/change.json"
printf 'header = "Authorization: Bearer %s"\n' "${ADMIN_TOKEN}" > "${ADMIN_SECRET_DIR}/auth.curl"
curl --fail --silent --show-error --config "${ADMIN_SECRET_DIR}/auth.curl" -X PUT \
  -H 'Content-Type: application/json' \
  --data-binary "@${ADMIN_SECRET_DIR}/change.json" \
  http://127.0.0.1:8080/api/auth/change-password
rm -rf -- "${ADMIN_SECRET_DIR}"
trap - EXIT
unset ADMIN_SECRET_DIR ADMIN_NEW_PASSWORD ADMIN_NEW_PASSWORD_JSON ADMIN_TOKEN
```

临时目录权限受 `umask 077` 保护，密码和令牌不会出现在进程参数中，并会在异常退出时清理。新密码必须为 12–64 位，包含大写字母、小写字母、数字和特殊字符，且不能包含账号名。命令成功返回“密码修改成功，请重新登录”。随后应建立具名管理员账号，并限制默认超级管理员的日常使用。

## 9. 配置 Nginx

Ubuntu / Debian 的 Nginx 包通常已启用默认站点。先把它移到不会被 Nginx `include` 的目录，避免重复声明 `default_server`；文件不存在则跳过：

```bash
if [ -e /etc/nginx/sites-enabled/default ] || [ -L /etc/nginx/sites-enabled/default ]; then
  sudo install -d -o root -g root -m 755 /etc/nginx/disabled-sites
  sudo mv /etc/nginx/sites-enabled/default /etc/nginx/disabled-sites/default
fi
```

准备 TLS 证书后创建 `/etc/nginx/conf.d/text-extract-tool.conf`：

```nginx
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    return 444;
}

server {
    listen 80;
    listen [::]:80;
    server_name your-domain.example.com;
    return 301 https://your-domain.example.com$request_uri;
}

server {
    listen 443 ssl http2 default_server;
    listen [::]:443 ssl http2 default_server;
    server_name your-domain.example.com;

    if ($host != your-domain.example.com) { return 444; }

    ssl_certificate     /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/private.key;
    ssl_protocols TLSv1.2 TLSv1.3;

    root /var/www/text-extract-tool/current;
    index index.html;
    client_max_body_size 110m;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy no-referrer always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
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

启用示例中的 HSTS 前，应确认域名及其所有子域均已永久启用 HTTPS；否则去掉 `includeSubDomains`，避免影响其他子域。

Rocky Linux / RHEL 启用了 SELinux 时，允许 Nginx 连接本机后端；否则反向代理通常会返回 502：

```bash
sudo setsebool -P httpd_can_network_connect 1
sudo restorecon -RFv /var/www/text-extract-tool
```

检查并重载：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

访问 `https://your-domain.example.com`，使用第 8 节设置的新密码登录。

## 10. 防火墙建议

Ubuntu / Debian 使用 UFW：

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

Rocky Linux / RHEL 使用 firewalld：

```bash
sudo systemctl enable --now firewalld
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo firewall-cmd --list-services
```

不要放行公网 8080 和 3306。若数据库位于独立服务器，只允许应用服务器的固定内网地址访问。

## 11. 日志

| 日志 | 默认位置 |
|---|---|
| systemd 标准输出 | `journalctl -u text-extract-tool` |
| 文件提取审计 | `/opt/text-extract-tool/logs/audit.log` |
| 账号和权限安全审计 | `/opt/text-extract-tool/logs/security-audit.log` |
| Nginx 访问/错误日志 | 发行版默认的 `/var/log/nginx/` |

应用日志会滚动压缩。等保场景应将安全日志同步到集中日志或 SIEM，并对登录失败、越权、账号与角色变更配置告警。

## 12. 升级发布

首次部署时创建仅 root 可读的 `/etc/text-extract-tool/backup.cnf`，密码填写第 5 节创建的备份账号密码：

```ini
[client]
user=text_extract_backup
password="替换为备份账号强密码"
host=127.0.0.1
ssl-mode=VERIFY_CA
ssl-ca=/etc/text-extract-tool/mysql-ca.pem
```

```bash
sudo chown root:root /etc/text-extract-tool/backup.cnf
sudo chmod 600 /etc/text-extract-tool/backup.cnf
```

升级前备份数据库和当前制品：

```bash
set -euo pipefail
BACKUP_STAMP="$(date +%Y%m%d_%H%M%S)"
DATABASE_BACKUP="/var/backups/text-extract-tool/text_extract_tool_${BACKUP_STAMP}.sql"
FRONTEND_BACKUP="/var/backups/text-extract-tool/frontend_${BACKUP_STAMP}.tar.gz"
DATABASE_BACKUP_TMP="${DATABASE_BACKUP}.tmp"
FRONTEND_BACKUP_TMP="${FRONTEND_BACKUP}.tmp"
JAR_BACKUP_TMP=/opt/text-extract-tool/app/extract-tool.jar.bak.tmp
trap 'sudo rm -f -- "${DATABASE_BACKUP_TMP}" "${FRONTEND_BACKUP_TMP}" "${JAR_BACKUP_TMP}"' EXIT
sudo sh -c 'umask 077; exec mysqldump --defaults-extra-file=/etc/text-extract-tool/backup.cnf --single-transaction --no-tablespaces --triggers text_extract_tool > "$1"' sh "${DATABASE_BACKUP_TMP}"
sudo tar -C /var/www/text-extract-tool/current -czf "${FRONTEND_BACKUP_TMP}" .
sudo cp /opt/text-extract-tool/app/extract-tool.jar "${JAR_BACKUP_TMP}"
sudo test -s "${DATABASE_BACKUP_TMP}"
sudo test -s "${FRONTEND_BACKUP_TMP}"
sudo test -s "${JAR_BACKUP_TMP}"
sudo mv "${DATABASE_BACKUP_TMP}" "${DATABASE_BACKUP}"
sudo mv "${FRONTEND_BACKUP_TMP}" "${FRONTEND_BACKUP}"
sudo mv "${JAR_BACKUP_TMP}" /opt/text-extract-tool/app/extract-tool.jar.bak
trap - EXIT
```

部署新版本：

```bash
set -euo pipefail
FRONTEND_RELEASE="$(date +%Y%m%d_%H%M%S)"
sudo install -d -o root -g root -m 755 "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}"
sudo cp -a /tmp/text-extract-dist/. "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}/"
sudo chown -R root:root "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}"
sudo find "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" -type d -exec chmod 755 {} \;
sudo find "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" -type f -exec chmod 644 {} \;
if command -v restorecon >/dev/null 2>&1; then
  sudo restorecon -RF /var/www/text-extract-tool
fi
test -s "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}/index.html"
sudo nginx -t
sudo install -o text-extract -g text-extract -m 640 /tmp/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar.new
sudo systemctl stop text-extract-tool
sudo mv /opt/text-extract-tool/app/extract-tool.jar.new /opt/text-extract-tool/app/extract-tool.jar

probe_backend() {
  local backend_http_code=000
  local attempt
  for attempt in {1..15}; do
    backend_http_code="$(curl --silent --connect-timeout 1 --max-time 2 \
      --output /dev/null --write-out '%{http_code}' \
      -H 'Content-Type: application/json' -d '{}' \
      http://127.0.0.1:8080/api/auth/login || true)"
    if [ "${backend_http_code}" = '400' ] && sudo systemctl is-active --quiet text-extract-tool; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if ! sudo systemctl start text-extract-tool || ! probe_backend; then
  sudo journalctl -u text-extract-tool -n 100 --no-pager
  if sudo systemctl stop text-extract-tool && \
     sudo cp /opt/text-extract-tool/app/extract-tool.jar.bak /opt/text-extract-tool/app/extract-tool.jar && \
     sudo chown text-extract:text-extract /opt/text-extract-tool/app/extract-tool.jar && \
     sudo chmod 640 /opt/text-extract-tool/app/extract-tool.jar && \
     sudo systemctl start text-extract-tool && probe_backend; then
    echo '新后端验证失败，已恢复并验证旧 JAR；前端未切换。'
  else
    echo '新旧后端均未通过验证，前端未切换，请立即人工恢复。' >&2
  fi
  exit 1
fi
sudo ln -sfn "/var/www/text-extract-tool/releases/${FRONTEND_RELEASE}" /var/www/text-extract-tool/.current-next
sudo mv -Tf /var/www/text-extract-tool/.current-next /var/www/text-extract-tool/current
sudo systemctl reload nginx
```

若新版本包含数据库变更，必须先阅读版本说明、备份，并通过受控变更流程执行迁移脚本。

发布成功后只保留最近 5 个前端 release；当前软链接指向的版本始终跳过删除：

```bash
CURRENT_FRONTEND="$(readlink -f /var/www/text-extract-tool/current)"
mapfile -t STALE_RELEASES < <(ls -1dt /var/www/text-extract-tool/releases/* | tail -n +6)
for stale_release in "${STALE_RELEASES[@]}"; do
  [ "$(readlink -f "${stale_release}")" = "${CURRENT_FRONTEND}" ] || sudo rm -rf -- "${stale_release}"
done
```

本机备份也必须按组织策略设置保留期并同步到异机介质。先按制度设置天数并仅预览待删除文件：

```bash
BACKUP_RETENTION_DAYS=30
case "${BACKUP_RETENTION_DAYS}" in ''|*[!0-9]*) echo '保留天数必须是非负整数'; exit 1 ;; esac
sudo find /var/backups/text-extract-tool -mindepth 1 -maxdepth 1 -type f -mtime "+${BACKUP_RETENTION_DAYS}" -print
```

逐项核对预览清单后，在同一终端明确输入 `DELETE` 才执行删除：

```bash
read -r -p '确认永久删除上述过期本机备份？输入 DELETE: ' CONFIRM_BACKUP_DELETE
[ "${CONFIRM_BACKUP_DELETE}" = 'DELETE' ] || { echo '已取消'; exit 1; }
sudo find /var/backups/text-extract-tool -mindepth 1 -maxdepth 1 -type f -mtime "+${BACKUP_RETENTION_DAYS}" -delete
unset CONFIRM_BACKUP_DELETE
```

## 13. 回滚

应用代码回滚：

```bash
set -euo pipefail
probe_backend() {
  local backend_http_code=000
  local attempt
  for attempt in {1..15}; do
    backend_http_code="$(curl --silent --connect-timeout 1 --max-time 2 \
      --output /dev/null --write-out '%{http_code}' \
      -H 'Content-Type: application/json' -d '{}' \
      http://127.0.0.1:8080/api/auth/login || true)"
    if [ "${backend_http_code}" = '400' ] && sudo systemctl is-active --quiet text-extract-tool; then
      return 0
    fi
    sleep 2
  done
  return 1
}

sudo test -s /opt/text-extract-tool/app/extract-tool.jar.bak
sudo cp /opt/text-extract-tool/app/extract-tool.jar /opt/text-extract-tool/app/extract-tool.jar.pre-rollback
sudo systemctl stop text-extract-tool
sudo cp /opt/text-extract-tool/app/extract-tool.jar.bak /opt/text-extract-tool/app/extract-tool.jar
sudo chown text-extract:text-extract /opt/text-extract-tool/app/extract-tool.jar
sudo chmod 640 /opt/text-extract-tool/app/extract-tool.jar
if ! sudo systemctl start text-extract-tool || ! probe_backend; then
  sudo journalctl -u text-extract-tool -n 100 --no-pager
  if sudo systemctl stop text-extract-tool && \
     sudo cp /opt/text-extract-tool/app/extract-tool.jar.pre-rollback /opt/text-extract-tool/app/extract-tool.jar && \
     sudo chown text-extract:text-extract /opt/text-extract-tool/app/extract-tool.jar && \
     sudo chmod 640 /opt/text-extract-tool/app/extract-tool.jar && \
     sudo systemctl start text-extract-tool && probe_backend; then
    echo '旧 JAR 回滚失败，已恢复回滚前版本。'
  else
    echo '回滚版本和回滚前版本均未通过验证，请立即人工恢复。' >&2
  fi
  exit 1
fi
sudo rm -f /opt/text-extract-tool/app/extract-tool.jar.pre-rollback
```

前端先列出保留版本，再将软链接原子切换到明确选择的旧版本：

```bash
set -euo pipefail
ls -1dt /var/www/text-extract-tool/releases/*
ROLLBACK_FRONTEND=/var/www/text-extract-tool/releases/替换为旧版本目录名
test -d "${ROLLBACK_FRONTEND}" && test -f "${ROLLBACK_FRONTEND}/index.html" || { echo '回滚目录无效'; exit 1; }
ORIGINAL_FRONTEND="$(readlink -f /var/www/text-extract-tool/current)"
test -d "${ORIGINAL_FRONTEND}" && test -f "${ORIGINAL_FRONTEND}/index.html"
sudo nginx -t
sudo ln -sfn "${ROLLBACK_FRONTEND}" /var/www/text-extract-tool/.current-next
sudo mv -Tf /var/www/text-extract-tool/.current-next /var/www/text-extract-tool/current
if ! curl --fail --silent --show-error --connect-timeout 3 --max-time 10 https://your-domain.example.com/ >/dev/null; then
  if sudo ln -sfn "${ORIGINAL_FRONTEND}" /var/www/text-extract-tool/.current-next && \
     sudo mv -Tf /var/www/text-extract-tool/.current-next /var/www/text-extract-tool/current; then
    echo '前端回滚验证失败，已恢复原链接。' >&2
  else
    echo '前端回滚验证失败且原链接恢复失败，请立即人工恢复。' >&2
  fi
  exit 1
fi
```

如果目录版本不可用，可使用第 12 节生成的前端压缩包恢复。数据库只有在确认新版本写入了不兼容数据、且业务负责人批准后才能恢复备份；数据库恢复会覆盖部署后的业务数据。

## 14. 常见故障

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

应用限制为 100MB，Nginx 示例预留 multipart 协议开销并设置为 110MB。若自行调整，应确保 Nginx 限制略高于应用限制。

### 登录全部失败

确认 MySQL 中存在启用的账号，并检查服务器时间是否准确。若 JWT 密钥被轮换，旧令牌会失效，用户需要重新登录。
