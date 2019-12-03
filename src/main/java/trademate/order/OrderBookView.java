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

import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;

import cointoss.order.OrderBookManager;
import cointoss.order.OrderUnit;
import cointoss.util.Num;
import stylist.Style;
import stylist.StyleDSL;
import trademate.TradeMateStyle;
import trademate.TradingView;
import viewtify.Viewtify;
import viewtify.ui.UI;
import viewtify.ui.UILabel;
import viewtify.ui.UIListView;
import viewtify.ui.UISpinner;
import viewtify.ui.View;
import viewtify.util.FXUtils;

public class OrderBookView extends View {

    /** UI for long maker. */
    private UIListView<OrderUnit> longList;

    /** UI for maker. */
    private UIListView<OrderUnit> shortList;

    /** UI for interval configuration. */
    private UISpinner<Num> priceRange;

    /** UI for interval configuration. */
    private UILabel priceLatest;

    /** UI for interval configuration. */
    private UILabel priceSpread;

    /** UI for interval configuration. */
    private UISpinner<Num> hideSize;

    /** Parent View */
    private TradingView view;

    /** Order Book. */
    private OrderBookManager book;

    /**
     * UI definition.
     */
    class view extends UI {
        {
            $(vbox, style.root, () -> {
                $(shortList, style.book);
                $(hbox, () -> {
                    $(priceRange, style.priceRange);
                    $(priceLatest, style.priceLatest);
                    $(priceSpread, style.priceSpread);
                    $(hideSize, style.hideSize);
                });
                $(longList, style.book);
            });
        }
    }

    /**
     * 
     */
    interface style extends StyleDSL {

        Style root = () -> {
            display.minWidth(220, px).maxWidth(220, px);
        };

        Style book = () -> {
            text.unselectable();

            $.select(".scroll-bar:horizontal").descendant(() -> {
                padding.size(0, px);
            });
        };

        Style priceRange = () -> {
            display.width(72, px);
        };

        Style priceLatest = () -> {
            display.width(60, px).height(25, px);
            text.indent(12, px);
        };

        Style priceSpread = () -> {
            display.width(50, px).height(25, px);
        };

        Style hideSize = () -> {
            display.width(60, px);
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        book = view.market.orderBook;
        book.longs.setContainer(FXCollections::observableList);
        book.shorts.setContainer(FXCollections::observableList);
        book.longs.setOperator(Viewtify.UIThread);
        book.shorts.setOperator(Viewtify.UIThread);

        hideSize.values(0, Num.range(0, 99));

        int scale = view.market.service.setting.targetCurrencyScaleSize;
        longList.renderByNode(displayOrderUnit(TradeMateStyle.BUY, scale))
                .take(hideSize, (unit, size) -> unit.size.isGreaterThanOrEqual(size));
        shortList.renderByNode(displayOrderUnit(TradeMateStyle.SELL, scale))
                .take(hideSize, (unit, size) -> unit.size.isGreaterThanOrEqual(size))
                .scrollToBottom();

        priceRange.values(0, view.market.service.setting.orderBookGroupRangesWithBase()).observeNow(range -> {
            longList.items((ObservableList) book.longs.selectBy(range));
            shortList.items((ObservableList) book.shorts.selectBy(range));
        });

        view.market.tickers.latest.observe().skipWhile(view.initializing).on(Viewtify.UIThread).to(e -> priceLatest.text(e.price));
        view.market.orderBook.spread.observe().skipWhile(view.initializing).on(Viewtify.UIThread).to(price -> priceSpread.text(price));
    }

    /**
     * Rendering {@link OrderUnit}.
     * 
     * @param color
     * @param scale
     * @return
     */
    private Function<OrderUnit, Canvas> displayOrderUnit(stylist.value.Color color, int scale) {
        double width = longList.ui.widthProperty().doubleValue();
        double height = 17;
        double fontSize = 12;
        Color foreground = FXUtils.color(color);
        Color background = foreground.deriveColor(0, 1, 1, 0.2);
        Font font = Font.font(fontSize);

        return e -> {
            Num size = e.size.scale(scale);
            double range = Math.min(width, size.doubleValue());

            Canvas canvas = new Canvas(width, height);
            GraphicsContext c = canvas.getGraphicsContext2D();
            c.setFill(background);
            c.fillRect(0, 0, range, height);

            c.setFont(font);
            c.setFill(foreground);
            c.setFontSmoothingType(FontSmoothingType.LCD);
            c.fillText(e.price + " " + size, 72, height - 3, width - 72 - 10);

            return canvas;
        };
    }
}
