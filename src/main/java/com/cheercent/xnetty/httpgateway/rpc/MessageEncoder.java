package com.cheercent.xnetty.httpgateway.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MessageEncoder extends MessageToByteEncoder<JSONObject> {
	
	private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);
	
    @Override
    public void encode(ChannelHandlerContext ctx, JSONObject data, ByteBuf out) throws Exception {
    	try{
			byte[] bytes = data.toString().getBytes();
			out.writeInt(bytes.length);
			out.writeBytes(bytes);
    	}catch(Exception e){
    		logger.error("encode", e);
    	}
    }
}
