package com.cheercent.xnetty.httpgateway.base;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

/*
 * @copyright (c) xhigher 2015 
 * @author xhigher    2015-3-26 
 */
public final class XServer {

	private static Logger logger = LoggerFactory.getLogger(XServer.class);
	
	private final String defaultHost = "0.0.0.0";
	private final int defaultPort;
	
	protected EventLoopGroup bossGroup = null;
	protected EventLoopGroup workerGroup = null;
	protected ServerBootstrap bootstrap = null;
	private DefaultEventExecutorGroup executorGroup = null;
	
	private ServiceDiscovery serviceDiscovery = null;
	
	public XServer(Properties properties) {
		serviceDiscovery = new ServiceDiscovery(properties.getProperty("zookeeper.server.list").trim(),
				properties.getProperty("service.product").trim(),
				properties.getProperty("service.business").trim());
		
		XClientManager.init(serviceDiscovery);
		
		defaultPort = Integer.parseInt(properties.getProperty("service.server.port").trim());

	}
	
	public void start() {
		executorGroup = new DefaultEventExecutorGroup(16, new DefaultThreadFactory("bizExecutorGroup"));
		bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(8, new DefaultThreadFactory("workerExecutorGroup"));
		try {
			bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup);
			bootstrap.channel(NioServerSocketChannel.class);
			bootstrap.option(ChannelOption.SO_BACKLOG, 100);
			bootstrap.handler(new LoggingHandler(LogLevel.INFO));
			bootstrap.childHandler(new XChannelInitializer(executorGroup));
			ChannelFuture future = bootstrap.bind(defaultHost,defaultPort).sync();
			future.channel().closeFuture().sync();
			
		}catch (Exception e){
			logger.error("XServer.start.Exception",e);
		} finally {
			stop();
		}
	}
	
	public void stop() {
		XClientManager.stop();
		
		if(bossGroup!=null){
			bossGroup.shutdownGracefully();
		}
		if(workerGroup != null){
			workerGroup.shutdownGracefully();
		}
		if(executorGroup != null){
			executorGroup.shutdownGracefully();
		}
	}
	
	public class XChannelInitializer extends ChannelInitializer<SocketChannel> {

		private final DefaultEventExecutorGroup executorGroup;
	
		public XChannelInitializer(final DefaultEventExecutorGroup executorGroup) {
			this.executorGroup = executorGroup;
		}

		@Override
		public void initChannel(SocketChannel ch) {
			ChannelPipeline pipeline = ch.pipeline();
	/*		pipeline.addLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS));
			pipeline.addLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS));*/
			pipeline.addLast(new HttpRequestDecoder());
			pipeline.addLast(new HttpResponseEncoder());
			pipeline.addLast(new HttpObjectAggregator(1048576));
			pipeline.addLast(new ChunkedWriteHandler());
			pipeline.addLast(this.executorGroup, "", new XHandler());
			
		}
	}

}
