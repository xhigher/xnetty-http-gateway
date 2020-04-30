package com.cheercent.xnetty.httpgateway.base;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpgateway.base.ServiceConfig.LogicRule;
import com.cheercent.xnetty.httpgateway.rpc.CallbackContext;
import com.cheercent.xnetty.httpgateway.rpc.CallbackPool;
import com.cheercent.xnetty.httpgateway.rpc.MessageFactory.MessageRequest;
import com.cheercent.xnetty.httpgateway.rpc.XClient;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public class XClientManager {

	private static final Logger logger = LoggerFactory.getLogger(XClientManager.class);
	
	public static final long REQUEST_TIMEOUT_MILLIS = 5000;
	
	private static XClientManager clientManager = null;
    private ServiceDiscovery serviceDiscovery;
    private ScheduledExecutorService requestTimeoutWatcher;
    
    private XClientManager(ServiceDiscovery serviceDiscovery){
    	this.serviceDiscovery = serviceDiscovery;
    	this.requestTimeoutWatcher = Executors.newSingleThreadScheduledExecutor();
    	this.requestTimeoutWatcher.scheduleAtFixedRate(new RequestTimeoutTask(), REQUEST_TIMEOUT_MILLIS, REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static void init(ServiceDiscovery serviceDiscovery){
        if(clientManager == null){
            synchronized (XClientManager.class){
                if(clientManager == null){
                	clientManager = new XClientManager(serviceDiscovery);
                }
            }
        }
    }
    
    public static LogicRule getLogicRule(String module, String action, int version){
    	return ServiceManager.getInstance().getLogicRule(module, action, version);
    }
    
    public static void sendResult(Channel channel, String allowOrigin, String responseContent){
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,Unpooled.wrappedBuffer(responseContent.getBytes()));
		if(responseContent != null && !responseContent.isEmpty()){
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=" + CharsetUtil.UTF_8.toString());
		}else{
			response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html; charset=" + CharsetUtil.UTF_8.toString());
		}
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH,response.content().readableBytes());
		if (allowOrigin != null) {
			response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
		}
		channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    public static void callService(Channel channel, String allowOrigin, String module, String action, int version, JSONObject parameters){
        try{
            XClient client = ServiceManager.getInstance().chooseClient(module, action, version);
            if(client == null){
            	sendResult(channel, allowOrigin, XLogic.errorRequestResult());
            	return;
            }
            
            MessageRequest request = new MessageRequest();
            request.setRequestid(UUID.randomUUID().toString()+"-"+System.currentTimeMillis());
            request.setModule(module);
            request.setAction(action);
            request.setVersion(version);
            request.setParameters(parameters);
            
            client.asyncTransport(channel, allowOrigin, request, REQUEST_TIMEOUT_MILLIS);

        }catch(Exception e){
        	logger.error("callService.Exception", e);
        	sendResult(channel, allowOrigin, XLogic.errorInternalResult());
        }
    }
    
    private void release() {
    	this.serviceDiscovery.stop();
    	this.requestTimeoutWatcher.shutdown();
    }
    
    public static void stop() {
        clientManager.release();
        ServiceManager.getInstance().stop();
    }
    
    class RequestTimeoutTask implements Runnable {

        @Override
        public void run() {
        	doDetect();
        }

        private synchronized void doDetect() {
            try {
            	int requestCount = 0;
            	int timeoutCount = 0;
                List<CallbackContext> requestList = CallbackPool.getRequestList();
                requestCount = requestList.size();
                if(requestCount > 0) {
                	for (CallbackContext context : requestList) {
                        if (System.currentTimeMillis() > context.getTimeoutMillis()) {
                        	CallbackPool.remove(context.getRequestId());
                        	XClientManager.sendResult(context.getChannel(), context.getAllowOrigin(), XLogic.errorInternalResult());
                        	timeoutCount ++;
                        }
                    }
                    logger.info("RequestTimeoutTask.doDetect: requestCount="+requestCount+", timeoutCount="+timeoutCount);
                }
            } catch (Exception e) {
            	logger.warn("Exception occurred when detecting timeout callbacks", e);
            }
        }
    }
}

