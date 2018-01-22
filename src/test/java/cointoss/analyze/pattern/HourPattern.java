/*
 * Copyright (C) 2017 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.analyze.pattern;

import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;

import cointoss.BackTester;
import cointoss.Trader;
import cointoss.market.bitflyer.BitFlyer;
import cointoss.util.Num;

/**
 * @version 2017/09/20 2:36:26
 */
public class HourPattern extends Trader {

    private Map<LocalTime, Statistics> statistics = new TreeMap();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        market.hour1.signal().to(tick -> {
            Statistics stat = statistics.computeIfAbsent(tick.start.toLocalTime().withMinute(0).withSecond(0).withNano(0), Statistics::new);
            Num diff = tick.closePrice.divide(tick.openPrice);

            if (tick.openPrice.isLessThan(tick.closePrice)) {
                stat.up = stat.up.plus(1);
                stat.upRatio = stat.upRatio.multiply(diff);
            } else {
                stat.down = stat.down.plus(1);
                stat.downRatio = stat.downRatio.multiply(diff);
            }
        });
    }

    /**
     * Analyze
     * 
     * @param args
     */
    public static void main(String[] args) {
        HourPattern analyzer = new HourPattern();

        BackTester.with()
                .baseCurrency(1000000)
                .targetCurrency(0)
                .log(BitFlyer.FX_BTC_JPY.log().rangeAll())
                .strategy(() -> analyzer)
                .trial(1)
                .run();

        for (Statistics value : analyzer.statistics.values()) {
            System.out.println(value);
        }
    }

    /**
     * @version 2017/09/20 15:36:57
     */
    private static class Statistics {

        public Num down = Num.ZERO;

        public Num downRatio = Num.ONE;

        public Num up = Num.ZERO;

        public Num upRatio = Num.ONE;

        private final LocalTime time;

        /**
         * @param time
         */
        public Statistics(LocalTime time) {
            this.time = time;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new StringBuilder().append(time.plusHours(9).getHour())
                    .append(" up")
                    .append(up)
                    .append(" ")
                    .append(upRatio)
                    .append(" \tdown")
                    .append(down)
                    .append(" ")
                    .append(downRatio)
                    .append("\t")
                    .append(up.divide(down).scale(2))
                    .toString();
        }
    }
}
