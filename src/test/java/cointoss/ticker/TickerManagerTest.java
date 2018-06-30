/*
 * Copyright (C) 2018 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.ticker;

import static cointoss.MarketTestSupport.*;

import org.junit.jupiter.api.Test;

import com.google.common.base.Predicate;

/**
 * @version 2018/06/30 9:04:39
 */
class TickerManagerTest {

    @Test
    void tickerBy() {
        TickerManager manager = new TickerManager();
        Ticker2 ticker = manager.tickerBy(TickSpan.Minute1);
        assert ticker != null;
    }

    @Test
    void updateHighPrice() {
        TickerManager manager = new TickerManager();

        // update
        manager.update(buy(100, 1).date(BaseDate));

        // validate
        manager.tickers().to(ticker -> {
            assert ticker.first().highPrice.is(100);
        });

        // update
        manager.update(buy(300, 1).date(BaseDate.plusMinutes(1)));
        manager.update(buy(200, 1).date(BaseDate.plusMinutes(2)));

        // validate
        manager.tickers().take(between(TickSpan.Minute3, TickSpan.Day7)).to(ticker -> {
            assert ticker.first().highPrice.is(300);
        });

        manager.tickers().take(between(TickSpan.Second1, TickSpan.Minute1)).to(ticker -> {
            assert ticker.first().highPrice.is(100);
            assert ticker.last().highPrice.is(200);
        });
    }

    private Predicate<Ticker2> between(TickSpan start, TickSpan end) {
        return e -> {
            int ordinal = e.span.ordinal();
            return start.ordinal() <= ordinal && ordinal <= end.ordinal();
        };
    }
}
