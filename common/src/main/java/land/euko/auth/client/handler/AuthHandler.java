package land.euko.auth.client.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class AuthHandler {

    private static final Gson gson = new Gson();
    private static final String AUTH_API_URL = "http://localhost:8080/mc/auth";

    private static final Set<String> TRUSTED_SERVERS = Set.of(
            "euko.land",
            "play.euko.land",
            "localhost"
    );

    private final Path configDir;

    public AuthHandler(Path configDir) {
        this.configDir = configDir;
    }

    public void handleAuth(String serverAddress, IAuthCallback callback) {
        String normalized = normalizeServerAddress(serverAddress);

        if (!TRUSTED_SERVERS.contains(normalized)) {
            System.out.println("[EukoAuth] Сервер не требует авторизации: " + normalized);
            return;
        }

        System.out.println("[EukoAuth] Начало авторизации на сервере: " + normalized);

        // Выполняем авторизацию асинхронно
        CompletableFuture.runAsync(() -> {
            try {
                String token = loadAuthToken();

                if (!normalized.equals("localhost")) {
                    if ("invalid-token".equals(token)) {
                        callback.onError("Не удалось загрузить токен авторизации");
                        return;
                    }
                }

                boolean success = sendAuthRequest(token, callback.getUsername());

                if (success) {
                    System.out.println("[EukoAuth] ✓ Авторизация успешна");
                    callback.onSuccess();
                } else {
                    callback.onError("Ошибка авторизации на сервере");
                }

            } catch (Exception e) {
                System.err.println("[EukoAuth] ✗ Критическая ошибка: " + e.getMessage());
                e.printStackTrace();
                callback.onError("Неполадки с авторизацией: " + e.getMessage());
            }
        });
    }

    private boolean sendAuthRequest(String token, String username) throws IOException {
        URL url = new URL(AUTH_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            // Подготовка данных
            JsonObject data = new JsonObject();
            data.addProperty("nickname", username);
            data.addProperty("system", System.getProperty("os.name"));
            data.addProperty("host", getComputerName());

            String jsonData = gson.toJson(data);
            System.out.println("[EukoAuth] Отправка данных: " + jsonData);

            // Отправка запроса
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[EukoAuth] Код ответа: " + responseCode);

            if (responseCode == 200) {
                return true;
            } else {
                // Читаем сообщение об ошибке
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    System.err.println("[EukoAuth] Ошибка сервера: " + response);
                }
                return false;
            }

        } finally {
            conn.disconnect();
        }
    }

    private String getComputerName() {
        String hostname = System.getenv("COMPUTERNAME");
        if (hostname == null) {
            hostname = System.getenv("HOSTNAME");
        }
        if (hostname == null) {
            try {
                hostname = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                hostname = "Unknown";
            }
        }
        return hostname;
    }

    public void handleDisconnect() {
        System.out.println("[EukoAuth] Отключение от сервера");
    }

    private String normalizeServerAddress(String address) {
        if (address == null) return "";

        address = address.toLowerCase().trim();

        if (address.contains(":")) {
            address = address.split(":")[0];
        }

        return address;
    }

    private String loadAuthToken() {
        try {
            // Читаем файл из корня jar-файла
            InputStream inputStream = AuthHandler.class.getResourceAsStream("/token");

            if (inputStream == null) {
                System.err.println("[EukoAuth] ✗ Файл токена не найден внутри jar");
                return "invalid-token";
            }

            String token = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            inputStream.close();

            if (token.isEmpty()) {
                System.err.println("[EukoAuth] ✗ Файл token пустой");
                return "invalid-token";
            }

            System.out.println("[EukoAuth] ✓ Токен успешно загружен из jar: "+token);
            return token;

        } catch (Exception e) {
            System.err.println("[EukoAuth] ✗ Ошибка загрузки токена: " + e.getMessage());
            e.printStackTrace();
            return "invalid-token";
        }
    }

    public interface IAuthCallback {
        String getUsername();
        void onError(String message);
        void onSuccess();
    }
}