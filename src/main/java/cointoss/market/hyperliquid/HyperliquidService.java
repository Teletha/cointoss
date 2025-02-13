/*
 * Copyright (C) 2024 The COINTOSS Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.market.hyperliquid;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.time.ZonedDateTime;

import cointoss.Direction;
import cointoss.MarketService;
import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.market.Exchange;
import cointoss.market.TimestampBasedMarketServiceSupporter;
import cointoss.orderbook.OrderBookChanges;
import cointoss.util.APILimiter;
import cointoss.util.Chrono;
import cointoss.util.EfficientWebSocket;
import cointoss.util.EfficientWebSocketModel.IdentifiableTopic;
import cointoss.util.Network;
import hypatia.Num;
import kiss.I;
import kiss.JSON;
import kiss.Signal;

public class HyperliquidService extends MarketService {

    static final TimestampBasedMarketServiceSupporter Support = new TimestampBasedMarketServiceSupporter();

    /** The bitflyer API limit. */
    private static final APILimiter Limit = APILimiter.with.limit(20).refresh(Duration.ofSeconds(1));

    /** The realtime communicator. */
    private static final EfficientWebSocket Realtime = EfficientWebSocket.with.address("wss://api.hyperliquid.xyz/ws").extractId(json -> {
        String channel = json.text("channel");
        return channel.equals("subscriptionResponse") ? "" : channel + "." + json.get("data").get("0").text("coin");
    });

    /**
     * @param marketName
     * @param setting
     */
    protected HyperliquidService(String marketName, MarketSetting setting) {
        super(Exchange.Hyperliquid, marketName, setting);
        this.executionRequestLimit = 1000;
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
    public Signal<Execution> executionLatest() {
        return I.signal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executions(long startId, long endId) {
        long start = Support.computeEpochTime(startId);
        long end = Support.computeEpochTime(endId);

        System.out.println(Chrono.currentTimeMills());
        return call("POST", """
                {
                    "type": "candleSnapshot",
                    "req": {
                        "coin": "%s",
                        "interval": "1m",
                        "startTime": %d,
                        "endTime": %d
                    }
                }
                """.formatted(marketName, start, end)).flatIterable(json -> json.find("*")).map(json -> {
            System.out.println(json);
            double open = json.get(double.class, "o");
            double close = json.get(double.class, "c");
            double high = json.get(double.class, "h");
            double low = json.get(double.class, "l");
            double volume = json.get(double.class, "v");
            ZonedDateTime time = Chrono.utcByMills(json.get(long.class, "t"));

            return nul;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionsBefore(long id) {
        return I.signal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<Execution> connectExecutionRealtimely() {
        long[] context = new long[3];

        return clientRealtimely().subscribe(new Topic("trades", marketName)).flatIterable(json -> json.find("data", "*")).map(e -> {
            Direction side = Direction.parse(e.text("side"));
            Num price = e.get(Num.class, "px");
            Num size = e.get(Num.class, "sz");
            ZonedDateTime date = Chrono.utcByMills(e.get(long.class, "time"));

            return Support.createExecution(side, size, price, date, context);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<OrderBookChanges> orderBook() {
        return I.signal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Signal<OrderBookChanges> connectOrderBookRealtimely() {
        return I.signal();
    }

    /**
     * Call rest API.
     * 
     * @param method
     * @param body
     * @return
     */
    private Signal<JSON> call(String method, String body) {
        Builder builder = HttpRequest.newBuilder(URI.create("https://api.hyperliquid.xyz/info"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body));

        return Network.rest(builder, Limit, client()).retry(withPolicy());
    }

    public static void main(String[] args) {
        Hyperliquid.BTC_USDC.executions(0, Support.computeID(Chrono.currentTimeMills())).waitForTerminate().to(e -> {
            System.out.println(e);
        });
    }

    public static void main2(String[] args) throws InterruptedException {
        Hyperliquid.BTC_USDC.executionsRealtimely().to(x -> {
            System.out.println(x);
        });
    }

    static class Topic extends IdentifiableTopic<Topic> {

        public String method = "subscribe";

        public Subscription subscription;

        /**
         * @param channel
         * @param market
         */
        private Topic(String channel, String market) {
            super(channel + "." + market);

            subscription = new Subscription();
            subscription.type = channel;
            subscription.coin = market;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean verifySubscribedReply(JSON reply) {
            JSON json = reply.get("data").get("subscription");
            return reply.has("channel", "subscriptionResponse") && json.has("type", subscription.type) && json
                    .has("coin", subscription.coin);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void buildUnsubscribeMessage(Topic topic) {
            topic.method = "unsubscribe";
        }
    }

    static class Subscription {

        public String type;

        public String coin;
    }
}