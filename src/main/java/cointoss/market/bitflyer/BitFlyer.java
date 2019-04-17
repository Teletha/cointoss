/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.market.bitflyer;

import cointoss.MarketService;
import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.execution.ExecutionDeltaLogger;
import cointoss.market.MarketAccount;
import cointoss.market.MarketServiceProvider;
import cointoss.util.Num;
import kiss.I;

public final class BitFlyer extends MarketServiceProvider {

    /** Reusable market configuration. */
    private static MarketSetting.Builder FiatBaseSetting = MarketSetting.builder()
            .baseCurrencyMinimumBidPrice(Num.of(1))
            .targetCurrencyMinimumBidSize(Num.of("0.01"))
            .targetCurrencyScaleSize(3)
            .orderBookGroupRanges(Num.of(50, 100, 250, 500, 1000, 2500, 5000))
            .acquirableExecutionSize(499)
            .executionLogger(new BitFlyerCodec());

    /** Reusable market configuration. */
    private static MarketSetting.Builder BTCBaseSetting = MarketSetting.builder()
            .baseCurrencyMinimumBidPrice(Num.of("0.01"))
            .targetCurrencyMinimumBidSize(Num.of("0.01"))
            .targetCurrencyScaleSize(6)
            .orderBookGroupRanges(Num.of(0.01, 0.02, 0.05, 0.1))
            .acquirableExecutionSize(499)
            .executionLogger(new BitFlyerCodec());

    /** Market */
    public static final MarketService BTC_JPY = new BitFlyerService("BTC_JPY", FiatBaseSetting);

    /** Market */
    public static final MarketService FX_BTC_JPY = new BitFlyerService("FX_BTC_JPY", FiatBaseSetting);

    /** Market */
    public static final MarketService ETH_BTC = new BitFlyerService("ETH_BTC", BTCBaseSetting);

    /** Market */
    public static final MarketService BCH_BTC = new BitFlyerService("BCH_BTC", BTCBaseSetting);

    /** Market */
    public static final MarketService BTCJPY28SEP2018 = new BitFlyerService("BTCJPY28SEP2018", FiatBaseSetting);

    /** Market */
    public static final MarketService BTCJPY03AUG2018 = new BitFlyerService("BTCJPY03AUG2018", FiatBaseSetting);

    /** Market */
    public static final MarketService BTCJPY10AUG2018 = new BitFlyerService("BTCJPY10AUG2018", FiatBaseSetting);

    /**
     * {@inheritDoc}
     */
    @Override
    public MarketAccount account() {
        return I.make(BitFlyerAccount.class);
    }

    /** The specialized logger. */
    private static class BitFlyerCodec extends ExecutionDeltaLogger {
        @Override
        protected Num decodePrice(String value, Execution previous) {
            return decodeIntegralDelta(value, previous.price, 0);
        }

        @Override
        protected String encodePrice(Execution execution, Execution previous) {
            return encodeIntegralDelta(execution.price, previous.price, 0);
        }
    }
}
