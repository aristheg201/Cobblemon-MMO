package vn.svframe.svframelib.api.stat.handler;

import vn.svframe.svframelib.api.stat.StatInstance;

@FunctionalInterface
public interface StatUpdateListener {
    void onUpdate(StatInstance instance);
}
