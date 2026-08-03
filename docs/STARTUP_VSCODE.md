# VS Code 本地开发与启动手册

本文适用于在 Windows、macOS 或 Linux 上使用 VS Code 开发、调试本项目。默认前端端口为 `5173`，后端端口为 `8080`，数据库为本机 MySQL 8。

## 1. 准备环境

| 工具 | 版本要求 | 验证命令 |
|---|---|---|
| Git | 2.30+ | `git --version` |
| JDK | 8+；建议 17 或 21 | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| MySQL | 8.x | `mysql --version` |
| Node.js | 20.19+ 或 22.12+ | `node -v` |
| npm | 随 Node.js 安装 | `npm -v` |

建议安装 VS Code 扩展：

- Extension Pack for Java
- Vue - Official
- Maven for Java（已包含在部分 Java 扩展包中）

> VS Code 的 Java 语言服务本身可能要求较新的 JDK，但项目仍以 Java 8 字节码为目标。若扩展无法启动，优先为 VS Code 配置 JDK 21，再由 Maven 按 `pom.xml` 中的 Java 8 目标编译。

## 2. 获取并打开项目

```bash
git clone https://github.com/pailchen101112/text-extract-tool.git
cd text-extract-tool
code .
```

在 VS Code 中应直接打开仓库根目录，不要只打开 `backend` 或 `frontend` 子目录。

## 3. 初始化 MySQL

确保 MySQL 服务已启动，然后在 VS Code 集成终端中执行：

### macOS / Linux

```bash
mysql -u root -p < init.sql
```

### Windows PowerShell

```powershell
cmd /c "mysql -u root -p < init.sql"
```

验证初始化结果：

```bash
mysql -u root -p -e "USE text_extract_tool; SHOW TABLES;"
```

应包含 `extracted_document`、`sys_user`、`sys_role`、`sys_menu`、`sys_company`、`sys_position` 等表。

重复执行 `init.sql` 不会删除已有数据；生产环境升级前仍应先备份数据库。

## 4. 创建本地环境变量文件

在仓库根目录创建 `.env.local`。该文件已被 `.gitignore` 排除，不能提交到 Git：

```dotenv
APP_JWT_SECRET="请替换为至少32字节的本地随机密钥"
APP_ALLOWED_BASE_PATHS="/绝对路径/text-extract-tool/sample"
APP_TRUST_FORWARDED_FOR=false
SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/text_extract_tool?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true"
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD="你的本地MySQL密码"
```

生成 JWT 密钥：

### macOS / Linux

```bash
openssl rand -base64 48
```

### Windows PowerShell

```powershell
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Windows 的 `APP_ALLOWED_BASE_PATHS` 可以使用 `C:/work/text-extract-tool/sample` 形式的绝对路径。若 root 没有密码，将 `SPRING_DATASOURCE_PASSWORD=` 保持为空即可。

## 5. 使用终端启动

### 终端一：后端

macOS / Linux：

```bash
while IFS='=' read -r key value || [ -n "${key}" ]; do
  [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
  value="${value%$'\r'}"
  case "${value}" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  export "${key}=${value}"
done < .env.local
cd backend
mvn spring-boot:run
```

该读取方式只解析 `KEY=VALUE`，不会把密码中的 `$()`、反引号等内容当作 Shell 命令执行。不要用 `source .env.local` 代替。

Windows PowerShell：

```powershell
Get-Content .env.local | ForEach-Object {
  if ($_ -match '^([^#][^=]*)=(.*)$') {
    $value = $matches[2].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $value, 'Process')
  }
}
Set-Location backend
mvn spring-boot:run
```

看到 `Tomcat started on port(s): 8080` 表示后端启动成功。

### 终端二：前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

- 初始账号：`admin`
- 初始密码：`Admin@123456`
- 首次登录必须修改密码

前端开发服务器会把 `/api` 自动代理到 `http://localhost:8080`，无需额外修改跨域配置。

## 6. 使用 VS Code F5 调试后端

创建本地文件 `.vscode/launch.json`。整个 `.vscode` 目录已被 Git 忽略，不会泄漏本机配置：

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "调试后端 ExtractToolApplication",
      "request": "launch",
      "mainClass": "com.example.extracttool.ExtractToolApplication",
      "cwd": "${workspaceFolder}/backend",
      "envFile": "${workspaceFolder}/.env.local",
      "console": "integratedTerminal"
    }
  ]
}
```

打开 `backend/src/main/java/com/example/extracttool/ExtractToolApplication.java`，在需要的位置设置断点，然后在“运行和调试”中选择上述配置并按 F5。

前端仍在第二个终端运行 `npm run dev`。修改 Vue 文件后 Vite 会自动热更新。

## 7. 构建验证

提交代码前执行：

```bash
cd backend
mvn clean package

cd ../frontend
npm ci
npm audit --audit-level=moderate
npm run build
```

产物位置：

- 后端：`backend/target/extract-tool-1.0.0.jar`
- 前端：`frontend/dist/`

## 8. 停止服务

在两个运行终端中分别按 `Ctrl+C`。关闭 VS Code 前确认没有遗留的 8080 或 5173 端口进程：

### macOS / Linux

```bash
lsof -i :8080
lsof -i :5173
```

### Windows PowerShell

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -ErrorAction SilentlyContinue
```

## 9. 常见故障

### 后端提示 JWT 密钥不足 32 字节

检查 `.env.local` 的 `APP_JWT_SECRET`，并确认启动配置加载了 `envFile`。应用会在密钥缺失时主动拒绝启动。

### 数据库连接失败

检查 MySQL 服务、数据库名、端口、账号密码；确认 `SPRING_DATASOURCE_URL` 使用的是 MySQL 8 可访问地址。

### 前端返回 401

确认后端已启动。若令牌过期或后端重启后更换了 JWT 密钥，清除当前登录状态并重新登录。

### 服务器路径提取被拒绝

该功能只允许读取 `APP_ALLOWED_BASE_PATHS` 下的绝对路径；上传文件功能不受该路径配置影响，但仍受扩展名、MIME 与 100MB 限制。

### 5173 或 8080 端口被占用

结束占用进程；若修改端口，还需要同步调整 `frontend/vite.config.js` 的代理目标和后端允许来源。
