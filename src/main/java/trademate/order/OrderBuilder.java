/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package trademate.order;

import static java.util.concurrent.TimeUnit.*;

import java.math.RoundingMode;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javafx.scene.control.Spinner;
import javafx.scene.input.ScrollEvent;

import cointoss.Side;
import cointoss.order.Order;
import cointoss.order.OrderState;
import cointoss.util.Num;
import kiss.Extensible;
import kiss.I;
import kiss.WiseBiConsumer;
import stylist.Style;
import stylist.StyleDSL;
import trademate.TradeShacklesStyle;
import trademate.TradingView;
import trademate.order.OrderBuilder.Lang;
import trademate.setting.Notificator;
import viewtify.ui.UI;
import viewtify.ui.UIButton;
import viewtify.ui.UISpinner;
import viewtify.ui.UIText;
import viewtify.ui.View;
import viewtify.ui.helper.User;

/**
 * @version 2018/02/07 17:11:55
 */
public class OrderBuilder extends View<Lang> {

    private Predicate<UIText> positiveNumber = ui -> {
        try {
            return Num.of(ui.value()).isPositive();
        } catch (NumberFormatException e) {
            return false;
        }
    };

    private Predicate<UIText> negativeNumber = ui -> {
        try {
            return Num.of(ui.value()).isNegative();
        } catch (NumberFormatException e) {
            return false;
        }
    };

    /** UI */
    private UIText orderSize;

    /** UI */
    private UISpinner<Num> orderSizeAmount;

    /** UI */

    UIText orderPrice;

    /** UI */
    private UISpinner<Num> orderPriceAmount;

    /** UI */
    private UISpinner<Integer> orderDivideSize;

    /** UI */
    private UISpinner<Integer> orderDivideIntervalAmount;

    /** UI */
    private UISpinner<Num> optimizeThreshold;

    /** UI */
    private UIText orderPriceInterval;

    /** UI */
    private UISpinner<Num> orderPriceIntervalAmount;

    /** UI */
    private UIButton orderLimitLong;

    /** UI */
    private UIButton orderLimitShort;

    /** UI */
    private UIButton orderCancel;

    /** UI */
    private UIButton orderStop;

    /** UI */
    private UIButton orderReverse;

    /** UI */
    private TradingView view;

    private Notificator notificator = I.make(Notificator.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected UI declareUI() {
        return new UI() {
            {
                $(vbox, S.Root, () -> {
                    $(hbox, S.Row, () -> {
                        label("数量", S.Label);
                        $(orderSize, S.Form);
                        $(orderSizeAmount, S.FormMin);
                    });
                    $(hbox, S.Row, () -> {
                        label("価格", S.Label);
                        $(orderPrice, S.Form);
                        $(orderPriceAmount, S.FormMin);
                    });
                    $(hbox, S.Row, () -> {
                        label("分散数", S.Label);
                        $(orderDivideSize, S.Form);
                        $(orderDivideIntervalAmount, S.FormMin);
                    });
                    $(hbox, S.Row, () -> {
                        label("価格間隔", S.Label);
                        $(orderPriceInterval, S.Form);
                        $(orderPriceIntervalAmount, S.FormMin);
                    });
                    $(hbox, S.Row, () -> {
                        label("閾枚数", S.Label);
                        $(optimizeThreshold, S.Form);
                    });
                    $(hbox, S.Row, () -> {
                        label("指値", S.Label);
                        $(orderLimitShort, S.FormButton, TradeShacklesStyle.Short);
                        $(orderLimitLong, S.FormButton, TradeShacklesStyle.Long);
                    });
                    $(hbox, S.Row, () -> {
                        label("決済", S.Label);
                        $(orderCancel, S.FormButton);
                        $(orderStop, S.FormButton);
                        $(orderReverse, S.FormButton);
                    });
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        orderSize.initial("0").when(User.Scroll, changeBy(orderSizeAmount.ui)).require(positiveNumber).when(User.MiddleClick, e -> {
            orderSize.value(view.market().positions.size.toString());
        });
        orderSizeAmount.values(0, view.service.setting.targetCurrencyBidSizes());

        orderPrice.initial("0").when(User.Scroll, changeBy(orderPriceAmount.ui)).require(positiveNumber);
        orderPriceAmount.values(0, Num.ONE, Num.HUNDRED, Num.THOUSAND, Num.of(10000));

        orderDivideSize.values(0, IntStream.range(1, 31).boxed());
        orderDivideIntervalAmount.values(0, IntStream.range(0, 20).boxed()).disableWhen(orderDivideSize.ui.valueProperty().isEqualTo(1));
        optimizeThreshold.values(0, Num.range(0, 20));
        orderPriceInterval.initial("0")
                .when(User.Scroll, changeBy(orderPriceIntervalAmount.ui))
                .require(positiveNumber)
                .parent()
                .disableWhen(orderDivideSize.ui.valueProperty().isEqualTo(1));
        orderPriceIntervalAmount.values(0, Num.TEN, Num.HUNDRED, Num.THOUSAND);

        // validate order condition
        orderLimitLong.parent().disableWhen(orderSize.isInvalid(), orderPrice.isInvalid());

        orderLimitLong.text($.buy()).when(User.MouseClick).throttle(1000, MILLISECONDS).mapTo(Side.BUY).to(this::requestOrder);
        orderLimitShort.text($.sell()).when(User.MouseClick).throttle(1000, MILLISECONDS).mapTo(Side.SELL).to(this::requestOrder);

        orderCancel.text($.cancel()).when(User.MouseClick).to(view.market()::cancel);
        orderStop.text($.stop()).when(User.MouseClick).to(view.market()::stop);
        orderReverse.text($.reverse()).when(User.MouseClick).to(view.market()::reverse);
    }

    /**
     * Support wheel change.
     * 
     * @param source
     * @param amount
     * @return
     */
    private WiseBiConsumer<ScrollEvent, UIText> changeBy(Spinner<Num> spinner) {
        return (e, ui) -> {
            Num current = Num.of(ui.value());
            double deltaY = e.getDeltaY();

            if (deltaY > 0) {
                // increment
                ui.value(current.plus(spinner.getValue()).toString());
            } else if (deltaY < 0) {
                // decrement
                ui.value(Num.max(Num.ZERO, current.minus(spinner.getValue())).toString());
            }
        };
    }

    /**
     * Request order by API.
     * 
     * @param side
     * @return
     */
    private void requestOrder(Side side) {
        OrderSet set = new OrderSet();

        // ========================================
        // Create Model
        // ========================================
        Num size = orderSize.valueOr(Num.ZERO);
        Num initSize = size;
        Num price = orderPrice.valueOr(Num.ZERO);
        int divideSize = orderDivideSize.value();
        int increaseInterval = orderDivideIntervalAmount.value();
        Num priceInterval = orderPriceInterval.valueOr(Num.ZERO).multiply(side.isBuy() ? -1 : 1);

        for (int i = 0; i < divideSize; i++) {
            Num optimizedSize = increaseInterval == 0 ? Num.ZERO
                    : Num.of(i).divide(increaseInterval).scale(0, RoundingMode.FLOOR).multiply(initSize.divide(2));
            Num optimizedPrice = view.market().orderBook.computeBestPrice(side, price, optimizeThreshold.value(), Num.of(2));

            Order order = Order.limit(side, size.plus(optimizedSize), optimizedPrice);
            order.state.set(OrderState.REQUESTING);
            order.observeTerminating().to(() -> set.sub.remove(order));

            set.sub.add(order);

            price = optimizedPrice.plus(priceInterval);
        }
        view.order(set);
    }

    /**
     * @version 2018/09/09 9:14:18
     */
    private interface S extends StyleDSL {

        Style Root = () -> {
            padding.horizontal(10, px);
            display.minWidth(280, px);
        };

        Style Row = () -> {
            padding.top(8, px);
            text.verticalAlign.middle();
        };

        Style Label = () -> {
            display.width(60, px);
            display.height(27, px);
        };

        Style Form = () -> {
            display.maxWidth(100, px).height(27, px);
        };

        Style FormMin = () -> {
            display.maxWidth(70, px).height(27, px);
            margin.left(15, px);
        };

        Style FormButton = () -> {
            display.width(62, px).height(31, px);
        };
    }

    /**
     * @version 2018/09/07 10:29:37
     */
    static class Lang implements Extensible {

        String date() {
            return "Date";
        }

        String side() {
            return "Side";
        }

        String amount() {
            return "Amount";
        }

        String price() {
            return "Price";
        }

        String profit() {
            return "Profit";
        }

        String sell() {
            return "Sell";
        }

        String buy() {
            return "Buy";
        }

        String cancel() {
            return "Cancel";
        }

        String stop() {
            return "Stop";
        }

        String reverse() {
            return "Reverse";
        }

        /**
         * @version 2018/09/07 10:44:14
         */
        private static class Lang_ja extends Lang {

            /**
             * {@inheritDoc}
             */
            @Override
            String date() {
                return "日付";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String side() {
                return "売買";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String amount() {
                return "数量";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String price() {
                return "値段";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String profit() {
                return "損益";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String sell() {
                return "売り";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String buy() {
                return "買い";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String cancel() {
                return "キャンセル";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String stop() {
                return "撤退";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            String reverse() {
                return "ドテン";
            }
        }
    }
}
