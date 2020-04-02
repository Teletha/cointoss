/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package trademate;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cointoss.MarketService;
import cointoss.market.binance.Binance;
import cointoss.market.bitfinex.Bitfinex;
import cointoss.market.bitflyer.BitFlyer;
import cointoss.market.bitmex.BitMex;
import cointoss.util.Chrono;
import cointoss.util.Network;
import kiss.I;
import kiss.Managed;
import kiss.Singleton;
import trademate.verify.BackTestView;
import transcript.Lang;
import viewtify.Key;
import viewtify.Theme;
import viewtify.Viewtify;
import viewtify.ui.UITab;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.dock.DockSystem;

@Managed(value = Singleton.class)
public class TradeMate extends View {

    /** The tab loading strategy. */
    private final TabLoader loader = new TabLoader();

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(DockSystem.UI);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        // DockSystem.register("Setting").contents(SettingView.class);
        // DockSystem.register("BackTest").contents(BackTestView.class);

        List<MarketService> services = List
                .of(BitFlyer.FX_BTC_JPY, BitFlyer.BTC_JPY, BitFlyer.ETH_JPY, BitFlyer.BCH_BTC, BitMex.XBT_USD, BitMex.ETH_USD, Binance.BTC_USDT, Binance.FUTURE_BTC_USDT, Bitfinex.BTC_USDT);

        // ========================================================
        // Create Tab for each Markets
        // ========================================================
        for (MarketService service : services) {
            UITab tab = DockSystem.register(service.marketIdentity())
                    .closable(false)
                    .text(service.marketReadableName())
                    .contents(ui -> new TradingView(ui, service));

            loader.add(tab);
        }

        // ========================================================
        // Clock in Title bar
        // ========================================================
        Chrono.seconds().map(Chrono.DateDayTime::format).on(Viewtify.UIThread).to(time -> {
            stage().v.setTitle(time);
        });

        // ========================================================
        // Global Shortcut
        // ========================================================
        TradeMateCommand.OpenBacktest.shortcut(Key.F11).contribute(() -> {
            Viewtify.openNewWindow(new BackTestView(), null);
        });
    }

    /**
     * {@link TradeMate} will automatically initialize in the background if any tab has not been
     * activated yet.
     */
    public final void requestLazyInitialization() {
        loader.tryLoad();
    }

    private static class TabLoader {

        /** The queue for loading tabs. */
        private final LinkedList<UITab> queue = new LinkedList();

        /** The current processing tab. */
        private UITab current;

        /**
         * Add tab to loading queue.
         * 
         * @param tab
         */
        private void add(UITab tab) {
            queue.add(tab);
        }

        private synchronized void tryLoad() {
            if (current == null || current.isLoaded()) {
                current = queue.pollFirst();

                if (current != null) {
                    if (current.isLoaded()) {
                        tryLoad();
                    } else {
                        Viewtify.inUI(() -> {
                            current.load();
                        });
                    }
                }
            }
        }

    }

    /**
     * Entry point.
     */
    public static void main(String[] args) {
        // initialize logger for non-main thread
        Logger log = LogManager.getLogger();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error(e.getMessage(), e));

        // activate application
        Viewtify.application()
                .use(Theme.Dark)
                .icon("icon/app.png")
                .language(Lang.of(I.env("language", Locale.getDefault().getLanguage())))
                .onTerminating(Network::terminate)
                .activate(TradeMate.class);
    }
}
