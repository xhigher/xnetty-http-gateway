package com.cheercent.xnetty.httpgateway.rpc;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheercent.xnetty.httpgateway.rpc.MessageFactory.MessageRequest;
import com.cheercent.xnetty.httpgateway.rpc.XException.ExceptionType;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.DefaultThreadFactory;

public class XClient {

    private static final Logger logger = LoggerFactory.getLogger(XClient.class);
 
    private boolean soKeepalive = true;

    private boolean soReuseaddr = true;

    private boolean tcpNodelay = true;
    
    private int connTimeout = 5000;

    private int soRcvbuf = 1024 * 128;

    private int soSndbuf = 1024 * 128;
    
    private String serverIP;

    private int serverPort;

    private Bootstrap bootstrap;
    private NioEventLoopGroup eventLoopGroup;
    
    private ChannelFuture channelFuture;

    public XClient(String ip, int port) {
    	serverIP = ip;
		serverPort = port;

        bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connTimeout);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, this.soKeepalive);
        bootstrap.option(ChannelOption.SO_REUSEADDR, this.soReuseaddr);
        bootstrap.option(ChannelOption.TCP_NODELAY, this.tcpNodelay);
        bootstrap.option(ChannelOption.SO_RCVBUF, this.soRcvbuf);
        bootstrap.option(ChannelOption.SO_SNDBUF, this.soSndbuf);
        
        ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline cp = ch.pipeline();
                cp.addLast(new MessageEncoder());
                cp.addLast(new LengthFieldBasedFrameDecoder(1024*1024*10, 0, 4, 0, 0));
                cp.addLast(new MessageDecoder());
                cp.addLast(new XClientHandler(XClient.this));
            }
        };
        eventLoopGroup = new NioEventLoopGroup(1, new DefaultThreadFactory(serverIP.substring(serverIP.lastIndexOf(".")+1)+"-"+serverPort+"-client-ip"));
        bootstrap.group(eventLoopGroup).handler(initializer);
        
        channelFuture = this.connect();
    }

    private ChannelFuture connect() {
        try {
        	ChannelFuture channelFuture = bootstrap.connect(new InetSocketAddress(serverIP, serverPort));
        	channelFuture.addListener(new ChannelFutureListener() {
        		
        		@Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (channelFuture.isSuccess()) {
                    	logger.info("Connection " + channelFuture.channel() + " is well established");
                    } else {
                    	logger.warn(String.format("Connection get failed on %s due to %s", channelFuture.cause().getMessage(), channelFuture.cause()));
                    }
                }
            });
            logger.info("connect to " + getAddress());
            return channelFuture;
        } catch (Exception e) {
        	logger.error("Failed to connect to " + getAddress(), e);
        }
        return null;
    }
    

    private ChannelFuture getChannelFuture() {
    	if(channelFuture != null) {
    		if(channelFuture.isSuccess()) {
    			if(channelFuture.channel().isActive()) {
    				return channelFuture;
    			}else {
    				channelFuture.channel().close();
    				channelFuture = this.connect();
    			}
            }
    	}else {
    		channelFuture = this.connect();
    	}
    	return null;
    }
    
    public void asyncTransport(Channel channel, String allowOrigin, MessageRequest request, long timeout) throws Exception {
    	ChannelFuture channelFuture = getChannelFuture();
    	if (channelFuture != null) {
            try {
            	logger.info("asyncTransport request = {}", request.toString());
                CallbackPool.put(request.getRequestid(), channel, allowOrigin, timeout);
                channelFuture.channel().writeAndFlush(request);
            } catch (Exception e) {
            	throw new XException(ExceptionType.connection, "Failed to transport", e);
            }
        } else {
            throw new XException(ExceptionType.internal, "Socket channel is not well established");
        }
    }
    
    public void tryToReconnect() {
    	if(channelFuture != null) {
    		if(channelFuture.isSuccess()) {
    			channelFuture.channel().close();
				channelFuture = this.connect();
            }
    	}
    }

    public void shutdown() {
        if (eventLoopGroup != null) {
        	eventLoopGroup.shutdownGracefully();
            channelFuture = null;
        }
    }
    
    public String getAddress() {
        return serverIP + ":" + serverPort;
    }
    
    public boolean isClosed() {
    	return channelFuture==null || !channelFuture.isSuccess() || !channelFuture.channel().isActive() || this.eventLoopGroup.isShutdown();
    }

}
