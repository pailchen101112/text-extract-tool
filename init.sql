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
