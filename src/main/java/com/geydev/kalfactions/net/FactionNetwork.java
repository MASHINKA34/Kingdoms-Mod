package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientFactionPayloadHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FactionNetwork {
    private static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                FactionPayloads.C2SOpenTable.TYPE,
                FactionPayloads.C2SOpenTable.STREAM_CODEC,
                FactionNetwork::handleOpen
        );
        registrar.playToServer(
                FactionPayloads.C2SCreateFaction.TYPE,
                FactionPayloads.C2SCreateFaction.STREAM_CODEC,
                FactionNetwork::handleCreate
        );
        registrar.playToServer(
                FactionPayloads.C2SUpdateFaction.TYPE,
                FactionPayloads.C2SUpdateFaction.STREAM_CODEC,
                FactionNetwork::handleUpdate
        );
        registrar.playToServer(
                FactionPayloads.C2SSetClaim.TYPE,
                FactionPayloads.C2SSetClaim.STREAM_CODEC,
                FactionNetwork::handleClaim
        );
        registrar.playToServer(
                FactionPayloads.C2SDepositTreasury.TYPE,
                FactionPayloads.C2SDepositTreasury.STREAM_CODEC,
                FactionNetwork::handleDeposit
        );
        registrar.playToServer(
                FactionPayloads.C2SWithdrawTreasury.TYPE,
                FactionPayloads.C2SWithdrawTreasury.STREAM_CODEC,
                FactionNetwork::handleWithdraw
        );
        registrar.playToServer(
                FactionPayloads.C2SKickMember.TYPE,
                FactionPayloads.C2SKickMember.STREAM_CODEC,
                FactionNetwork::handleKick
        );
        registrar.playToServer(
                FactionPayloads.C2SSetMemberRole.TYPE,
                FactionPayloads.C2SSetMemberRole.STREAM_CODEC,
                FactionNetwork::handleSetRole
        );
        registrar.playToServer(
                FactionPayloads.C2SSetPvp.TYPE,
                FactionPayloads.C2SSetPvp.STREAM_CODEC,
                FactionNetwork::handleSetPvp
        );
        registrar.playToServer(
                FactionPayloads.C2SLeaveFaction.TYPE,
                FactionPayloads.C2SLeaveFaction.STREAM_CODEC,
                FactionNetwork::handleLeave
        );
        registrar.playToServer(
                FactionPayloads.C2SDisbandFaction.TYPE,
                FactionPayloads.C2SDisbandFaction.STREAM_CODEC,
                FactionNetwork::handleDisband
        );
        registrar.playToServer(
                FactionPayloads.C2SInvitePlayer.TYPE,
                FactionPayloads.C2SInvitePlayer.STREAM_CODEC,
                FactionNetwork::handleInvite
        );
        registrar.playToServer(
                FactionPayloads.C2STransferLeadership.TYPE,
                FactionPayloads.C2STransferLeadership.STREAM_CODEC,
                FactionNetwork::handleTransfer
        );
        registrar.playToServer(
                FactionPayloads.C2SRequestAlliance.TYPE,
                FactionPayloads.C2SRequestAlliance.STREAM_CODEC,
                FactionNetwork::handleRequestAlliance
        );
        registrar.playToServer(
                FactionPayloads.C2SBreakAlliance.TYPE,
                FactionPayloads.C2SBreakAlliance.STREAM_CODEC,
                FactionNetwork::handleBreakAlliance
        );
        registrar.playToServer(
                FactionPayloads.C2SDeclareWar.TYPE,
                FactionPayloads.C2SDeclareWar.STREAM_CODEC,
                FactionNetwork::handleDeclareWar
        );
        registrar.playToServer(
                FactionPayloads.C2SEndWar.TYPE,
                FactionPayloads.C2SEndWar.STREAM_CODEC,
                FactionNetwork::handleEndWar
        );
        registrar.playToServer(
                FactionPayloads.C2SMapSetClaims.TYPE,
                FactionPayloads.C2SMapSetClaims.STREAM_CODEC,
                FactionNetwork::handleMapSetClaims
        );
        registrar.playToServer(
                FactionPayloads.C2SSetEmblem.TYPE,
                FactionPayloads.C2SSetEmblem.STREAM_CODEC,
                FactionNetwork::handleSetEmblem
        );
        registrar.playToServer(
                FactionPayloads.C2SSetChestMode.TYPE,
                FactionPayloads.C2SSetChestMode.STREAM_CODEC,
                FactionNetwork::handleSetChestMode
        );
        registrar.playToServer(
                FactionPayloads.C2SEditChestWhitelist.TYPE,
                FactionPayloads.C2SEditChestWhitelist.STREAM_CODEC,
                FactionNetwork::handleEditChestWhitelist
        );
        registrar.playToServer(
                FactionPayloads.C2SRequestFactionList.TYPE,
                FactionPayloads.C2SRequestFactionList.STREAM_CODEC,
                FactionNetwork::handleRequestFactionList
        );
        registrar.playToServer(
                FactionPayloads.C2SRespondInvite.TYPE,
                FactionPayloads.C2SRespondInvite.STREAM_CODEC,
                FactionNetwork::handleRespondInvite
        );
        registrar.playToServer(
                FactionPayloads.C2SRespondAlliance.TYPE,
                FactionPayloads.C2SRespondAlliance.STREAM_CODEC,
                FactionNetwork::handleRespondAlliance
        );
        registrar.playToClient(
                FactionPayloads.S2CFactionList.TYPE,
                FactionPayloads.S2CFactionList.STREAM_CODEC,
                FactionNetwork::handleFactionList
        );
        registrar.playToClient(
                FactionPayloads.S2CChestAccessState.TYPE,
                FactionPayloads.S2CChestAccessState.STREAM_CODEC,
                FactionNetwork::handleChestAccessState
        );
        registrar.playToClient(
                FactionPayloads.S2CFactionState.TYPE,
                FactionPayloads.S2CFactionState.STREAM_CODEC,
                FactionNetwork::handleState
        );
        registrar.playToClient(
                FactionPayloads.S2CSyncClaims.TYPE,
                FactionPayloads.S2CSyncClaims.STREAM_CODEC,
                FactionNetwork::handleSyncClaims
        );
        registrar.playToClient(
                FactionPayloads.S2CFactionNotice.TYPE,
                FactionPayloads.S2CFactionNotice.STREAM_CODEC,
                FactionNetwork::handleNotice
        );
    }

    private static void handleOpen(FactionPayloads.C2SOpenTable payload, IPayloadContext context) {
        FactionServerHooks.openFor(serverPlayer(context), payload.tablePos(), payload.silent());
    }

    private static void handleCreate(FactionPayloads.C2SCreateFaction payload, IPayloadContext context) {
        FactionServerHooks.create(
                serverPlayer(context),
                payload.tablePos(),
                payload.name(),
                payload.color(),
                payload.bonuses(),
                payload.emblem(),
                payload.emblemUrl()
        );
    }

    private static void handleSetEmblem(FactionPayloads.C2SSetEmblem payload, IPayloadContext context) {
        FactionServerHooks.setEmblem(
                serverPlayer(context),
                payload.tablePos(),
                payload.emblem(),
                payload.emblemUrl()
        );
    }

    private static void handleUpdate(FactionPayloads.C2SUpdateFaction payload, IPayloadContext context) {
        FactionServerHooks.update(serverPlayer(context), payload.tablePos(), payload.name(), payload.color());
    }

    private static void handleClaim(FactionPayloads.C2SSetClaim payload, IPayloadContext context) {
        FactionServerHooks.setClaim(
                serverPlayer(context),
                payload.tablePos(),
                payload.chunkX(),
                payload.chunkZ(),
                payload.claimed()
        );
    }

    private static void handleDeposit(FactionPayloads.C2SDepositTreasury payload, IPayloadContext context) {
        FactionServerHooks.deposit(serverPlayer(context), payload.tablePos(), payload.amount());
    }

    private static void handleWithdraw(FactionPayloads.C2SWithdrawTreasury payload, IPayloadContext context) {
        FactionServerHooks.withdraw(serverPlayer(context), payload.tablePos(), payload.amount());
    }

    private static void handleKick(FactionPayloads.C2SKickMember payload, IPayloadContext context) {
        FactionServerHooks.kickMember(serverPlayer(context), payload.tablePos(), payload.playerId());
    }

    private static void handleSetRole(FactionPayloads.C2SSetMemberRole payload, IPayloadContext context) {
        FactionServerHooks.setMemberRole(serverPlayer(context), payload.tablePos(), payload.playerId(), payload.role());
    }

    private static void handleSetPvp(FactionPayloads.C2SSetPvp payload, IPayloadContext context) {
        FactionServerHooks.setPvp(serverPlayer(context), payload.tablePos(), payload.enabled());
    }

    private static void handleLeave(FactionPayloads.C2SLeaveFaction payload, IPayloadContext context) {
        FactionServerHooks.leave(serverPlayer(context), payload.tablePos());
    }

    private static void handleDisband(FactionPayloads.C2SDisbandFaction payload, IPayloadContext context) {
        FactionServerHooks.disband(serverPlayer(context), payload.tablePos());
    }

    private static void handleInvite(FactionPayloads.C2SInvitePlayer payload, IPayloadContext context) {
        FactionServerHooks.invite(serverPlayer(context), payload.tablePos(), payload.targetName());
    }

    private static void handleTransfer(FactionPayloads.C2STransferLeadership payload, IPayloadContext context) {
        FactionServerHooks.transfer(serverPlayer(context), payload.tablePos(), payload.targetName());
    }

    private static void handleRequestAlliance(FactionPayloads.C2SRequestAlliance payload, IPayloadContext context) {
        FactionServerHooks.requestAlliance(
                serverPlayer(context),
                payload.tablePos(),
                payload.targetFactionName()
        );
    }

    private static void handleBreakAlliance(FactionPayloads.C2SBreakAlliance payload, IPayloadContext context) {
        FactionServerHooks.breakAlliance(
                serverPlayer(context),
                payload.tablePos(),
                payload.targetFactionName()
        );
    }

    private static void handleDeclareWar(FactionPayloads.C2SDeclareWar payload, IPayloadContext context) {
        FactionServerHooks.declareWar(serverPlayer(context), payload.tablePos(), payload.targetFactionName());
    }

    private static void handleEndWar(FactionPayloads.C2SEndWar payload, IPayloadContext context) {
        FactionServerHooks.endWar(serverPlayer(context), payload.tablePos());
    }

    private static void handleMapSetClaims(FactionPayloads.C2SMapSetClaims payload, IPayloadContext context) {
        FactionServerHooks.mapSetClaims(serverPlayer(context), payload.claimed(), payload.chunks());
    }

    private static void handleSetChestMode(FactionPayloads.C2SSetChestMode payload, IPayloadContext context) {
        FactionServerHooks.setChestMode(serverPlayer(context), payload.pos(), payload.mode());
    }

    private static void handleEditChestWhitelist(
            FactionPayloads.C2SEditChestWhitelist payload,
            IPayloadContext context
    ) {
        FactionServerHooks.editChestWhitelist(
                serverPlayer(context),
                payload.pos(),
                payload.add(),
                payload.targetId(),
                payload.targetName()
        );
    }

    private static void handleRequestFactionList(
            FactionPayloads.C2SRequestFactionList payload,
            IPayloadContext context
    ) {
        FactionServerHooks.sendFactionList(serverPlayer(context));
    }

    private static void handleRespondInvite(FactionPayloads.C2SRespondInvite payload, IPayloadContext context) {
        FactionServerHooks.respondInvite(serverPlayer(context), payload.factionId(), payload.accept());
    }

    private static void handleRespondAlliance(FactionPayloads.C2SRespondAlliance payload, IPayloadContext context) {
        FactionServerHooks.respondAlliance(serverPlayer(context), payload.factionId(), payload.accept());
    }

    private static void handleFactionList(FactionPayloads.S2CFactionList payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handleFactionList(payload);
        }
    }

    private static void handleChestAccessState(FactionPayloads.S2CChestAccessState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handleChestAccess(payload);
        }
    }

    private static void handleState(FactionPayloads.S2CFactionState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handle(payload);
        }
    }

    private static void handleSyncClaims(FactionPayloads.S2CSyncClaims payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handleSyncClaims(payload);
        }
    }

    private static void handleNotice(FactionPayloads.S2CFactionNotice payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handleNotice(payload);
        }
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            return player;
        }
        throw new IllegalStateException("Received a faction C2S payload without a server player");
    }

    private FactionNetwork() {
    }
}
