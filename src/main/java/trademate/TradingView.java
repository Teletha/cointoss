/*
 * Copyright (C) 2017 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package trademate;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;

import cointoss.Market;
import cointoss.market.bitflyer.BitFlyer;
import trademate.console.Console;
import trademate.order.OrderBookView;
import trademate.order.OrderBuilder;
import trademate.order.OrderCatalog;
import trademate.order.PositionCatalog;
import viewtify.View;
import viewtify.Viewtify;

/**
 * @version 2017/11/29 10:50:06
 */
public class TradingView extends View {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    public final BitFlyer provider;

    public @FXML ExecutionView executionView;

    public @FXML Console console;

    public @FXML OrderBookView board;

    public @FXML OrderBuilder builder;

    public @FXML OrderCatalog orders;

    public @FXML PositionCatalog positions;

    public @FXML Pane chart;

    /** Market cache. */
    private Market market;

    /**
     *
     */
    public TradingView(BitFlyer provider) {
        this.provider = provider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        market().yourExecution.to(o -> {

        });

        market().health.to(v -> {
            System.out.println(v);
        });
        // chart.getChildren().add(new CandleChart().candleDate(market.minute1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String name() {
        return TradingView.class.getSimpleName() + View.IDSeparator + provider.fullName();
    }

    /**
     * Retrieve the associated market.
     * 
     * @return
     */
    public final synchronized Market market() {
        if (market == null) {
            Viewtify.Terminator.add(market = new Market(provider.service(), provider.log().fromLast(60, ChronoUnit.MINUTES)));
        }
        return market;
    }
}
