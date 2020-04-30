package com.cheercent.xnetty.httpgateway.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpgateway.base.XClientManager;
import com.cheercent.xnetty.httpgateway.base.XLogic;
import com.cheercent.xnetty.httpgateway.rpc.MessageFactory.MessageResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class XClientHandler extends SimpleChannelInboundHandler<JSONObject> {
    
	private static final Logger logger = LoggerFactory.getLogger(XClientHandler.class);

	private int lostEchoCount = 0;
	
	private XClient client;
	
	public XClientHandler(XClient client) {
		this.client = client;
	}
	
    @Override
    public void channelRead0(ChannelHandlerContext ctx, JSONObject data) throws Exception {
    	logger.info("channelRead0: data = {}", data.toString());
    	MessageResponse response = MessageFactory.toMessageResponse(data);
    	if(response != null) {
    		String requestid = response.getRequestid();
            try {
            	if(!MessageFactory.isHeartBeatMessage(requestid)) {
            		CallbackContext context = CallbackPool.getContext(requestid);
                    if (context != null) {
                        if(MessageFactory.isSuccessful(response)){
                        	XClientManager.sendResult(context.getChannel(), context.getAllowOrigin(), XLogic.errorRequestResult());
                        	return;
                        }
                        XClientManager.sendResult(context.getChannel(), context.getAllowOrigin(), response.getData());
                    }else {
                    	logger.warn("Receive msg from server but no context found, requestid=" + requestid);
                    }
            	}
            } finally {
            	CallbackPool.remove(requestid);
            }
    	}
    	
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            IdleStateEvent event = (IdleStateEvent)evt;
            if (event.state() == IdleState.READER_IDLE){
            	try {
                    lostEchoCount ++;
                    if (lostEchoCount > 2){
                    	lostEchoCount = 0;
                        this.client.tryToReconnect();
                    }else {
                        ctx.writeAndFlush(MessageFactory.newHeartBeatMessage().toJSONObject());
                    }
            	}catch(Exception e) {
            		lostEchoCount = 0;
            		logger.error(ctx.channel().remoteAddress().toString(), e);
            		this.client.tryToReconnect();
            	}
            }
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(ctx.channel().remoteAddress().toString(), cause);
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }



}
