/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.support.dispute;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.support.SupportType;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.Contract;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
@Getter
public final class Dispute implements NetworkPayload, PersistablePayload {
    private final String tradeId;
    private final String id;
    private final int traderId;
    private final boolean disputeOpenerIsBuyer;
    private final boolean disputeOpenerIsMaker;
    // PubKeyRing of trader who opened the dispute
    private final PubKeyRing traderPubKeyRing;
    private final long tradeDate;
    private final Contract contract;
    @Nullable
    private final byte[] contractHash;
    @Nullable
    private final byte[] depositTxSerialized;
    @Nullable
    private final byte[] payoutTxSerialized;
    @Nullable
    private final String depositTxId;
    @Nullable
    private final String payoutTxId;
    private final String contractAsJson;
    @Nullable
    private final String makerContractSignature;
    @Nullable
    private final String takerContractSignature;
    private final PubKeyRing agentPubKeyRing; // dispute agent
    private final boolean isSupportTicket;
    private final ObservableList<ChatMessage> chatMessages = FXCollections.observableArrayList();
    private final BooleanProperty isClosedProperty = new SimpleBooleanProperty();
    // disputeResultProperty.get is Nullable!
    private final ObjectProperty<DisputeResult> disputeResultProperty = new SimpleObjectProperty<>();
    private final long openingDate;
    @Nullable
    @Setter
    private String disputePayoutTxId;
    @Setter
    // Added v1.2.0
    private SupportType supportType;
    // Only used at refundAgent so that he knows how the mediator resolved the case
    @Setter
    @Nullable
    private String mediatorsDisputeResult;
    @Setter
    @Nullable
    private String delayedPayoutTxId;
    
    // Added for XMR integration
    private boolean isOpener;

    // Added at v1.3.9
    @Setter
    @Nullable
    private String donationAddressOfDelayedPayoutTx;
    // We do not persist uid, it is only used by dispute agents to guarantee an uid.
    @Setter
    @Nullable
    private transient String uid;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Dispute(long openingDate,
                   String tradeId,
                   int traderId,
                   boolean isOpener,
                   boolean disputeOpenerIsBuyer,
                   boolean disputeOpenerIsMaker,
                   PubKeyRing traderPubKeyRing,
                   long tradeDate,
                   Contract contract,
                   @Nullable byte[] contractHash,
                   @Nullable byte[] depositTxSerialized,
                   @Nullable byte[] payoutTxSerialized,
                   @Nullable String depositTxId,
                   @Nullable String payoutTxId,
                   String contractAsJson,
                   @Nullable String makerContractSignature,
                   @Nullable String takerContractSignature,
                   PubKeyRing agentPubKeyRing,
                   boolean isSupportTicket,
                   SupportType supportType) {
        this.openingDate = openingDate;
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.isOpener = isOpener;
        this.disputeOpenerIsBuyer = disputeOpenerIsBuyer;
        this.disputeOpenerIsMaker = disputeOpenerIsMaker;
        this.traderPubKeyRing = traderPubKeyRing;
        this.tradeDate = tradeDate;
        this.contract = contract;
        this.contractHash = contractHash;
        this.depositTxSerialized = depositTxSerialized;
        this.payoutTxSerialized = payoutTxSerialized;
        this.depositTxId = depositTxId;
        this.payoutTxId = payoutTxId;
        this.contractAsJson = contractAsJson;
        this.makerContractSignature = makerContractSignature;
        this.takerContractSignature = takerContractSignature;
        this.agentPubKeyRing = agentPubKeyRing;
        this.isSupportTicket = isSupportTicket;
        this.supportType = supportType;

        id = tradeId + "_" + traderId;
        uid = UUID.randomUUID().toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Dispute toProtoMessage() {
        // Needed to avoid ConcurrentModificationException
        List<ChatMessage> clonedChatMessages = new ArrayList<>(chatMessages);
        protobuf.Dispute.Builder builder = protobuf.Dispute.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setIsOpener(isOpener)
                .setDisputeOpenerIsBuyer(disputeOpenerIsBuyer)
                .setDisputeOpenerIsMaker(disputeOpenerIsMaker)
                .setTraderPubKeyRing(traderPubKeyRing.toProtoMessage())
                .setTradeDate(tradeDate)
                .setContract(contract.toProtoMessage())
                .setContractAsJson(contractAsJson)
                .setAgentPubKeyRing(agentPubKeyRing.toProtoMessage())
                .setIsSupportTicket(isSupportTicket)
                .addAllChatMessage(clonedChatMessages.stream()
                        .map(msg -> msg.toProtoNetworkEnvelope().getChatMessage())
                        .collect(Collectors.toList()))
                .setIsClosed(isClosedProperty.get())
                .setOpeningDate(openingDate)
                .setId(id);

        Optional.ofNullable(contractHash).ifPresent(e -> builder.setContractHash(ByteString.copyFrom(e)));
        Optional.ofNullable(depositTxSerialized).ifPresent(e -> builder.setDepositTxSerialized(ByteString.copyFrom(e)));
        Optional.ofNullable(payoutTxSerialized).ifPresent(e -> builder.setPayoutTxSerialized(ByteString.copyFrom(e)));
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        Optional.ofNullable(payoutTxId).ifPresent(builder::setPayoutTxId);
        Optional.ofNullable(disputePayoutTxId).ifPresent(builder::setDisputePayoutTxId);
        Optional.ofNullable(makerContractSignature).ifPresent(builder::setMakerContractSignature);
        Optional.ofNullable(takerContractSignature).ifPresent(builder::setTakerContractSignature);
        Optional.ofNullable(disputeResultProperty.get()).ifPresent(result -> builder.setDisputeResult(disputeResultProperty.get().toProtoMessage()));
        Optional.ofNullable(supportType).ifPresent(result -> builder.setSupportType(SupportType.toProtoMessage(supportType)));
        Optional.ofNullable(mediatorsDisputeResult).ifPresent(result -> builder.setMediatorsDisputeResult(mediatorsDisputeResult));
        Optional.ofNullable(delayedPayoutTxId).ifPresent(result -> builder.setDelayedPayoutTxId(delayedPayoutTxId));
        Optional.ofNullable(donationAddressOfDelayedPayoutTx).ifPresent(result -> builder.setDonationAddressOfDelayedPayoutTx(donationAddressOfDelayedPayoutTx));
        return builder.build();
    }

    public static Dispute fromProto(protobuf.Dispute proto, CoreProtoResolver coreProtoResolver) {
        Dispute dispute = new Dispute(proto.getOpeningDate(),
                proto.getTradeId(),
                proto.getTraderId(),
                proto.getIsOpener(),
                proto.getDisputeOpenerIsBuyer(),
                proto.getDisputeOpenerIsMaker(),
                PubKeyRing.fromProto(proto.getTraderPubKeyRing()),
                proto.getTradeDate(),
                Contract.fromProto(proto.getContract(), coreProtoResolver),
                ProtoUtil.byteArrayOrNullFromProto(proto.getContractHash()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getDepositTxSerialized()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getPayoutTxSerialized()),
                ProtoUtil.stringOrNullFromProto(proto.getDepositTxId()),
                ProtoUtil.stringOrNullFromProto(proto.getPayoutTxId()),
                proto.getContractAsJson(),
                ProtoUtil.stringOrNullFromProto(proto.getMakerContractSignature()),
                ProtoUtil.stringOrNullFromProto(proto.getTakerContractSignature()),
                PubKeyRing.fromProto(proto.getAgentPubKeyRing()),
                proto.getIsSupportTicket(),
                SupportType.fromProto(proto.getSupportType()));

        dispute.chatMessages.addAll(proto.getChatMessageList().stream()
                .map(ChatMessage::fromPayloadProto)
                .collect(Collectors.toList()));

        dispute.isClosedProperty.set(proto.getIsClosed());
        if (proto.hasDisputeResult())
            dispute.disputeResultProperty.set(DisputeResult.fromProto(proto.getDisputeResult()));
        dispute.disputePayoutTxId = ProtoUtil.stringOrNullFromProto(proto.getDisputePayoutTxId());

        String mediatorsDisputeResult = proto.getMediatorsDisputeResult();
        if (!mediatorsDisputeResult.isEmpty()) {
            dispute.setMediatorsDisputeResult(mediatorsDisputeResult);
        }

        String delayedPayoutTxId = proto.getDelayedPayoutTxId();
        if (!delayedPayoutTxId.isEmpty()) {
            dispute.setDelayedPayoutTxId(delayedPayoutTxId);
        }

        String donationAddressOfDelayedPayoutTx = proto.getDonationAddressOfDelayedPayoutTx();
        if (!donationAddressOfDelayedPayoutTx.isEmpty()) {
            dispute.setDonationAddressOfDelayedPayoutTx(donationAddressOfDelayedPayoutTx);
        }

        return dispute;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAndPersistChatMessage(ChatMessage chatMessage) {
        if (!chatMessages.contains(chatMessage)) {
            chatMessages.add(chatMessage);
        } else {
            log.error("disputeDirectMessage already exists");
        }
    }

    public boolean isMediationDispute() {
        return !chatMessages.isEmpty() && chatMessages.get(0).getSupportType() == SupportType.MEDIATION;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setIsClosed(boolean isClosed) {
        this.isClosedProperty.set(isClosed);
    }

    public void setDisputeResult(DisputeResult disputeResult) {
        disputeResultProperty.set(disputeResult);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getShortTradeId() {
        return Utilities.getShortId(tradeId);
    }

    public ReadOnlyBooleanProperty isClosedProperty() {
        return isClosedProperty;
    }

    public ReadOnlyObjectProperty<DisputeResult> disputeResultProperty() {
        return disputeResultProperty;
    }

    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Date getOpeningDate() {
        return new Date(openingDate);
    }

    public boolean isClosed() {
        return isClosedProperty.get();
    }


    @Override
    public String toString() {
        return "Dispute{" +
                "\n     tradeId='" + tradeId + '\'' +
                ",\n     id='" + id + '\'' +
                ",\n     uid='" + uid + '\'' +
                ",\n     traderId=" + traderId +
                ",\n     isOpener=" + isOpener +
                ",\n     disputeOpenerIsBuyer=" + disputeOpenerIsBuyer +
                ",\n     disputeOpenerIsMaker=" + disputeOpenerIsMaker +
                ",\n     traderPubKeyRing=" + traderPubKeyRing +
                ",\n     tradeDate=" + tradeDate +
                ",\n     contract=" + contract +
                ",\n     contractHash=" + Utilities.bytesAsHexString(contractHash) +
                ",\n     depositTxSerialized=" + Utilities.bytesAsHexString(depositTxSerialized) +
                ",\n     payoutTxSerialized=" + Utilities.bytesAsHexString(payoutTxSerialized) +
                ",\n     depositTxId='" + depositTxId + '\'' +
                ",\n     payoutTxId='" + payoutTxId + '\'' +
                ",\n     contractAsJson='" + contractAsJson + '\'' +
                ",\n     makerContractSignature='" + makerContractSignature + '\'' +
                ",\n     takerContractSignature='" + takerContractSignature + '\'' +
                ",\n     agentPubKeyRing=" + agentPubKeyRing +
                ",\n     isSupportTicket=" + isSupportTicket +
                ",\n     chatMessages=" + chatMessages +
                ",\n     isClosedProperty=" + isClosedProperty +
                ",\n     disputeResultProperty=" + disputeResultProperty +
                ",\n     disputePayoutTxId='" + disputePayoutTxId + '\'' +
                ",\n     openingDate=" + openingDate +
                ",\n     supportType=" + supportType +
                ",\n     mediatorsDisputeResult='" + mediatorsDisputeResult + '\'' +
                ",\n     delayedPayoutTxId='" + delayedPayoutTxId + '\'' +
                ",\n     donationAddressOfDelayedPayoutTx='" + donationAddressOfDelayedPayoutTx + '\'' +
                "\n}";
    }
}
