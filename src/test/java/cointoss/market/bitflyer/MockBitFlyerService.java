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

import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cointoss.MarketSetting;
import cointoss.execution.Execution;
import cointoss.order.Order;
import cointoss.order.OrderState;
import cointoss.util.Chrono;
import cointoss.util.MockNetwork;
import cointoss.util.Num;
import kiss.I;

/**
 * @version 2018/04/29 21:19:34
 */
class MockBitFlyerService extends BitFlyerService {

    /** The mockable network. */
    private final MockNetwork mockNetwork;

    /**
     * 
     */
    MockBitFlyerService() {
        super("FX_BTC_JPY", true, MarketSetting.with.baseCurrencyMinimumBidPrice(Num.ONE)
                .targetCurrencyMinimumBidSize(Num.of("0.01"))
                .orderBookGroupRanges(Num.ONE));

        network = mockNetwork = new MockNetwork();
    }

    /**
     * Set next response.
     * 
     * @param orderId
     */
    protected void requestWillResponse(String... orderIds) {
        mockNetwork.request("/v1/me/sendchildorder").willResponse((Object[]) orderIds);
    }

    /**
     * Set next response.
     * 
     * @param order
     * @param acceptanceId
     */
    protected void ordersWillResponse(Order order, String acceptanceId) {
        ChildOrderResponse o = new ChildOrderResponse();
        o.child_order_acceptance_id = acceptanceId;
        o.child_order_id = acceptanceId;
        o.side = order.direction;
        o.price = order.price;
        o.average_price = order.price;
        o.size = order.size;
        o.child_order_state = OrderState.ACTIVE;
        o.child_order_date = Chrono.DateTimeWithT.format(Chrono.utcNow());
        o.outstanding_size = order.size;
        o.executed_size = Num.ZERO;

        mockNetwork.request("/v1/me/getchildorders").willResponse(o);
    }

    /**
     * Set next response.
     * 
     * @param exe
     * @param buyerId
     * @param sellerId
     */
    protected void executionWillResponse(Execution exe, String buyerId, String sellerId) {
        Objects.requireNonNull(exe);
        Objects.requireNonNull(buyerId);
        Objects.requireNonNull(sellerId);

        JsonObject o = new JsonObject();
        o.addProperty("id", exe.id);
        o.addProperty("side", exe.direction.name());
        o.addProperty("price", exe.price.toDouble());
        o.addProperty("size", exe.size.toDouble());
        o.addProperty("exec_date", BitFlyerService.RealTimeFormat.format(exe.date) + "Z");
        o.addProperty("buy_child_order_acceptance_id", buyerId);

        o.addProperty("sell_child_order_acceptance_id", sellerId);

        JsonArray root = new JsonArray();
        root.add(o);

        mockNetwork.connect("wss://ws.lightstream.bitflyer.com/json-rpc").willResponse(I.json(root.toString()));
    }
}
