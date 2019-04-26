/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.verify;

import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import cointoss.Direction;
import cointoss.MarketService;
import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.order.Order;
import cointoss.order.OrderBookChange;
import cointoss.order.OrderState;
import cointoss.order.OrderType;
import cointoss.order.QuantityCondition;
import cointoss.util.Num;
import cointoss.util.RetryPolicy;
import kiss.Disposable;
import kiss.I;
import kiss.Signal;
import kiss.Signaling;
import kiss.Ⅲ;

/**
 * @version 2018/09/18 20:37:42
 */
public class VerifiableMarketService extends MarketService {

    /** The terminator. */
    private final Disposable diposer = Disposable.empty();

    /** The managed id. */
    private int id = 0;

    /** The order manager. */
    private final ConcurrentLinkedDeque<BackendOrder> orderActive = new ConcurrentLinkedDeque<>();

    /** The order manager. */
    private final ConcurrentLinkedQueue<BackendOrder> orderAll = new ConcurrentLinkedQueue<>();

    /** The order manager. */
    private final Signaling<Ⅲ<Direction, String, Execution>> positions = new Signaling();

    /** The execution manager. */
    private final LinkedList<Execution> executeds = new LinkedList();

    /** The lag generator. */
    private TimeLag lag = new TimeLag(0);

    /** The initial base currency. */
    private final Num baseCurrency = Num.HUNDRED;

    /** The initial target currency. */
    private final Num targetCurrency = Num.ZERO;

    /** The current time. */
    private ZonedDateTime now = TimeLag.Base;

    /**
     * 
     */
    public VerifiableMarketService() {
        super("TestableExchange", "TestableMarket", MarketSetting.builder()
                .baseCurrencyMinimumBidPrice(Num.ONE)
                .targetCurrencyMinimumBidSize(Num.ONE)
                .orderBookGroupRanges(Num.of(1))
                .retryPolicy(new RetryPolicy().retryMaximum(0))
                .build());
    }

    /**
     * 
     */
    public VerifiableMarketService(MarketService delegation) {
        super(delegation.exchangeName, delegation.marketName, delegation.setting.withRetryPolicy(new RetryPolicy().retryMaximum(0)));
    }

    /**
     * Configure fixed lag.
     * 
     * @param lag
     * @return
     */
    public final VerifiableMarketService lag(int lag) {
        this.lag = new TimeLag(lag);

        return this;
    }

    /**
     * Configure random lag.
     * 
     * @param lag
     * @return
     */
    public final VerifiableMarketService lag(int start, int end) {
        this.lag = new TimeLag(start, end);

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Integer> delay() {
        return I.signal(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void vandalize() {
        diposer.dispose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<String> request(Order order) {
        return I.signal(order).map(o -> {
            BackendOrder child = new BackendOrder(order);
            child.id.let("LOCAL-ACCEPTANCE-" + id++);
            child.state.set(OrderState.ACTIVE);
            child.creationTime.set(now.plusNanos(lag.generate()));
            child.remainingSize.set(order.size);

            orderAll.add(child);
            orderActive.add(child);
            return child.id.v;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Order> cancel(Order order) {
        return new Signal<>((observer, disposer) -> {
            orderActive.removeIf(o -> o.id.equals(order.id));
            I.signal(orderAll).take(o -> o.id.equals(order.id)).take(1).to(o -> {
                o.state.set(OrderState.CANCELED);
                observer.accept(order);
                observer.complete();
            });
            return disposer;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionsRealtimely() {
        return I.signal(executeds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Ⅲ<Direction, String, Execution>> executionsRealtimelyForMe() {
        return positions.expose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executionLatest() {
        // If this exception will be thrown, it is bug of this program. So we must rethrow the
        // wrapped error in here.
        throw new Error();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Execution> executions(long start, long end) {
        return I.signal(executeds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Order> orders() {
        return I.signal(orderAll).as(Order.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Num> baseCurrency() {
        return I.signal(baseCurrency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<Num> targetCurrency() {
        return I.signal(targetCurrency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signal<OrderBookChange> orderBook() {
        return Signal.never();
    }

    /**
     * Emulate {@link Execution}.
     * 
     * @param e
     * @return
     */
    public Execution emulate(Execution e) {
        now = e.date;

        // emulate market execution
        Iterator<BackendOrder> iterator = orderActive.iterator();

        while (iterator.hasNext()) {
            BackendOrder order = iterator.next();
            System.out.println(e.date + "  " + order.creationTime + "   " + order);
            // time base filter
            if (e.date.isBefore(order.creationTime.get())) {
                continue;
            }

            // check quantity condition
            if (order.condition == QuantityCondition.FillOrKill && !validateTradable(order, e)) {
                iterator.remove();
                orderAll.remove(order);
                continue;
            }

            if (order.condition == QuantityCondition.ImmediateOrCancel) {
                if (validateTradableByPrice(order, e)) {
                    order.remainingSize.set(v -> Num.min(e.size, v));
                } else {
                    iterator.remove();
                    orderAll.remove(order);
                    continue;
                }
            }

            if (validateTradableByPrice(order, e)) {
                Num executedSize = Num.min(e.size, order.remainingSize.v);
                if (order.type.isMarket() && executedSize.isNot(0)) {
                    order.marketMinPrice = order.isBuy() ? Num.max(order.marketMinPrice, e.price) : Num.min(order.marketMinPrice, e.price);
                    order.price(order.price.multiply(order.executedSize)
                            .plus(order.marketMinPrice.multiply(executedSize))
                            .divide(executedSize.plus(order.executedSize)));
                }
                order.executedSize.set(v -> v.plus(executedSize));
                order.remainingSize.set(v -> v.minus(executedSize));

                Execution exe = new Execution();
                exe.side = order.direction();
                exe.size = exe.cumulativeSize = executedSize;
                exe.price = order.type.isMarket() ? order.marketMinPrice : order.price;
                exe.date = e.date;
                executeds.add(exe);

                if (order.remainingSize.v.isZero()) {
                    order.state.set(OrderState.COMPLETED);
                    iterator.remove();
                }
                positions.accept(I.pair(exe.side, order.id.v, exe));

                // replace execution info
                e.side = exe.side;
                e.size = e.cumulativeSize = exe.size;
                e.price = exe.price;
                break;
            }
        }
        return e;
    }

    /**
     * Test whether this order can trade with the specified {@link Execution}.
     * 
     * @param e A target {@link Execution}.
     * @return A result.
     */
    private boolean validateTradable(Order order, Execution e) {
        return validateTradableBySize(order, e) && validateTradableByPrice(order, e);
    }

    /**
     * Test whether this order price can trade with the specified {@link Execution}.
     * 
     * @param e A target {@link Execution}.
     * @return A result.
     */
    private boolean validateTradableByPrice(Order order, Execution e) {
        if (order.type == OrderType.MARKET) {
            return true;
        }

        if (order.isBuy()) {
            Num price = order.price;

            return price.isGreaterThan(e.price) || price.is(setting.baseCurrencyMinimumBidPrice());
        } else {
            return order.price.isLessThan(e.price);
        }
    }

    /**
     * Test whether this order size can trade with the specified {@link Execution}.
     * 
     * @param e A target {@link Execution}.
     * @return A result.
     */
    private boolean validateTradableBySize(Order order, Execution e) {
        return order.size.isLessThanOrEqual(e.size);
    }

    /**
     * For test.
     */
    private static class BackendOrder extends Order {

        /** The minimum price for market order. */
        private Num marketMinPrice = isBuy() ? Num.ZERO : Num.MAX;

        /**
         * @param o
         */
        private BackendOrder(Order o) {
            super(o.direction(), o.size);

            price(o.price);
            type(o.condition);
        }
    }
}
