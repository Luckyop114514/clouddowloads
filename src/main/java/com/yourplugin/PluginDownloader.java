package com.lazyop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class PluginDownloader extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private final Gson gson = new Gson();

    @Override
    public void onEnable() {
        getCommand("cldw").setExecutor(this);
        getCommand("cldw").setTabCompleter(this);
        getLogger().info("PluginDownloader v1.0 作者: Lazyop 已启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("PluginDownloader v1.0 作者: Lazyop 已关闭！");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.isOp()) {
                player.sendMessage("§c你必须拥有OP权限才能使用此命令！");
                return true;
            }
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "modrinth":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /cldw modrinth <MC版本> <slug/ID>");
                    sender.sendMessage("§7示例: /cldw modrinth 1.21.1 worldedit");
                    return true;
                }
                String mcVersion = args[1];
                String query = args.length > 2 ? args[2] : "";
                
                if (query.isEmpty()) {
                    sender.sendMessage("§c请提供项目名称或ID");
                    sender.sendMessage("§7示例: /cldw modrinth 1.21.1 worldedit");
                    return true;
                }
                
                sender.sendMessage("§e正在从Modrinth搜索: §7" + query + " §e(MC " + mcVersion + ")");
                final String finalQuery = query;
                final String finalMcVersion = mcVersion;
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    downloadFromModrinth(sender, finalQuery, finalMcVersion);
                });
                break;
                
            case "singlefile":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /cldw singlefile <url>");
                    return true;
                }
                String singleUrl = args[1];
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    downloadSingleFile(sender, singleUrl);
                });
                break;
                
            case "multiplefiles":
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /cldw multiplefiles <url1>,<url2>");
                    return true;
                }
                String urlsString = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                String[] urls = urlsString.split(",");
                
                List<String> urlList = new ArrayList<>();
                for (String url : urls) {
                    String trimmedUrl = url.trim();
                    if (!trimmedUrl.isEmpty()) {
                        urlList.add(trimmedUrl);
                    }
                }
                
                if (urlList.isEmpty()) {
                    sender.sendMessage("§c错误：没有提供有效的URL！");
                    return true;
                }
                
                sender.sendMessage("§e开始批量下载，共 §7" + urlList.size() + " §e个文件...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    downloadMultipleFiles(sender, urlList);
                });
                break;
                
            default:
                sendUsage(sender);
                break;
        }
        
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6========== PluginDownloader v1.0 ==========");
        sender.sendMessage("§6作者: Lazyop");
        sender.sendMessage("");
        sender.sendMessage("§e/cldw modrinth <MC版本> <slug/ID>");
        sender.sendMessage("§7  从Modrinth下载指定MC版本的插件");
        sender.sendMessage("§7  示例: /cldw modrinth 1.21.1 worldedit");
        sender.sendMessage("§7  示例: /cldw modrinth 1.21.1 luckperms");
        sender.sendMessage("");
        sender.sendMessage("§e/cldw singlefile <url>");
        sender.sendMessage("§7  从URL下载单个插件");
        sender.sendMessage("");
        sender.sendMessage("§e/cldw multiplefiles <url1>,<url2>");
        sender.sendMessage("§7  批量下载多个插件（用逗号分隔）");
        sender.sendMessage("§6========================================");
    }

    private void downloadFromModrinth(CommandSender sender, String query, String mcVersion) {
        try {
            // 1. 先尝试直接通过ID/slug获取
            JsonObject project = getProjectById(query);
            
            // 2. 如果直接获取失败，尝试搜索
            if (project == null) {
                sender.sendMessage("§7正在搜索...");
                project = searchModrinthProject(query);
            }
            
            if (project == null) {
                sender.sendMessage("§c错误：未找到项目: " + query);
                return;
            }
            
            String projectId = getStringSafely(project, "id", "");
            String title = getStringSafely(project, "title", query);
            String slug = getStringSafely(project, "slug", query);
            
            if (projectId.isEmpty() && project.has("project_id")) {
                projectId = getStringSafely(project, "project_id", "");
            }
            
            if (projectId.isEmpty()) {
                sender.sendMessage("§c错误：无法获取项目ID");
                return;
            }
            
            sender.sendMessage("§a✓ 找到项目: §f" + title);
            
            // 3. 获取版本列表
            sender.sendMessage("§e正在获取版本列表...");
            JsonArray versions = getModrinthVersions(projectId);
            if (versions == null || versions.isEmpty()) {
                sender.sendMessage("§c错误：该项目没有可用的版本！");
                return;
            }
            
            // 4. 查找指定MC版本
            JsonObject targetVersion = findVersionByMcVersion(sender, versions, mcVersion);
            if (targetVersion == null) {
                sender.sendMessage("§c错误：未找到支持MC " + mcVersion + " 的版本！");
                return;
            }
            
            String versionNumber = getStringSafely(targetVersion, "version_number", "未知");
            String versionName = getStringSafely(targetVersion, "name", versionNumber);
            
            sender.sendMessage("§a选择的版本: §f" + versionName + " (" + versionNumber + ")");
            
            // 5. 找到主文件
            JsonArray files = targetVersion.getAsJsonArray("files");
            JsonObject mainFile = null;
            
            for (JsonElement fileElement : files) {
                JsonObject file = fileElement.getAsJsonObject();
                boolean primary = file.has("primary") && !file.get("primary").isJsonNull() 
                    && file.get("primary").getAsBoolean();
                if (primary) {
                    mainFile = file;
                    break;
                }
                if (mainFile == null) {
                    mainFile = file;
                }
            }
            
            if (mainFile == null) {
                sender.sendMessage("§c错误：未找到可下载的文件！");
                return;
            }
            
            // 6. 下载
            String fileUrl = getStringSafely(mainFile, "url", "");
            String fileName = getStringSafely(mainFile, "filename", slug + ".jar");
            long fileSize = mainFile.has("size") && !mainFile.get("size").isJsonNull()
                ? mainFile.get("size").getAsLong() : 0;
            
            sender.sendMessage("§7文件: §f" + fileName + " §7(" + formatFileSize(fileSize) + ")");
            sender.sendMessage("§e正在下载...");
            
            Path pluginsFolder = Paths.get("plugins");
            Path targetFile = pluginsFolder.resolve(fileName);
            
            if (Files.exists(targetFile)) {
                sender.sendMessage("§e警告: 文件已存在，将覆盖...");
            }
            
            downloadFileFromUrl(fileUrl, targetFile);
            
            sender.sendMessage("§a✓ 下载成功！");
            sender.sendMessage("§7  文件: " + fileName);
            sender.sendMessage("§7  保存至: plugins/" + fileName);
            sender.sendMessage("§e提示: 请重启服务器或使用/reload加载插件");
            
        } catch (Exception e) {
            sender.sendMessage("§c从Modrinth下载失败: " + e.getMessage());
            getLogger().log(Level.SEVERE, "从Modrinth下载失败: " + query, e);
        }
    }

    private String getStringSafely(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private JsonObject getProjectById(String idOrSlug) {
        try {
            String apiUrl = MODRINTH_API + "/project/" + idOrSlug;
            return makeApiRequest(apiUrl);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject searchModrinthProject(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String apiUrl = MODRINTH_API + "/search?query=" + encodedQuery + "&limit=10";
        
        JsonObject response = makeApiRequest(apiUrl);
        if (response == null) return null;
        
        JsonArray hits = response.getAsJsonArray("hits");
        if (hits == null || hits.isEmpty()) return null;
        
        // 优先精确匹配slug
        for (JsonElement hitElement : hits) {
            JsonObject hit = hitElement.getAsJsonObject();
            String slug = getStringSafely(hit, "slug", "");
            String title = getStringSafely(hit, "title", "");
            String projectId = getStringSafely(hit, "project_id", "");
            
            if (slug.equalsIgnoreCase(query) || title.equalsIgnoreCase(query) || projectId.equals(query)) {
                return hit;
            }
        }
        
        return hits.get(0).getAsJsonObject();
    }

    private JsonArray getModrinthVersions(String projectId) throws Exception {
        String apiUrl = MODRINTH_API + "/project/" + projectId + "/version";
        JsonElement response = makeApiRequestRaw(apiUrl);
        
        if (response != null && response.isJsonArray()) {
            return response.getAsJsonArray();
        }
        return null;
    }

    private JsonObject findVersionByMcVersion(CommandSender sender, JsonArray versions, String mcVersion) {
        getLogger().info("查找支持MC " + mcVersion + " 的版本...");
        
        // 精确匹配
        for (JsonElement versionElement : versions) {
            JsonObject version = versionElement.getAsJsonObject();
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            if (gameVersions != null) {
                for (JsonElement gvElement : gameVersions) {
                    if (gvElement.getAsString().equals(mcVersion)) {
                        getLogger().info("精确匹配: " + mcVersion);
                        return version;
                    }
                }
            }
        }
        
        sender.sendMessage("§c未找到支持MC " + mcVersion + " 的版本");
        return null;
    }

    private void downloadSingleFile(CommandSender sender, String urlString) {
        boolean success = downloadFile(sender, urlString, 1, 1);
        if (success) {
            sender.sendMessage("§a单文件下载完成！");
        } else {
            sender.sendMessage("§c单文件下载失败！");
        }
    }

    private void downloadMultipleFiles(CommandSender sender, List<String> urls) {
        int totalFiles = urls.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            int currentFile = i + 1;
            
            sender.sendMessage("§e[" + currentFile + "/" + totalFiles + "] §7正在下载: " + url);
            
            boolean success = downloadFile(sender, url, currentFile, totalFiles);
            
            if (success) {
                successCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
            }
            
            if (i < urls.size() - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        sender.sendMessage("§6========== 批量下载总结 ==========");
        sender.sendMessage("§a成功下载: §7" + successCount.get() + " §a个文件");
        if (failCount.get() > 0) {
            sender.sendMessage("§c下载失败: §7" + failCount.get() + " §c个文件");
        }
        sender.sendMessage("§e总计处理: §7" + totalFiles + " §e个文件");
    }

    private boolean downloadFile(CommandSender sender, String urlString, int currentFile, int totalFiles) {
        try {
            URI uri = new URI(urlString);
            URL url = uri.toURL();
            
            String fileName = getFileNameFromUrl(urlString);
            if (!fileName.endsWith(".jar")) {
                sender.sendMessage("§c[" + currentFile + "/" + totalFiles + "] 错误：URL必须指向.jar文件！");
                return false;
            }

            Path pluginsFolder = Paths.get("plugins");
            Path targetFile = pluginsFolder.resolve(fileName);
            
            if (Files.exists(targetFile)) {
                sender.sendMessage("§e[" + currentFile + "/" + totalFiles + "] 警告: §7" + fileName + " §e已存在，将覆盖...");
            }

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Lazyop/PluginDownloader/1.0");
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                sender.sendMessage("§c[" + currentFile + "/" + totalFiles + "] 错误：HTTP " + responseCode);
                return false;
            }

            long fileSize = connection.getContentLengthLong();
            sender.sendMessage("§7[" + currentFile + "/" + totalFiles + "] 文件: §f" + fileName + " §7(" + formatFileSize(fileSize) + ")");
            
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            sender.sendMessage("§a[" + currentFile + "/" + totalFiles + "] ✓ 下载成功: §7" + fileName);
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage("§c[" + currentFile + "/" + totalFiles + "] ✗ 下载失败: " + e.getMessage());
            return false;
        }
    }

    private void downloadFileFromUrl(String urlString, Path targetFile) throws Exception {
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Lazyop/PluginDownloader/1.0");
        connection.setInstanceFollowRedirects(true);
        
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private JsonObject makeApiRequest(String apiUrl) throws Exception {
        JsonElement element = makeApiRequestRaw(apiUrl);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    private JsonElement makeApiRequestRaw(String apiUrl) throws Exception {
        URI uri = new URI(apiUrl);
        URL url = uri.toURL();
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Lazyop/PluginDownloader/1.0");
        connection.setRequestProperty("Accept", "application/json");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return null;
        }
        
        try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8)) {
            String jsonString = scanner.useDelimiter("\\A").next();
            return JsonParser.parseString(jsonString);
        }
    }

    private String getFileNameFromUrl(String urlString) {
        try {
            URI uri = new URI(urlString);
            String path = uri.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) {
                return "plugin.jar";
            }
            return fileName;
        } catch (Exception e) {
            return "plugin.jar";
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "未知";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                               @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("modrinth".startsWith(input)) {
                completions.add("modrinth");
            }
            if ("singlefile".startsWith(input)) {
                completions.add("singlefile");
            }
            if ("multiplefiles".startsWith(input)) {
                completions.add("multiplefiles");
            }
        }
        
        return completions;
    }
}