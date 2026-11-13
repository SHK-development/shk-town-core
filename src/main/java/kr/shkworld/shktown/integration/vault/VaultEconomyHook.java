package kr.shkworld.shktown.integration.vault;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class VaultEconomyHook {
    public Optional<Economy> findProvider() {
        requireServerThread();
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? Optional.empty() : Optional.of(registration.getProvider());
    }

    public Economy requireProvider() {
        return findProvider().orElseThrow(() ->
                new IllegalStateException("No vault legacy economy provider is registered.")
        );
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Vault economy access must run on the server thread.");
        }
    }
}
