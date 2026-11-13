package ca.emilybelnavis.mcmods.createtransit;

import ca.emilybelnavis.mcmods.createtransit.economy.EconomyBridge;
import ca.emilybelnavis.mcmods.createtransit.fare.FarePolicy;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(CreateTransit.MOD_ID)
public final class CreateTransit {
    public static final String MOD_ID = "create_transit";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateTransit() {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("Create Transit foundation loaded; Economy banking API {} required",
                EconomyBridge.REQUIRED_BANKING_VERSION);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("transit")
                .then(Commands.literal("status").executes(context -> {
                    var status = EconomyBridge.status(context.getSource().getServer());
                    context.getSource().sendSuccess(
                            () -> Component.translatable("commands.create_transit.status", status), false);
                    return status == EconomyBridge.Status.READY ? 1 : 0;
                }))
                .then(Commands.literal("fare")
                        .then(Commands.argument("start_zone", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .then(Commands.argument("end_zone", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            int start = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "start_zone");
                                            int end = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "end_zone");
                                            var fare = FarePolicy.DEFAULT.quote(start, end);
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "commands.create_transit.fare", fare.zoneDifference(),
                                                    fare.totalCad().toPlainString()), false);
                                            return 1;
                                        })))));
    }
}
