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

package bisq.core.trade.statistics;

import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatisticsManager {

    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatistics2StorageService tradeStatistics2StorageService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics2> observableTradeStatisticsSet = FXCollections.observableSet();

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics2StorageService tradeStatistics2StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.tradeStatistics2StorageService = tradeStatistics2StorageService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);

        appendOnlyDataStoreService.addService(tradeStatistics2StorageService);
    }

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics2)
                addToSet((TradeStatistics2) payload);
        });

        Set<TradeStatistics2> set = tradeStatistics2StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics2)
                .map(e -> (TradeStatistics2) e)
                .map(WrapperTradeStatistics2::new)
                .distinct()
                .map(WrapperTradeStatistics2::unwrap)
                .filter(TradeStatistics2::isValid)
                .collect(Collectors.toSet());
        observableTradeStatisticsSet.addAll(set);

        priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);

        dump();
    }

    public ObservableSet<TradeStatistics2> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void addToSet(TradeStatistics2 tradeStatistics) {
        if (!observableTradeStatisticsSet.contains(tradeStatistics)) {
            Optional<TradeStatistics2> duplicate = observableTradeStatisticsSet.stream().filter(
                    e -> e.getOfferId().equals(tradeStatistics.getOfferId())).findAny();

            if (duplicate.isPresent()) {
                // TODO: Can be removed as soon as everyone uses v1.2.6+
                // Removes an existing object with a trade id if the new one matches the existing except
                // for the deposit tx id
                if ((tradeStatistics.getMakerDepositTxId() == null || tradeStatistics.getTakerDepositTxId() == null) &&
                        tradeStatistics.isValid() &&
                        duplicate.get().compareTo(tradeStatistics) == 0) {
                    observableTradeStatisticsSet.remove(duplicate.get());
                } else {
                    return;
                }
            }

            if (!tradeStatistics.isValid()) {
                return;
            }

            observableTradeStatisticsSet.add(tradeStatistics);
            priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
            dump();
        }
    }

    private void dump() {
        if (dumpStatistics) {
            ArrayList<CurrencyTuple> fiatCurrencyList = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            jsonFileManager.writeToDisc(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDisc(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");

            // We store the statistics as json so it is easy for further processing (e.g. for web based services)
            // TODO This is just a quick solution for storing to one file.
            // 1 statistic entry has 500 bytes as json.
            // Need a more scalable solution later when we get more volume.
            // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
            // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
            List<TradeStatisticsForJson> list = observableTradeStatisticsSet.stream().map(TradeStatisticsForJson::new)
                    .sorted((o1, o2) -> (Long.compare(o2.tradeDate, o1.tradeDate)))
                    .collect(Collectors.toList());
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }

    static class WrapperTradeStatistics2 {
        private final TradeStatistics2 tradeStatistics;

        public WrapperTradeStatistics2(TradeStatistics2 tradeStatistics) {
            this.tradeStatistics = tradeStatistics;
        }

        public TradeStatistics2 unwrap() {
            return this.tradeStatistics;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            var wrapper = (WrapperTradeStatistics2) obj;
            return Objects.equals(tradeStatistics.getOfferId(), wrapper.tradeStatistics.getOfferId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(tradeStatistics.getOfferId());
        }
    }
}
