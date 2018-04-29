/*
 * Copyright (C) 2018 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.backtest;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import cointoss.Execution;
import cointoss.Market;
import cointoss.MarketLog;
import cointoss.Trader;
import cointoss.util.Num;
import kiss.I;
import kiss.Signal;

/**
 * @version 2018/04/29 14:34:51
 */
public class BackTest {

    /** The target execution log. */
    private Function<MarketLog, Signal<Execution>> log;

    /** The base currency. */
    private Num base = Num.ZERO;

    /** The target currency. */
    private Num target = Num.ZERO;

    /** The strategy generator. */
    private Supplier<Trader> strategy;

    /** A number of trials. */
    private int trial = 1;

    /**
     * @param rangeRandom
     * @return
     */
    public BackTest log(Function<MarketLog, Signal<Execution>> log) {
        this.log = Objects.requireNonNull(log);
        return this;
    }

    /**
     * Set initial base and target currency.
     * 
     * @param base
     * @param target
     * @return
     */
    public BackTest currency(double base, double target) {
        if (base < 0) {
            throw new IllegalArgumentException("Base currency must be positive.");
        }

        if (target < 0) {
            throw new IllegalArgumentException("Target currency must be positive.");
        }

        this.base = Num.of(base);
        this.target = Num.of(target);

        return this;
    }

    /**
     * @param strategy
     * @return
     */
    public BackTest strategy(Supplier<Trader> strategy) {
        this.strategy = Objects.requireNonNull(strategy);
        return this;
    }

    /**
     * Set a number of trials.
     * 
     * @param number
     * @return
     */
    public BackTest trial(int number) {
        if (number < 2) {
            number = 1;
        }
        this.trial = number;
        return this;
    }

    /**
     * Run test.
     */
    public void run() {
        IntStream.range(0, trial).parallel().forEach(index -> {
            Market market = new Market(new BackTestService());
            market.add(strategy.get());
            market.read(log);
            market.dispose();
        });
    }

    /**
     * @version 2018/04/29 14:59:08
     */
    private class BackTestService extends TestableMarketService {

        /**
         */
        private BackTestService() {
            super(Time.lag(0, 10));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Signal<Num> getBaseCurrency() {
            return I.signal(base);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Signal<Num> getTargetCurrency() {
            return I.signal(target);
        }
    }
}
