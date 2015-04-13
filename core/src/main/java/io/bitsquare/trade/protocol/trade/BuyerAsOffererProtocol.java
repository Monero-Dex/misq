/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.RequestFinalizePayoutTxMessage;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.CreateDepositTxInputs;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessRequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessRequestFinalizePayoutTxMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.ProcessRequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendDepositTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendFiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendPayoutTxFinalizedMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SendRequestPayDepositMessage;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndFinalizePayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.SignAndPublishDepositTx;
import io.bitsquare.trade.protocol.trade.tasks.buyer.VerifyAndSignContract;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakeOfferFeePayment;
import io.bitsquare.trade.protocol.trade.tasks.offerer.VerifyTakerAccount;
import io.bitsquare.trade.protocol.trade.tasks.shared.CommitPayoutTx;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.checkTradeId;

public class BuyerAsOffererProtocol extends TradeProtocol implements BuyerProtocol, OffererProtocol {
    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererProtocol.class);

    private final BuyerAsOffererTrade buyerAsOffererTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsOffererProtocol(BuyerAsOffererTrade trade) {
        super(trade.getProcessModel());

        log.debug("New OffererProtocol " + this);
        this.buyerAsOffererTrade = trade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, Peer taker) {
        checkTradeId(processModel.getId(), message);
        processModel.setTradeMessage(message);
        buyerAsOffererTrade.setTradingPeer(taker);

        //buyerAsOffererTrade.setLifeCycleState(OffererTradeState.LifeCycleState.OFFER_RESERVED);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> log.debug("taskRunner at handleRequestDepositTxInputsMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessRequestDepositTxInputsMessage.class,
                CreateDepositTxInputs.class,
                SendRequestPayDepositMessage.class
        );
        taskRunner.run();
        startTimeout();
    }

    @Override
    public void doApplyMailboxMessage(Message message, Trade trade) {
        this.trade = trade;

        // Find first the actual peer address, as it might have changed in the meantime
        findPeerAddress(processModel.tradingPeer.getPubKeyRing(),
                () -> {
                    if (message instanceof RequestFinalizePayoutTxMessage) {
                        handle((RequestFinalizePayoutTxMessage) message);
                    }
                },
                (errorMessage -> {
                    log.error(errorMessage);
                }));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestPublishDepositTxMessage tradeMessage) {
        stopTimeout();
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> log.debug("taskRunner at handleRequestPublishDepositTxMessage completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                ProcessRequestPublishDepositTxMessage.class,
                VerifyTakerAccount.class,
                VerifyAndSignContract.class,
                SignAndPublishDepositTx.class,
                SendDepositTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> log.debug("taskRunner at handleBankTransferStartedUIEvent completed"),
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                VerifyTakeOfferFeePayment.class,
                SendFiatTransferStartedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(RequestFinalizePayoutTxMessage tradeMessage) {
        log.debug("handle RequestFinalizePayoutTxMessage called");
        processModel.setTradeMessage(tradeMessage);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsOffererTrade,
                () -> {
                    log.debug("taskRunner at handlePayoutTxPublishedMessage completed");
                    // we are done!
                    processModel.onComplete();
                },
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                ProcessRequestFinalizePayoutTxMessage.class,
                SignAndFinalizePayoutTx.class,
                CommitPayoutTx.class,
                SendPayoutTxFinalizedMessage.class,
                SetupPayoutTxLockTimeReachedListener.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, Peer sender) {
        if (tradeMessage instanceof RequestPublishDepositTxMessage) {
            handle((RequestPublishDepositTxMessage) tradeMessage);
        }
        else if (tradeMessage instanceof RequestFinalizePayoutTxMessage) {
            handle((RequestFinalizePayoutTxMessage) tradeMessage);
        }
        else {
            log.error("Incoming decrypted tradeMessage not supported. " + tradeMessage);
        }
    }
}
