/*
 * Copyright (C) 2020 cointoss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cointoss.execution.Execution;
import cointoss.execution.ExecutionLog;
import cointoss.execution.ExecutionLogRepository;
import cointoss.market.Exchange;
import cointoss.market.MarketServiceProvider;
import cointoss.order.Order;
import cointoss.order.OrderBookPageChanges;
import cointoss.order.OrderState;
import cointoss.util.Chrono;
import cointoss.util.EfficientWebSocket;
import cointoss.util.RetryPolicy;
import cointoss.util.arithmetic.Num;
import kiss.Decoder;
import kiss.Disposable;
import kiss.Encoder;
import kiss.Signal;
import psychopath.Directory;
import psychopath.Locator;

public abstract class MarketService implements Comparable<MarketService>, Disposable {

    /** The logging system. */
    protected static final Logger logger = LogManager.getLogger(MarketService.class);

    /** The exchange. */
    public final Exchange exchange;

    /** The market name. */
    public final String marketName;

    /** The human-readable market name. */
    public final String marketReadableName;

    /** The execution log. */
    public final ExecutionLog log;

    /** The service disposer. */
    protected final Disposable disposer = Disposable.empty();

    /** The market configuration. */
    public final MarketSetting setting;

    /** The market specific scheduler. */
    private final ScheduledThreadPoolExecutor scheduler;

    /** The shared real-time execution log. */
    private Signal<Execution> executions;

    /** The shared real-time order. */
    private Signal<Order> orders;

    /** The shared real-time order book. */
    private Signal<OrderBookPageChanges> orderBooks;

    /**
     * @param exchange
     * @param marketName
     */
    protected MarketService(Exchange exchange, String marketName, MarketSetting setting) {
        this.exchange = Objects.requireNonNull(exchange);
        this.marketName = Objects.requireNonNull(marketName);
        this.marketReadableName = marketIdentity().replaceAll("_", "");
        this.setting = setting;
        this.scheduler = new ScheduledThreadPoolExecutor(2, task -> {
            Thread thread = new Thread(task);
            thread.setName(marketIdentity() + " Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.allowCoreThreadTimeOut(true);
        this.scheduler.setKeepAliveTime(30, TimeUnit.SECONDS);

        this.log = new ExecutionLog(this);
    }

    /**
     * Returns the identity of market.
     * 
     * @return A market identity.
     */
    public final String marketIdentity() {
        return exchange + " " + marketName;
    }

    /**
     * Returns the root directory of this service.
     * 
     * @return
     */
    public final Directory directory() {
        return Locator.directory(".log").directory(exchange.name()).directory(marketName.replace(':', '-'));
    }

    /**
     * Return the http communicator.
     * 
     * @return
     */
    protected HttpClient client() {
        return null;
    }

    /**
     * Return the realtime communicator.
     * 
     * @return
     */
    protected abstract EfficientWebSocket clientRealtimely();

    /**
     * Request order actually.
     * 
     * @param order A order to request.
     * @return A requested order.
     */
    public abstract Signal<String> request(Order order);

    /**
     * Request canceling order actually.
     * 
     * @param order A order to cancel.
     * @return A cancelled order result (state, remainingSize, executedSize).
     */
    public abstract Signal<Order> cancel(Order order);

    /**
     * Acquire the execution log between start (exclusive) and end (exclusive) key.
     * 
     * @param key An execution sequencial key (i.e. ID, datetime etc).
     * @return This {@link Signal} will be completed immediately.
     */
    public abstract Signal<Execution> executions(long startId, long endId);

    /**
     * Acquire execution log in realtime. This is infinitely.
     * 
     * @return A shared realtime execution logs.
     */
    public final synchronized Signal<Execution> executionsRealtimely() {
        return executionsRealtimely(true);
    }

    /**
     * Acquire execution log in realtime. This is infinitely.
     * 
     * @param autoReconnect Need to reconnect automatically.
     * @return A shared realtime execution logs.
     */
    public final synchronized Signal<Execution> executionsRealtimely(boolean autoReconnect) {
        if (executions == null) {
            executions = connectExecutionRealtimely().effectOnObserve(disposer::add);
        }

        if (autoReconnect) {
            return executions.retryWhen(retryPolicy(500, "ExecutionRealtimely"));
        } else {
            return executions;
        }
    }

    /**
     * Connect to the realtime execution log stream.
     * 
     * @return A realtime execution logs.
     */
    protected abstract Signal<Execution> connectExecutionRealtimely();

    /**
     * Acquier the latest execution log.
     * 
     * @return A latest execution log.
     */
    public abstract Signal<Execution> executionLatest();

    /**
     * Acquier the latest execution log.
     * 
     * @return A latest execution log.
     */
    public abstract Signal<Execution> executionLatestAt(long id);

    /**
     * Checks whether the specified {@link Execution}s are the same.
     * 
     * @param one Non-null target.
     * @param other Non-null target.
     * @return Result.
     */
    public boolean checkEquality(Execution one, Execution other) {
        return one.id == other.id;
    }

    public long estimateAcquirableExecutionIdRange(double factor) {
        return setting.acquirableExecutionSize;
    }

    /**
     * Estimate the inital execution id of the {@link Market}.
     * 
     * @return
     */
    public long estimateInitialExecutionId() {
        long start = 0;
        long end = executionLatest().waitForTerminate().to().exact().id;
        long middle = (start + end) / 2;

        while (true) {
            List<Execution> result = executionLatestAt(middle).skipError().waitForTerminate().toList();
            if (result.isEmpty()) {
                start = middle;
                middle = (start + end) / 2;
            } else {
                end = result.get(0).id + 1;
                middle = (start + end) / 2;
            }

            if (end - start <= 10) {
                return start;
            }
        }
    }

    /**
     * Return {@link ExecutionLog} of this market.
     * 
     * @return
     */
    public final ExecutionLog log() {
        return log;
    }

    /**
     * Request all orders.
     * 
     * @return
     */
    public abstract Signal<Order> orders();

    /**
     * Request all orders with the specified state.
     * 
     * @return
     */
    public abstract Signal<Order> orders(OrderState state);

    /**
     * Acquire the order state in realtime. This is infinitely.
     * 
     * @return A event stream of order state.
     */
    public final synchronized Signal<Order> ordersRealtimely() {
        if (orders == null) {
            orders = connectOrdersRealtimely().effectOnObserve(disposer::add).retryWhen(retryPolicy(500, "OrderRealtimely"));
        }
        return orders;
    }

    /**
     * Connect to the realtime order stream.
     * 
     * @return
     */
    protected abstract Signal<Order> connectOrdersRealtimely();

    /**
     * <p>
     * Get amount of the base and target currency.
     * </p>
     * 
     * @return
     */
    public abstract Signal<OrderBookPageChanges> orderBook();

    /**
     * Acquire order book in realtime. This is infinitely.
     * 
     * @return A shared realtime order books.
     */
    public final synchronized Signal<OrderBookPageChanges> orderBookRealtimely() {
        return orderBookRealtimely(true);
    }

    /**
     * Acquire order book in realtime. This is infinitely.
     * 
     * @param autoReconnect Need to reconnect automatically.
     * @return A shared realtime order books.
     */
    public final synchronized Signal<OrderBookPageChanges> orderBookRealtimely(boolean autoReconnect) {
        if (orderBooks == null) {
            orderBooks = orderBook().concat(connectOrderBookRealtimely()).effectOnObserve(disposer::add);
        }

        if (autoReconnect) {
            return orderBooks.retryWhen(retryPolicy(500, "OrderBookRealtimely"));
        } else {
            return orderBooks;
        }
    }

    /**
     * Connect to the realtime order book stream.
     * 
     * @return A realtime order books.
     */
    protected abstract Signal<OrderBookPageChanges> connectOrderBookRealtimely();

    /**
     * Calculate human-readable price for display.
     * 
     * @param price A target price.
     * @return
     */
    public String calculateReadablePrice(double price) {
        return Num.of(price).scale(setting.base.scale).toString();
    }

    /**
     * Calculate human-readable data-time for display.
     * 
     * @param seconds A target time. (second)
     * @return
     */
    public String calculateReadableTime(double seconds) {
        ZonedDateTime time = Chrono.systemBySeconds((long) seconds);

        if (time.getMinute() == 0 && time.getHour() % 6 == 0) {
            return time.format(Chrono.DateTimeWithoutSec);
        } else {
            return time.format(Chrono.TimeWithoutSec);
        }
    }

    /**
     * Get amount of the base currency.
     */
    public abstract Signal<Num> baseCurrency();

    /**
     * Get amount of the target currency.
     */
    public abstract Signal<Num> targetCurrency();

    /**
     * Get the current time.
     * 
     * @return The current time.
     */
    public ZonedDateTime now() {
        return Chrono.utcNow();
    }

    /**
     * Get the current nano-time.
     * 
     * @return The current time.
     */
    public long nano() {
        return System.nanoTime();
    }

    /**
     * Get the market scheduler.
     * 
     * @return A scheduler.
     */
    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    /**
     * Get the external log repository.
     * 
     * @return
     */
    public ExecutionLogRepository externalRepository() {
        return null;
    }

    /**
     * Create new {@link RetryPolicy}.
     * 
     * @param max The maximum number to retry.
     * @return
     */
    public final RetryPolicy retryPolicy(int max) {
        return retryPolicy(max, null);
    }

    /**
     * Create new {@link RetryPolicy}.
     * 
     * @param max The maximum number to retry.
     * @return
     */
    public RetryPolicy retryPolicy(int max, String name) {
        return RetryPolicy.with.limit(max)
                .delayLinear(Duration.ofSeconds(2))
                .scheduler(scheduler())
                .name(name == null || name.length() == 0 ? null : marketIdentity() + " : " + name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void vandalize() {
        disposer.dispose();
        scheduler.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(MarketService o) {
        return marketIdentity().compareTo(o.marketIdentity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return marketIdentity();
    }

    /**
     * Codec.
     */
    @SuppressWarnings("unused")
    private static class Codec implements Decoder<MarketService>, Encoder<MarketService> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String encode(MarketService value) {
            return value.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MarketService decode(String value) {
            return MarketServiceProvider.by(value).or((MarketService) null);
        }
    }
}