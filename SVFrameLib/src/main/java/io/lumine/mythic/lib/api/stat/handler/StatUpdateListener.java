package io.lumine.mythic.lib.api.stat.handler;

import io.lumine.mythic.lib.api.stat.StatInstance;

@FunctionalInterface
public interface StatUpdateListener {
    void onUpdate(StatInstance instance);
}
