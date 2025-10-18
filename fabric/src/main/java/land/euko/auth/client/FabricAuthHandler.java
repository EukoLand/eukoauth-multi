package land.euko.auth.client;

import land.euko.auth.client.handler.AuthHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class FabricAuthHandler {

    private static AuthHandler authHandler;

    public static void init() {
        authHandler = new AuthHandler(FabricLoader.getInstance().getConfigDir());

        // Авторизация при подключении к серверу
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String serverAddress = getServerAddress(client);

            AuthHandler.IAuthCallback callback = new AuthHandler.IAuthCallback() {
                @Override
                public String getUsername() {
                    return client.player != null ? client.player.getName().getString() : "Unknown";
                }

                @Override
                public void onError(String errorMessage) {
                    if (client.player != null) {
                        client.player.connection.getConnection().disconnect(
                                Component.literal("§c[EukoAuth] " + errorMessage)
                        );
                    }
                }

                @Override
                public void onSuccess() {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("§a[EukoAuth] Авторизация успешна!"),
                                false
                        );
                    }
                }
            };

            authHandler.handleAuth(serverAddress, callback);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            authHandler.handleDisconnect();
        });

        System.out.println("[EukoAuth] ✓ Fabric handler инициализирован");
    }

    private static String getServerAddress(Minecraft client) {
        if (client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        }
        return "";
    }
}