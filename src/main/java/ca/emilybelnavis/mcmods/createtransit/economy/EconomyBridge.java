package ca.emilybelnavis.mcmods.createtransit.economy;

import ca.emilybelnavis.mcmods.economy.Economy;
import ca.emilybelnavis.mcmods.economy.api.BankingIntegration;
import ca.emilybelnavis.mcmods.economy.api.Identifiers.*;
import ca.emilybelnavis.mcmods.economy.api.Money;
import ca.emilybelnavis.mcmods.economy.api.PartyRef;
import ca.emilybelnavis.mcmods.economy.card.CardAccess;
import ca.emilybelnavis.mcmods.economy.card.CardStackIdentity;
import ca.emilybelnavis.mcmods.economy.foundation.PartyAuthorization;
import ca.emilybelnavis.mcmods.economy.foundation.ServerServices;
import ca.emilybelnavis.mcmods.economy.journal.Journal;
import ca.emilybelnavis.mcmods.economy.journal.JournalEngine;
import ca.emilybelnavis.mcmods.economy.journal.JournalService;
import ca.emilybelnavis.mcmods.economy.overdraft.Overdrafts;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Trusted server adapter. No packet, command, or GUI exposes money movement in this milestone. */
public final class EconomyBridge {
    public static final int REQUIRED_BANKING_VERSION = 3;

    public enum Status { NOT_STARTED, STARTING, STOPPING, STOPPED, UNAVAILABLE, INCOMPATIBLE_API, READY }

    private EconomyBridge() {}

    public static Status status(MinecraftServer server) {
        requireServerThread(server);
        var services = Economy.serverServices(server);
        if (services == null) return Status.NOT_STARTED;
        return switch (services.state()) {
            case INITIALIZING -> Status.STARTING;
            case STOPPING -> Status.STOPPING;
            case STOPPED -> Status.STOPPED;
            case UNAVAILABLE -> Status.UNAVAILABLE;
            case READY -> services.ledger().version() == REQUIRED_BANKING_VERSION ? Status.READY : Status.INCOMPATIBLE_API;
        };
    }

    /** Resolve exactly the clicked hand, then recheck the live player/hand before returning. */
    public static CompletionStage<Optional<CardAccess>> presentedCard(ServerPlayer player, InteractionHand hand) {
        Objects.requireNonNull(hand);
        var session = authenticate(player);
        var stack = player.getItemInHand(hand);
        var mark = CardStackIdentity.mark(stack, session.services().worldId());
        if (mark.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var original = mark.orElseThrow();
        var design = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var result = new CompletableFuture<Optional<CardAccess>>();
        session.services().ledger().usableCardAccess(new PlayerId(player.getUUID()),
                session.services().worldId(), original.token()).whenComplete((access, failure) ->
                player.server.execute(() -> {
                    if (failure != null) {
                        result.completeExceptionally(failure);
                        return;
                    }
                    if (!current(player, session.services())
                            || !CardStackIdentity.mark(player.getItemInHand(hand), session.services().worldId())
                                    .filter(original::equals).isPresent()
                            || !BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).toString().equals(design)) {
                        result.complete(Optional.empty());
                        return;
                    }
                    result.complete(access.filter(card -> card.design().equals(design)));
                }));
        return result;
    }

    /**
     * Low-level settlement for a server-validated, durably recorded transit invoice.
     * Callers must validate the presented debit card and freeze the recipient/amount before use.
     * This method does not issue transit value or mark a journey paid.
     */
    public static JournalService.Submission debit(ServerPlayer player, Debit invoice) {
        var session = authenticate(player);
        return session.banking().debitAccount(session.grant(), invoice.operation(), invoice.attempt(),
                invoice.source(), invoice.destination(), invoice.amount(), Overdrafts.DebitKind.INTEGRATION_OVERRIDE);
    }

    /** Query the original canonical invoice. Absence or failure never automatically resubmits. */
    public static CompletionStage<Optional<Journal.Outcome>> lookupDebit(ServerPlayer player, Debit invoice) {
        var session = authenticate(player);
        return session.services().lookupOwned(session.grant(), Overdrafts.DEBIT,
                invoice.operation(), invoice.attempt(), debitLookupRequest(session.grant().target(), invoice),
                new JournalEngine.Registration(1, (intent, reads) -> {
                    throw new IllegalStateException("Transit outcome lookup cannot settle a payment");
                }));
    }

    static Journal.CanonicalRequest debitLookupRequest(PartyRef owner, Debit invoice) {
        var request = Overdrafts.debitRequest(owner, invoice.source().value(), invoice.destination().value(),
                invoice.amount(), Overdrafts.DebitKind.INTEGRATION_OVERRIDE);
        var fields = new TreeMap<>(request.fields());
        // Economy.lookupOwned restores this reserved field from the fresh grant, exactly as debitAccount does.
        fields.remove("economy:owner");
        return new Journal.CanonicalRequest(request.version(), fields);
    }

    public record Debit(OperationId operation, RequestId attempt, AccountId source, AccountId destination, Money amount) {
        public Debit {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(attempt);
            Objects.requireNonNull(source);
            Objects.requireNonNull(destination);
            Objects.requireNonNull(amount);
            if (source.equals(destination) || amount.minorUnits() <= 0) {
                throw new IllegalArgumentException("Transit debit requires distinct accounts and a positive amount");
            }
        }
    }

    private record Session(ServerServices services, BankingIntegration banking, PartyAuthorization.Grant grant) {}

    private static Session authenticate(ServerPlayer player) {
        Objects.requireNonNull(player);
        var server = player.server;
        if (status(server) != Status.READY) throw new IllegalStateException("Economy banking API 3 is not ready");
        var services = Economy.serverServices(server);
        var target = new PartyRef(services.worldId(), new PartyRef.Player(new PlayerId(player.getUUID())));
        var grant = PartyAuthorization.player(player, Economy::serverServices, target)
                .orElseThrow(() -> new IllegalStateException("Current authenticated player required"));
        return new Session(services, services.ledger(), grant);
    }

    private static boolean current(ServerPlayer player, ServerServices services) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && Economy.serverServices(player.server) == services
                && services.state() == ServerServices.State.READY;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
    }
}
