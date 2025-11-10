package land.euko.auth.client;

import land.euko.auth.client.handler.AuthHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class FabricAuthHandler {

    private static AuthHandler authHandler;
    private static String lastServerAddress = "";

    public static void init() {
        authHandler = new AuthHandler(FabricLoader.getInstance().getConfigDir());

        // Используем LOGIN событие - оно срабатывает во время handshake
        ClientLoginConnectionEvents.INIT.register((handler, client) -> {
            System.out.println("[EukoAuth] ClientLoginConnectionEvents.INIT triggered");

            // Пробуем получить адрес
            String serverAddress = lastServerAddress;
            System.out.println("[EukoAuth] Server address from cache: " + serverAddress);

            AuthHandler.IAuthCallback callback = new AuthHandler.IAuthCallback() {
                @Override
                public String getUsername() {
                    return client.getUser().getName();
                }

                @Override
                public void onError(String errorMessage) {
                    System.err.println("[EukoAuth] Ошибка авторизации: " + errorMessage);
                }

                @Override
                public void onSuccess() {
                    System.out.println("[EukoAuth] Запрос авторизации отправлен");
                }
            };

            authHandler.handleAuth(serverAddress, callback);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            authHandler.handleDisconnect();
        });

        System.out.println("[EukoAuth] ✓ Fabric handler инициализирован");
    }

    // Этот метод должен вызываться из миксина!
    public static void setServerAddress(String address) {
        lastServerAddress = address;
        System.out.println("[EukoAuth] Server address captured: " + address);
    }
}