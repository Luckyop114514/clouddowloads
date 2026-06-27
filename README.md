# Clouddowloads

一个简单实用的 Bukkit/Paper 插件下载工具，支持从 URL 或 Modrinth 直接下载插件到服务器的 plugins 文件夹。

## 功能特性

- 多源下载 - 支持直接 URL 下载和 Modrinth 平台下载
- 批量下载 - 支持逗号分隔多个 URL 批量下载插件
- 版本控制 - Modrinth 下载时可精确指定 Minecraft 版本
- 权限管理 - 仅 OP 玩家和控制台可使用，安全可控
- 异步下载 - 不阻塞服务器主线程，下载过程流畅
- 进度提示 - 实时显示文件大小和下载进度
- 中文支持 - 完整的中文提示信息

## 命令列表

| 命令 | 说明 | 示例 |
|------|------|------|
| `/cldw modrinth <MC版本> <slug>` | 从 Modrinth 下载指定版本的插件 | `/cldw modrinth 1.21.1 worldedit` |
| `/cldw singlefile <url>` | 从 URL 下载单个插件 | `/cldw singlefile https://example.com/plugin.jar` |
| `/cldw multiplefiles <url1>,<url2>` | 批量下载多个插件 | `/cldw multiplefiles https://a.com/1.jar,https://b.com/2.jar` |

## 快速开始

### 安装
1. 从 Releases 下载最新版本
2. 将 `PluginDownloader-1.0.jar` 放入服务器的 `plugins` 文件夹
3. 重启服务器或执行 `/reload`

### 使用示例

```bash
# 从 Modrinth 下载 WorldEdit（1.21.1 版本）
/cldw modrinth 1.21.1 worldedit

# 从 Modrinth 下载 LuckPerms
/cldw modrinth 1.21.1 luckperms

# 从 URL 下载单个插件
/cldw singlefile https://cdn.example.com/EssentialsX.jar

# 批量下载多个插件
/cldw multiplefiles https://cdn.example.com/plugin1.jar,https://cdn.example.com/plugin2.jar
```

## 环境要求

- **服务器**: Paper 1.21.11+ (兼容所有 1.21.x 版本)
- **Java**: 21+
- **权限**: OP 或控制台

## 编译

```bash
git clone https://github.com/Lazyop/PluginDownloader.git
cd PluginDownloader
mvn clean package
```

编译产物在 `target/PluginDownloader-1.0.jar`

## 安全提示

- 请仅从可信来源下载插件
- 妥善保管 OP 权限
- 下载的插件需重启服务器才能生效
