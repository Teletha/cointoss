/*
 * Copyright (C) 2020 cointoss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.market.binance;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cointoss.Direction;
import cointoss.MarketService;
import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.market.Exchange;
import cointoss.order.OrderBookPage;
import cointoss.order.OrderBookPageChanges;
import cointoss.ticker.data.Liquidation;
import cointoss.ticker.data.OpenInterest;
import cointoss.util.APILimiter;
import cointoss.util.Chrono;
import cointoss.util.EfficientWebSocket;
import cointoss.util.EfficientWebSocketModel.IdentifiableTopic;
import cointoss.util.Network;
import cointoss.util.arithmetic.Num;
import kiss.I;
import kiss.JSON;
import kiss.Signal;

public class BinanceService extends MarketService {

    /** The API limit. */
    private static final APILimiter Limit = APILimiter.with.limit(550).refresh(Duration.ofMinutes(1));

    /** The realtime communicator. */
    private static final EfficientWebSocket Realtime = EfficientWebSocket.with.address("wss://stream.binance.com:9443/stream")
            .extractId(json -> json.text("stream"));

    /** The realtime communicator. */
    private static final EfficientWebSocket RealtimeFuture = Realtime.withAddress("wss://fstream.binance.com/stream");

    /** The market type. */
    private final boolean isFutures;

    /**
     * @param marketName
     * @param setting
     */
    protected BinanceService(String marketName, boolean isFutures, MarketSetting setting) {
        super(isFutures ? Exchange.BinanceF : Exchange.Binance, marketName, setting);

        this.isFutures = isFutures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EfficientWebSocket clientRealtimely() {
        return isFutures ? RealtimeFuture : Realtime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executions(long startId, long endId) {
        return call("GET", "aggTrades?symbol=" + marketName + "&limit=1000&fromId=" + (startId + 1)).flatIterable(e -> e.find("*"))
                .map(this::createExecution);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<Execution> connectExecutionRealtimely() {
        return clientRealtimely().subscribe(new Topic("aggTrade", marketName)).map(json -> createExecution(json.get("data")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionLatest() {
        return call("GET", "aggTrades?symbol=" + marketName + "&limit=1").flatIterable(e -> e.find("*")).map(this::createExecution);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionsBefore(long id) {
        // Since the Binance API only provides a fromID parameter, subtract the ID by the maximum
        // number of acquisitions. If that makes the specified ID a negative number, emulate the API
        // by setting the ID to 0 and reduce the number of acquisitions.
        long limit = setting.acquirableExecutionSize;
        long fromId = id - limit;
        if (fromId < 0) {
            limit += fromId;
            fromId = 0;
        }

        return this.call("GET", "aggTrades?symbol=" + marketName + "&fromId=" + fromId + "&limit=" + limit)
                .flatIterable(e -> e.find("*"))
                .map(this::createExecution);
    }

    /**
     * Convert to {@link Execution}.
     * 
     * @param e
     * @return
     */
    private Execution createExecution(JSON e) {
        long id = Long.parseLong(e.text("a"));
        Direction side = e.get(Boolean.class, "m") ? Direction.SELL : Direction.BUY;
        Num size = e.get(Num.class, "q");
        Num price = e.get(Num.class, "p");
        ZonedDateTime date = Chrono.utcByMills(Long.parseLong(e.text("T")));

        return Execution.with.direction(side, size)
                .id(id)
                .price(price)
                .date(date)
                .consecutive(Execution.ConsecutiveDifference)
                .delay(Execution.DelayInestimable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<OrderBookPageChanges> orderBook() {
        return call("GET", "depth?symbol=" + marketName + "&limit=" + (isFutures ? "1000" : "5000"))
                .map(e -> createOrderBook(e, "bids", "asks"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<OrderBookPageChanges> connectOrderBookRealtimely() {
        return clientRealtimely().subscribe(new Topic("depth", marketName)).map(json -> createOrderBook(json.get("data"), "b", "a"));
    }

    /**
     * Convert JSON to {@link OrderBookPageChanges}.
     * 
     * @param array
     * @return
     */
    private OrderBookPageChanges createOrderBook(JSON pages, String bidName, String askName) {
        OrderBookPageChanges change = new OrderBookPageChanges();

        for (JSON bid : pages.find(bidName, "*")) {
            Num price = bid.get(Num.class, "0");
            double size = Double.parseDouble(bid.text("1"));

            change.bids.add(new OrderBookPage(price, size));
        }

        for (JSON ask : pages.find(askName, "*")) {
            Num price = ask.get(Num.class, "0");
            double size = Double.parseDouble(ask.text("1"));

            change.asks.add(new OrderBookPage(price, size));
        }
        return change;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<OpenInterest> provideOpenInterest(ZonedDateTime startExcluded) {
        if (!isFutures) {
            return I.signal();
        }

        startExcluded = Chrono.between(Chrono.utcNow().minusDays(30), startExcluded, startExcluded);

        long start = startExcluded.plusMinutes(5).toInstant().toEpochMilli();
        long end = startExcluded.plusMinutes(5 * 500).toInstant().toEpochMilli();

        return call("GET", "https://fapi.binance.com/futures/data/openInterestHist?symbol=" + marketName + "&period=5m&limit=500&startTime=" + start + "&endTime=" + end)
                .flatIterable(e -> e.find("*"))
                .map(e -> {
                    ZonedDateTime time = Chrono.utcByMills(e.get(long.class, "timestamp"));
                    double size = e.get(double.class, "sumOpenInterest");
                    double value = e.get(double.class, "sumOpenInterestValue");

                    return OpenInterest.with.date(time).size(size).value(value);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Liquidation> liquidations(ZonedDateTime startExcluded, ZonedDateTime endExcluded) {
        if (!isFutures) {
            return I.signal();
        }

        return call("GET", "allForceOrders?symbol=" + marketName + "&limit=1000&endTime=" + endExcluded.toInstant().toEpochMilli(), 20)
                .flatIterable(e -> e.find("*"))
                .map(e -> {
                    System.out.println(e);
                    return null;
                });
    }

    public static void main(String[] args) throws InterruptedException {
        Binance.FUTURE_BTC_USDT.liquidations(Chrono.utc(2020, 12, 25), Chrono.utc(2020, 12, 26)).waitForTerminate().to(e -> {
            System.out.println(e);
        });
    }

    // private static final HttpClient client;
    //
    // static {
    // client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
    // }
    //
    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // protected HttpClient client() {
    // return client;
    // }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<Liquidation> connectLiquidation() {
        if (!isFutures) {
            return I.signal();
        }

        return clientRealtimely().subscribe(new Topic("forceOrder", marketName)).map(e -> {
            JSON json = e.get("data").get("o");
            return Liquidation.with.date(Chrono.utcByMills(json.get(long.class, "T")))
                    .direction(json.get(Direction.class, "S").inverse())
                    .size(json.get(double.class, "q"))
                    .price(json.get(Num.class, "ap"));
        });
    }

    /**
     * Call rest API.
     * 
     * @param method
     * @param path
     * @return
     */
    private Signal<JSON> call(String method, String path) {
        return call(method, path, 1);
    }

    /**
     * Call rest API.
     * 
     * @param method
     * @param path
     * @return
     */
    private Signal<JSON> call(String method, String path, int weight) {
        String uri = isFutures ? "https://fapi.binance.com/fapi/v1/" : "https://api.binance.com/api/v3/";
        Builder builder = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : uri + path));

        return Network.rest(builder, Limit, weight, client()).retryWhen(retryPolicy(10, "Binance RESTCall"));
    }

    /**
     * 
     */
    static class Topic extends IdentifiableTopic<Topic> {

        private static final AtomicInteger counter = new AtomicInteger();

        public String method = "SUBSCRIBE";

        public List<String> params = new ArrayList();

        public int id = counter.incrementAndGet();

        /** The string expression to make equality checking fast. */
        private String idText = Integer.toString(id);

        private Topic(String channel, String market) {
            super(market.toLowerCase() + "@" + channel, topic -> topic.method = "UNSUBSCRIBE");
            this.params.add(market.toLowerCase() + "@" + channel);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean verifySubscribedReply(JSON reply) {
            return reply.has("id", idText);
        }
    }
}