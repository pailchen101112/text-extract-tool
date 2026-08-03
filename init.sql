-- 文本提取与检索工具 - 数据库初始化脚本
-- 用法: mysql -u root < init.sql   (brew 安装的 MySQL 默认 root 无密码)

CREATE DATABASE IF NOT EXISTS text_extract_tool
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE text_extract_tool;

-- 缓存表: 按文件名查询, 用 content_hash(SHA-256) 校验内容是否变化
CREATE TABLE IF NOT EXISTS extracted_document (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  file_name      VARCHAR(512) NOT NULL              COMMENT '文件名(缓存查询入口)',
  content_hash   CHAR(64)     NOT NULL              COMMENT '文件内容 SHA-256, 校验内容变化',
  file_path      VARCHAR(1024)                      COMMENT '服务器本地路径(上传方式为空)',
  file_size      BIGINT                             COMMENT '文件字节数',
  file_type      VARCHAR(64)                        COMMENT 'pdf/doc/docx 等',
  extracted_text LONGTEXT                            COMMENT 'Tika 提取的全文',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- content_hash 仅用于变更校验, 不设唯一约束(不同文件名可能内容相同, 各自缓存)
  KEY idx_hash (content_hash),
  KEY idx_file_name (file_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文本提取缓存表';

-- 组织、岗位、菜单、角色、用户：RBAC 权限模型
CREATE TABLE IF NOT EXISTS sys_company (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  parent_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司组织';

CREATE TABLE IF NOT EXISTS sys_position (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_position_company FOREIGN KEY (company_id) REFERENCES sys_company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位';

CREATE TABLE IF NOT EXISTS sys_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  path VARCHAR(128),
  icon VARCHAR(64),
  permission VARCHAR(128),
  type VARCHAR(16) NOT NULL DEFAULT 'MENU',
  sort_order INT NOT NULL DEFAULT 0,
  visible TINYINT(1) NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_menu_parent_sort (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单与权限点';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(512),
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  email VARCHAR(128),
  phone VARCHAR(32),
  company_id BIGINT,
  position_id BIGINT,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  failed_login_attempts INT NOT NULL DEFAULT 0,
  locked_until DATETIME,
  last_login_at DATETIME,
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  must_change_password TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES sys_company(id),
  CONSTRAINT fk_user_position FOREIGN KEY (position_id) REFERENCES sys_position(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id),
  CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';

INSERT IGNORE INTO sys_company (id, code, name, status) VALUES (1, 'HEADQUARTERS', '总公司', 'ENABLED');
INSERT IGNORE INTO sys_position (id, company_id, code, name, status) VALUES (1, 1, 'SYSTEM_ADMIN', '系统管理员', 'ENABLED');

INSERT IGNORE INTO sys_menu (id, parent_id, name, path, icon, permission, type, sort_order, visible, status) VALUES
  (1, NULL, '附件处理', NULL, 'FolderOpened', NULL, 'DIRECTORY', 10, 1, 'ENABLED'),
  (2, 1, '文本提取', '/attachments/extract', 'UploadFilled', 'attachment:extract', 'MENU', 11, 1, 'ENABLED'),
  (3, 1, '文本检索', '/attachments/search', 'Search', 'attachment:search', 'MENU', 12, 1, 'ENABLED'),
  (4, NULL, '浙江省 3D', '/zhejiang-3d', 'MapLocation', 'visualization:zhejiang:view', 'MENU', 20, 1, 'ENABLED'),
  (10, NULL, '系统管理', NULL, 'Setting', NULL, 'DIRECTORY', 30, 1, 'ENABLED'),
  (11, 10, '用户管理', '/system/users', 'User', 'system:user:list', 'MENU', 31, 1, 'ENABLED'),
  (12, 11, '用户写入', NULL, NULL, 'system:user:write', 'BUTTON', 32, 0, 'ENABLED'),
  (13, 10, '角色管理', '/system/roles', 'UserFilled', 'system:role:list', 'MENU', 33, 1, 'ENABLED'),
  (14, 13, '角色写入', NULL, NULL, 'system:role:write', 'BUTTON', 34, 0, 'ENABLED'),
  (15, 10, '公司管理', '/system/companies', 'OfficeBuilding', 'system:company:list', 'MENU', 35, 1, 'ENABLED'),
  (16, 15, '公司写入', NULL, NULL, 'system:company:write', 'BUTTON', 36, 0, 'ENABLED'),
  (17, 10, '岗位管理', '/system/positions', 'Postcard', 'system:position:list', 'MENU', 37, 1, 'ENABLED'),
  (18, 17, '岗位写入', NULL, NULL, 'system:position:write', 'BUTTON', 38, 0, 'ENABLED'),
  (19, 10, '菜单管理', '/system/menus', 'Menu', 'system:menu:list', 'MENU', 39, 1, 'ENABLED'),
  (20, 19, '菜单写入', NULL, NULL, 'system:menu:write', 'BUTTON', 40, 0, 'ENABLED');

INSERT IGNORE INTO sys_role (id, code, name, description, status)
VALUES (1, 'SUPER_ADMIN', '超级管理员', '拥有全部菜单与权限，仅用于受控管理', 'ENABLED');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;

-- 初始账号 admin / Admin@123456；首次登录强制修改。生产环境启用前必须更换。
INSERT IGNORE INTO sys_user
  (id, username, password_hash, display_name, company_id, position_id, status, must_change_password)
VALUES
  (1, 'admin', '$2a$12$nhp.163nBehN70dfHwGZKegAR0sljFLJftcCO9cyisJoniglAKq5O', '系统管理员', 1, 1, 'ENABLED', 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
