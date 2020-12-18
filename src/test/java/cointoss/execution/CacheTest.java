/*
 * Copyright (C) 2019 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.execution;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import antibug.CleanRoom;
import cointoss.execution.ExecutionLog.Cache;
import cointoss.util.Chrono;
import cointoss.verify.VerifiableMarket;
import kiss.I;
import kiss.Signal;
import psychopath.Locator;

class CacheTest {

    @RegisterExtension
    CleanRoom room = new CleanRoom();

    VerifiableMarket market = new VerifiableMarket();

    ExecutionLog log = new ExecutionLog(market.service, Locator.directory(room.root));

    @Test
    void normalLog() {
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        assert cache.normal.name().equals("execution20201215.log");
    }

    @Test
    void compactLog() {
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        assert cache.compactLog().name().equals("execution20201215.clog");
    }

    @Test
    void fastLog() {
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        assert cache.fastLog().name().equals("execution20201215.flog");
    }

    @Test
    void writeNormal() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        assert cache.existNormal() == false;

        cache.writeNormal(e1, e2);
        assert cache.existNormal();
    }

    @Test
    void writeCompact() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        assert cache.existCompact() == false;

        cache.writeCompact(e1, e2);
        assert cache.existCompact();
    }

    @Test
    void readNormal() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeNormal(e1, e2);

        // read
        List<Execution> executions = cache.readNormal().toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(e1);
        assert executions.get(1).equals(e2);
    }

    @Test
    void readCompact() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeCompact(e1, e2);

        // read
        List<Execution> executions = cache.readCompact().toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(e1);
        assert executions.get(1).equals(e2);
    }

    @Test
    void readFromNotNormalButCompact() {
        Execution n1 = Execution.with.buy(1).price(10);
        Execution n2 = Execution.with.buy(1).price(12);
        Execution c1 = Execution.with.buy(2).price(10);
        Execution c2 = Execution.with.buy(2).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeNormal(n1, n2);
        cache.writeCompact(c1, c2);

        // read
        List<Execution> executions = cache.read().toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(c1);
        assert executions.get(1).equals(c2);
    }

    @Test
    void readExternalRepository() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        ExecutionLogRepository external = new ExecutionLogRepository(market.service) {

            @Override
            public Signal<Execution> convert(ZonedDateTime date) {
                return I.signal(e1, e2);
            }

            @Override
            public Signal<ZonedDateTime> collect() {
                return I.signal(Chrono.utc(2020, 12, 15));
            }
        };

        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        List<Execution> executions = cache.readExternalRepository(external).toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(e1);
        assert executions.get(1).equals(e2);
        assert cache.existNormal();
        assert cache.existCompact() == false;
    }

    @Test
    void convertNormalToCompact() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeNormal(e1, e2);
        assert cache.existNormal();
        assert cache.existCompact() == false;

        // convert
        cache.convertNormalToCompact();
        assert cache.existNormal() == false;
        assert cache.existCompact();

        // read from compact
        List<Execution> executions = cache.read().toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(e1);
        assert executions.get(1).equals(e2);
    }

    @Test
    void convertCompactToNormal() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeCompact(e1, e2);
        assert cache.existNormal() == false;
        assert cache.existCompact();

        // convert
        cache.convertCompactToNormal();
        assert cache.existNormal();
        assert cache.existCompact();

        // read from normal
        List<Execution> executions = cache.readNormal().toList();
        assert executions.size() == 2;
        assert executions.get(0).equals(e1);
        assert executions.get(1).equals(e2);
    }

    @Test
    void convertCompactToFast() {
        Execution e1 = Execution.with.buy(1).price(10);
        Execution e2 = Execution.with.buy(1).price(12);

        // write
        Cache cache = log.cache(Chrono.utc(2020, 12, 15));
        cache.writeCompact(e1, e2);
        assert cache.existCompact();
        assert cache.existFast() == false;

        // convert
        cache.convertCompactToFast();
        assert cache.existCompact();
        assert cache.existFast();

        // read from fast
        List<Execution> executions = cache.readFast().toList();
        assert executions.size() == 4;
    }

    @Test
    void existCompletedNormalOnCompleted() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Execution r1 = Execution.with.buy(1).price(12).date(date.plusDays(1));
        market.service.executionsWillResponse(r1);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert cache.existCompletedNormal();
    }

    @Test
    void existCompletedNormalOnImcompleted() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Execution r1 = Execution.with.buy(1).price(14).date(date);
        market.service.executionsWillResponse(r1);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert cache.existCompletedNormal() == false;
    }

    @Test
    void repairWithCompactLog() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Cache cache = log.cache(date);
        cache.writeCompact(e1, e2);
        assert cache.repair();
        assert cache.existCompact();
        assert cache.existNormal() == false;
    }

    @Test
    void repairWithComletedNormalLog() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Execution r1 = Execution.with.buy(1).price(14).date(date.plusDays(1));
        market.service.executionsWillResponse(r1);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert cache.repair();
        assert cache.existCompact();
        assert cache.existNormal() == false;
    }

    @Test
    void repairWithIncomletedNormalLogAndExternalRepository() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Execution r1 = Execution.with.buy(1).price(14).date(date);
        market.service.executionsWillResponse(r1);
        market.service.external = useExternalRepository(date);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert cache.repair();
        assert cache.existCompact();
        assert cache.existNormal() == false;
    }

    @Test
    void repairWithNoNormalLogAndExternalRepository() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);

        Execution r1 = Execution.with.buy(1).price(14).date(date);
        market.service.executionsWillResponse(r1);
        market.service.external = useExternalRepository(date);

        Cache cache = log.cache(date);
        assert cache.repair();
        assert cache.existCompact();
        assert cache.existNormal() == false;
    }

    /**
     * Helper to create {@link ExecutionLogRepository} which has the completed log at the specified
     * date.
     * 
     * @return
     */
    private ExecutionLogRepository useExternalRepository(ZonedDateTime target) {
        Objects.requireNonNull(target);

        return new ExecutionLogRepository(market.service) {

            @Override
            public Signal<Execution> convert(ZonedDateTime date) {
                if (target.isEqual(date)) {
                    Execution e1 = Execution.with.buy(1).price(10).date(date);
                    Execution e2 = Execution.with.buy(1).price(12).date(date);
                    Execution e3 = Execution.with.sell(1).price(12).date(date);

                    return I.signal(e1, e2, e3);
                } else {
                    return I.signal();
                }
            }

            @Override
            public Signal<ZonedDateTime> collect() {
                return I.signal(target);
            }
        };
    }

    @Test
    void repairWithIncomletedNormalLog() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Execution r1 = Execution.with.buy(1).price(13).date(date);
        Execution r2 = Execution.with.sell(1).price(14).date(date);
        Execution r3 = Execution.with.buy(1).price(15).date(date.plusDays(1));
        market.service.executionsWillResponse(r1);
        market.service.executionsWillResponse(e2, r1, r2, r3);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert cache.repair();
        assert checkCompact(cache, e1, e2, r1, r2);
        assert cache.existNormal() == false;
    }

    @Test
    void flushOnNoFile() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(12).date(date);

        Cache cache = log.cache(date);
        cache.flush(List.of(e1, e2));
        assert cache.existNormal();
        assert checkNormal(cache, e1, e2);
    }

    @Test
    void flushOnImcompleted() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(11).date(date);
        Execution e3 = Execution.with.buy(1).price(12).date(date);
        Execution e4 = Execution.with.buy(1).price(13).date(date);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert checkNormal(cache, e1, e2);

        cache.flush(List.of(e3, e4));
        assert cache.existNormal();
        assert checkNormal(cache, e1, e2, e3, e4);
    }

    @Test
    void flushAlreadyWrittenExecutions() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(11).date(date);

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert checkNormal(cache, e1, e2);

        cache.flush(List.of(e1, e2));
        assert cache.existNormal();
        assert checkNormal(cache, e1, e2);
    }

    @Test
    void flushExecutionsOnDifferentDay() {
        ZonedDateTime date = Chrono.utc(2020, 12, 15);
        Execution e1 = Execution.with.buy(1).price(10).date(date);
        Execution e2 = Execution.with.buy(1).price(11).date(date);
        Execution next = Execution.with.buy(1).price(10).date(date.plusDays(1));
        Execution previous = Execution.with.buy(1).price(11).date(date.minusDays(1));

        Cache cache = log.cache(date);
        cache.writeNormal(e1, e2);
        assert checkNormal(cache, e1, e2);

        cache.flush(List.of(next, previous));
        assert cache.existNormal();
        assert checkNormal(cache, e1, e2);
    }

    /**
     * Assert log.
     * 
     * @param cache
     * @param executions
     * @return
     */
    private boolean checkNormal(Cache cache, Execution... executions) {
        List<Execution> list = cache.readNormal().toList();
        assert list.size() == executions.length;
        for (int i = 0; i < executions.length; i++) {
            assert list.get(i).equals(executions[i]);
        }
        return true;
    }

    /**
     * Assert log.
     * 
     * @param cache
     * @param executions
     * @return
     */
    private boolean checkCompact(Cache cache, Execution... executions) {
        List<Execution> list = cache.readCompact().toList();
        assert list.size() == executions.length;
        for (int i = 0; i < executions.length; i++) {
            assert list.get(i).equals(executions[i]);
        }
        return true;
    }
}
