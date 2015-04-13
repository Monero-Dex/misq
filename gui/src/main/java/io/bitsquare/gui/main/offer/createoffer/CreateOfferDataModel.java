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

package io.bitsquare.gui.main.offer.createoffer;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.arbitration.ArbitratorService;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.model.DataModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Country;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.AccountSettings;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel implements Activatable, DataModel {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferDataModel.class);

    private OpenOfferManager openOfferManager;
    private final WalletService walletService;
    private final AccountSettings accountSettings;
    private final Preferences preferences;
    private final BSFormatter formatter;

    private final String offerId;

    private Offer.Direction direction;
    private AddressEntry addressEntry;

    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();
    final StringProperty fiatCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty requestPlaceOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> offerFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> networkFeeAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> securityDepositAsCoin = new SimpleObjectProperty<>();

    final ObservableList<Country> acceptedCountries = FXCollections.observableArrayList();
    final ObservableList<String> acceptedLanguageCodes = FXCollections.observableArrayList();
    final ObservableList<Arbitrator> acceptedArbitrators = FXCollections.observableArrayList();


    // non private for testing
    @Inject
    public CreateOfferDataModel(OpenOfferManager openOfferManager, WalletService walletService, ArbitratorService arbitratorService,
                                AccountSettings accountSettings, Preferences preferences, User user, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.accountSettings = accountSettings;
        this.preferences = preferences;
        this.formatter = formatter;
        this.offerId = UUID.randomUUID().toString();

        offerFeeAsCoin.set(FeePolicy.CREATE_OFFER_FEE);
        networkFeeAsCoin.set(FeePolicy.TX_FEE);

        if (walletService != null && walletService.getWallet() != null) {
            addressEntry = walletService.getAddressEntry(offerId);

            walletService.addBalanceListener(new BalanceListener(getAddressEntry().getAddress()) {
                @Override
                public void onBalanceChanged(@NotNull Coin balance) {
                    updateBalance(balance);
                }
            });
            updateBalance(walletService.getBalanceForAddress(getAddressEntry().getAddress()));
        }

        if (user != null) {
            user.currentFiatAccountProperty().addListener((ov, oldValue, newValue) -> applyBankAccount(newValue));

            applyBankAccount(user.currentFiatAccountProperty().get());
        }

        if (accountSettings != null)
            btcCode.bind(preferences.btcDenominationProperty());

        // we need to set it here already as initWithData is called before activate
        if (accountSettings != null)
            securityDepositAsCoin.set(accountSettings.getSecurityDeposit());
    }

    @Override
    public void activate() {
        // might be changed after screen change
        if (accountSettings != null) {
            // set it here again to cover the case of an securityDeposit change after a screen change
            securityDepositAsCoin.set(accountSettings.getSecurityDeposit());

            acceptedCountries.setAll(accountSettings.getAcceptedCountries());
            acceptedLanguageCodes.setAll(accountSettings.getAcceptedLanguageLocaleCodes());
            acceptedArbitrators.setAll(accountSettings.getAcceptedArbitrators());
        }
    }

    @Override
    public void deactivate() {
        // no-op
    }

    void onPlaceOffer() {
        // data validation is done in the trade domain
        openOfferManager.onPlaceOffer(offerId,
                direction,
                priceAsFiat.get(),
                amountAsCoin.get(),
                minAmountAsCoin.get(),
                (transaction) -> {
                    transactionId.set(transaction.getHashAsString());
                    requestPlaceOfferSuccess.set(true);
                },
                requestPlaceOfferErrorMessage::set
        );
    }

    void calculateVolume() {
        try {
            if (priceAsFiat.get() != null &&
                    amountAsCoin.get() != null &&
                    !amountAsCoin.get().isZero() &&
                    !priceAsFiat.get().isZero()) {
                volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateAmount() {
        try {
            if (volumeAsFiat.get() != null &&
                    priceAsFiat.get() != null &&
                    !volumeAsFiat.get().isZero() &&
                    !priceAsFiat.get().isZero()) {
                // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
                amountAsCoin.set(formatter.reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin
                        (volumeAsFiat.get())));

                calculateTotalToPay();
            }
        } catch (Throwable t) {
            // Should be never reached
            log.error(t.toString());
        }
    }

    void calculateTotalToPay() {
        if (securityDepositAsCoin.get() != null) {
            if (direction == Offer.Direction.BUY)
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()));
            else
                totalToPayAsCoin.set(offerFeeAsCoin.get().add(networkFeeAsCoin.get()).add(securityDepositAsCoin.get()).add(amountAsCoin.get()));
        }
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
        return true;
    }

    void securityDepositInfoDisplayed() {
        preferences.setDisplaySecurityDepositInfo(false);
    }


    @Nullable
    Offer.Direction getDirection() {
        return direction;
    }

    WalletService getWalletService() {
        return walletService;
    }

    String getOfferId() {
        return offerId;
    }

    private void updateBalance(@NotNull Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

    public AddressEntry getAddressEntry() {
        return addressEntry;
    }

    private void applyBankAccount(FiatAccount fiatAccount) {
        if (fiatAccount != null) {
            bankAccountType.set(fiatAccount.type.toString());
            bankAccountCurrency.set(fiatAccount.currencyCode);
            bankAccountCounty.set(fiatAccount.country.name);

            fiatCode.set(fiatAccount.currencyCode);
        }
    }

    public Boolean getDisplaySecurityDepositInfo() {
        return preferences.getDisplaySecurityDepositInfo();
    }

    public void initWithData(Offer.Direction direction, Coin amount, Fiat price) {
        checkNotNull(direction);
        this.direction = direction;
    }
}
