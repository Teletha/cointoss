/*
 * Copyright (C) 2020 cointoss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.market.coinbase;

import static java.util.concurrent.TimeUnit.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import cointoss.Direction;
import cointoss.MarketService;
import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.market.Exchange;
import cointoss.market.TimestampBasedMarketServiceSupporter;
import cointoss.order.OrderBookPage;
import cointoss.order.OrderBookPageChanges;
import cointoss.util.APILimiter;
import cointoss.util.Chrono;
import cointoss.util.EfficientWebSocket;
import cointoss.util.EfficientWebSocketModel.IdentifiableTopic;
import cointoss.util.Network;
import cointoss.util.arithmetic.Num;
import kiss.JSON;
import kiss.Signal;
import kiss.Variable;

public class CoinbaseService extends MarketService {

    private static final DateTimeFormatter TimeFormat = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]X");

    /** The API limit. */
    private static final APILimiter LIMITER = APILimiter.with.limit(6).refresh(500, MILLISECONDS);

    /** The realtime communicator. */
    private static final EfficientWebSocket Realtime = EfficientWebSocket.with.address("wss://ws-feed.pro.coinbase.com")
            .extractId(json -> json.text("type") + ":" + json.text("product_id"));

    /**
     * @param marketName
     * @param setting
     */
    protected CoinbaseService(String marketName, MarketSetting setting) {
        super(Exchange.Coinbase, marketName, setting);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EfficientWebSocket clientRealtimely() {
        return Realtime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executions(long startId, long endId) {
        long[] context = new long[3];

        return call("GET", "products/" + marketName + "/trades?before=" + startId + "&after=" + (endId + 1)).flatIterable(e -> e.find("*"))
                .reverse()
                .map(json -> createExecution(json, false, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<Execution> connectExecutionRealtimely() {
        long[] previous = new long[3];

        return clientRealtimely().subscribe(new Topic("ticker", marketName)).map(json -> createExecution(json, true, previous));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionLatest() {
        return call("GET", "products/" + marketName + "/trades?limit=1").flatIterable(e -> e.find("*"))
                .map(json -> createExecution(json, false, new long[3]));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionLatestAt(long id) {
        long[] context = new long[3];

        return call("GET", "products/" + marketName + "/trades?after=" + id).flatIterable(e -> e.find("*"))
                .reverse()
                .map(json -> createExecution(json, false, context));
    }

    /**
     * Convert to {@link Execution}.
     * 
     * @param json
     * @param previous
     * @return
     */
    private Execution createExecution(JSON e, boolean realtime, long[] previous) {
        long id = e.get(long.class, "trade_id");
        Direction side = e.get(Direction.class, "side");
        if (!realtime) side = side.inverse();
        Num size = e.get(Num.class, realtime ? "last_size" : "size");
        Num price = e.get(Num.class, "price");
        ZonedDateTime date = ZonedDateTime.parse(e.text("time"), TimeFormat);
        int consecutive = TimestampBasedMarketServiceSupporter.computeConsecutive(side, date.toInstant().toEpochMilli(), previous);

        return Execution.with.direction(side, size).price(price).id(id).date(date).consecutive(consecutive);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<OrderBookPageChanges> orderBook() {
        return call("GET", "orderbooks?symbol=" + marketName).map(e -> createOrderBook(e.get("data")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<OrderBookPageChanges> createOrderBookRealtimely() {
        return clientRealtimely().subscribe(new Topic("orderbooks", marketName)).map(this::createOrderBook);
    }

    /**
     * Convert json to {@link OrderBookPageChanges}.
     * 
     * @param root
     * @return
     */
    private OrderBookPageChanges createOrderBook(JSON root) {
        OrderBookPageChanges changes = new OrderBookPageChanges();
        changes.clearInside = true;

        for (JSON ask : root.find("asks", "*")) {
            Num price = ask.get(Num.class, "price");
            double size = ask.get(double.class, "size");
            changes.asks.add(new OrderBookPage(price, size));
        }
        for (JSON bid : root.find("bids", "*")) {
            Num price = bid.get(Num.class, "price");
            double size = bid.get(double.class, "size");
            changes.bids.add(new OrderBookPage(price, size));
        }

        return changes;
    }

    /**
     * Call rest API.
     * 
     * @param method
     * @param path
     * @return
     */
    private Signal<JSON> call(String method, String path) {
        Builder builder = HttpRequest.newBuilder(URI.create("https://api.pro.coinbase.com/" + path));

        return Network.rest(builder, LIMITER, client()).retryWhen(retryPolicy(10, exchange + " RESTCall"));
    }

    /**
     * Subscription topic for websocket communication.
     */
    static class Topic extends IdentifiableTopic<Topic> {

        public String type = "subscribe";

        public List<String> product_ids = new ArrayList();

        public List<String> channels = new ArrayList();

        private Topic(String channel, String market) {
            super(channel + ":" + market, topic -> topic.type = "unsubscribe");

            product_ids.add(market);
            channels.add(channel);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean verifySubscribedReply(JSON reply) {
            return reply.text("type").equals("subscriptions") && reply.find(String.class, "channels", "*", "name")
                    .contains(channels.get(0));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Coinbase.BTCUSD.log.fromId(111489317).to(e -> {
        // });

        Variable<ZonedDateTime> holder = Variable.of(Chrono.utc(2020, 11, 2));

        holder.observing().effect(e -> System.out.println(e)).flatMap(e -> Coinbase.BTCUSD.log.at(e).effectOnComplete(() -> {
            System.out.println("Complete");
            holder.set(v -> v.plusDays(1));
        })).to(e -> {
            System.out.println(e);
        });

        Thread.sleep(1000 * 3500);
    }
}