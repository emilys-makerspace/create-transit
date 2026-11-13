package ca.emilybelnavis.mcmods.createtransit.smoke;

import ca.emilybelnavis.mcmods.economy.api.BankingIntegration;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in development source set, excluded from both release and sources jars. */
@Mod("create_transit_smoke")
public final class StartupSmoke {
    private boolean completed;

    public StartupSmoke() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (completed || !(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
        var expected = Map.of("create_transit", "0.1.0-alpha", "create", "6.0.10", "economy", "0.6.0-alpha",
                "neoforge", "21.1.252", "minecraft", "1.21.1");
        expected.forEach((id, version) -> {
            var mod = ModList.get().getModContainerById(id).orElseThrow();
            if (!version.equals(mod.getModInfo().getVersion().toString())) {
                throw new IllegalStateException("Unexpected version for " + id + ": " + mod.getModInfo().getVersion());
            }
        });
        for (String id : new String[] {"flywheel", "ponder"}) {
            if (!ModList.get().isLoaded(id)) throw new IllegalStateException("Missing runtime dependency: " + id);
        }
        // Registrate and SQLite are runtime libraries, not FML mod containers.
        try {
            Class.forName("com.tterrag.registrate.Registrate", false, getClass().getClassLoader());
            ca.emilybelnavis.mcmods.economy.storage.SqliteStorage.verifyDriver();
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Missing Registrate runtime library", missing);
        }
        if (BankingIntegration.VERSION != 3) throw new IllegalStateException("Expected Economy banking API 3");
        var report = Path.of(System.getProperty("create_transit.smokeReport"));
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "passed", true, "screen", "TitleScreen", "mods", expected,
                    "java", System.getProperty("java.version"), "bankingApi", BankingIntegration.VERSION)) + "\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write startup report", failure);
        }
        completed = true;
        client.stop();
    }
}
