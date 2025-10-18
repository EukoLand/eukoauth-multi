package land.euko.auth.client;

import net.fabricmc.api.ClientModInitializer;

public class MainClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricAuthHandler.init();
    }
}
