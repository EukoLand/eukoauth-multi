package land.euko.auth.client;

import land.euko.auth.client.handler.AuthHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class FabricAuthHandler {

    private static AuthHandler authHandler;

    public static void init() {
        authHandler = new AuthHandler(FabricLoader.getInstance().getConfigDir());

        ClientLoginConnectionEvents.INIT.register((handler, client) -> {
            String serverAddress = getServerAddr(handler);

            if (serverAddress == null) {
                System.err.println("[EukoAuth] Не удалось получить адрес сервера");
                return;
            }

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

    private static String getServerAddr(ClientHandshakePacketListenerImpl handshakePacketListener) {
        try {
            Connection connection = getConnection(handshakePacketListener);
            if (connection == null) {
                System.err.println("[EukoAuth] Connection == null (возможно, соединение ещё не установлено)");
                return null;
            }

            SocketAddress address = connection.getRemoteAddress();
            if (address instanceof InetSocketAddress inetAddress) {
                String host = inetAddress.getHostString();
                int port = inetAddress.getPort();
                System.out.println("[EukoAuth] Подключение к: " + host + ":" + port);
                return host + ":" + port;
            } else {
                System.err.println("[EukoAuth] Не удалось определить тип SocketAddress: " + address);
            }

        } catch (NoSuchFieldException e) {
            System.err.println("[EukoAuth] Поле 'connection' не найдено. Возможно, изменилась версия Minecraft или mappings.");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            System.err.println("[EukoAuth] Нет доступа к полю 'connection'.");
            e.printStackTrace();
        } catch (Throwable e) {
            System.err.println("[EukoAuth] Ошибка при получении адреса сервера:");
            e.printStackTrace();
        }

        return null;
    }

    private static Connection getConnection(ClientHandshakePacketListenerImpl handshakePacketListener) throws NoSuchFieldException, IllegalAccessException {
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();

        String mappedField = resolver.mapFieldName(
                "intermediary",
                "net.minecraft.class_635", // ClientHandshakePacketListenerImpl
                "field_3707",              // connection
                "Lnet/minecraft/class_2535;" // Connection
        );

        Field connectionField = handshakePacketListener.getClass().getDeclaredField(mappedField);
        connectionField.setAccessible(true);

        Connection connection = (Connection) connectionField.get(handshakePacketListener);
        return connection;
    }
}