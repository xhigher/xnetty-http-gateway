package com.cheercent.xnetty.httpgateway.rpc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;

public class CallbackPool {

    private static final int INITIAL_CAPACITY = 128 * 4 / 3;

    private static final float LOAD_FACTOR = 0.75f;

    private static final int CONCURRENCY_LEVEL = 16;

    private static ConcurrentHashMap<String, CallbackContext> CALLBACK_MAP = new ConcurrentHashMap<String, CallbackContext>(
            INITIAL_CAPACITY, LOAD_FACTOR, CONCURRENCY_LEVEL);


    public static CallbackContext getContext(String requestId) {
        CallbackContext callbackContext = CALLBACK_MAP.get(requestId);
        return callbackContext == null ? null : callbackContext;
    }

    public static void put(String requestId, Channel channel, String allowOrigin, long timeout) {
        CALLBACK_MAP.putIfAbsent(requestId, new CallbackContext(requestId, channel, allowOrigin, timeout));
    }

    public static void remove(String requestId) {
        CALLBACK_MAP.remove(requestId);
    }

    public static void clear() {
        CALLBACK_MAP.clear();
    }

    public static ConcurrentHashMap<String, CallbackContext> getCALLBACK_MAP() {
        return CALLBACK_MAP;
    }
    
    public static List<CallbackContext> getRequestList() {
    	List<CallbackContext> list = new ArrayList<CallbackContext>(CALLBACK_MAP.values());
    	return list;
    }

}
