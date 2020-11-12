package com.cheercent.xnetty.httpgateway.base;
 
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpgateway.base.ServiceConfig.ActionMethod;
import com.cheercent.xnetty.httpgateway.base.ServiceConfig.LogicRule;
import com.cheercent.xnetty.httpgateway.conf.DataKey;
import com.cheercent.xnetty.httpgateway.conf.PublicConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;



public class XHandler extends SimpleChannelInboundHandler<HttpObject> {

	private static Logger logger = LoggerFactory.getLogger(XHandler.class);
	
	private static final Pattern pathPattern = Pattern.compile("^\\/v(\\d{1,2})\\/([a-zA-Z]\\w+)\\/([a-zA-Z]\\w+)\\/?$");
	
	private String requestIP = null;
	private Integer requestVersion = 1;
	private String requestModule = null;
	private String requestAction = null;
	private ActionMethod requestMethod = null;
	private StringBuffer requestBody = null;
	private JSONObject requestParameters = null;
	private String allowOrigin = null;

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

    }
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
        	HttpRequest request = (HttpRequest) msg;
        	
        	if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }
        	
        	DecoderResult result = request.decoderResult();
        	
        	if (!result.isSuccess()) {
        		logger.error("channelRead:HttpResponseStatus.BAD_REQUEST");
        		sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
        	
        	HttpHeaders header = request.headers();
        	allowOrigin = header.get(HttpHeaderNames.ORIGIN);
        	if(allowOrigin != null){
    			Matcher matcher = PublicConfig.ALLOW_DOMAIN_PATTERN.matcher(allowOrigin);
    			if(!matcher.matches()){
    				allowOrigin = null;
    			}
    		}
        	
        	HttpMethod method = request.method();
        	logger.info("requestMethod=" + request.method());
            if (method == HttpMethod.OPTIONS) {
            	sendOptionsResult(ctx);
                return;
            }
            
        	if (method != HttpMethod.GET && method != HttpMethod.POST) {
        		logger.error("channelRead:HttpResponseStatus.METHOD_NOT_ALLOWED");
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }

        	QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        	String requestPath = decoder.path();
        	logger.info("requestPath="+requestPath);
            if (requestPath == null) {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
                return;
            }
            
            Matcher matcher = pathPattern.matcher(requestPath);
    		if(!matcher.matches()){
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
    		}
    		requestVersion = Integer.valueOf(matcher.group(1));
    		requestModule = matcher.group(2);
    		requestAction = matcher.group(3);
    		
    		this.cleanRequestParameters(decoder.parameters());
            
            if(!requestParameters.containsKey(DataKey.PEERID) && header.contains(PublicConfig.HEADER_PEERID)){
            	requestParameters.put(DataKey.PEERID, header.get(PublicConfig.HEADER_PEERID));
            }
            if(!requestParameters.containsKey(DataKey.SESSIONID) && header.contains(PublicConfig.HEADER_SESSIONID)){
            	requestParameters.put(DataKey.SESSIONID, header.get(PublicConfig.HEADER_SESSIONID));
            }
            if(header.contains(PublicConfig.HEADER_VERSION)){
            	requestParameters.put(DataKey.CLIENT_VERSION, header.get(PublicConfig.HEADER_VERSION));
            }
            if(header.contains(PublicConfig.HEADER_DEVICE)){
            	requestParameters.put(DataKey.DEVICE_INFO, header.get(PublicConfig.HEADER_DEVICE));
            }
            
            requestIP = header.get(PublicConfig.HEADER_REAL_IP);
            if (requestIP == null) {
            	InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
    			requestIP = insocket.getAddress().getHostAddress();
			}
			requestParameters.put(DataKey.CLIENT_IP, requestIP);
			requestParameters.put(DataKey.REQUEST_PATH, requestPath);
			
            if(method == HttpMethod.POST){
            	requestMethod = ActionMethod.POST;
                requestBody = new StringBuffer();
            }else if(method == HttpMethod.GET){
            	requestMethod = ActionMethod.GET;
            }else{
            	logger.error("channelRead:HttpResponseStatus.METHOD_NOT_ALLOWED");
            	sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }
        }
       
        if (msg instanceof HttpContent) {
        	HttpContent chunk = (HttpContent) msg;
        	
        	if(requestMethod == ActionMethod.POST){
	        	if(requestBody == null){
	        		sendError(ctx, HttpResponseStatus.FORBIDDEN);
	                return;
				}
	        	try{
					ByteBuf content = chunk.content();
					if (content.isReadable()) {
						requestBody.append(content.toString(CharsetUtil.UTF_8));
					}
				}catch (Exception e){
	        		logger.error("channelRead:chunk", e);
					sendError(ctx, HttpResponseStatus.BAD_REQUEST);
					return;
				}

        	}
 
			if (chunk instanceof LastHttpContent) {
				handleBusiness(ctx);
			}
        }else{
        	if(msg instanceof LastHttpContent){
        		handleBusiness(ctx);
        	}
        }
    }
    
    private void cleanRequestParameters(Map<String, List<String>> params){
    	if(requestParameters == null) {
    		requestParameters = new JSONObject();
    	}
    	
		List<String> tpv = null;
		String pv = null;
		JSONArray item = null;
		for(String pn : params.keySet()){
			tpv = params.get(pn);
			if(tpv.size() > 1){
				item = new JSONArray();
				item.addAll(tpv);
				pv = item.toJSONString();
			}else if(params.get(pn).size() == 1) {
				pv = params.get(pn).get(0).trim();
			}else{
				pv = "";
			}
			requestParameters.put(pn, pv);
		}
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	logger.error("exceptionCaught:", cause);
    	ctx.flush();
    	ctx.channel().close();
    }
    
    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
        ctx.write(response);
    }
    
    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset="+CharsetUtil.UTF_8.toString());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
 
    private void sendOptionsResult(ChannelHandlerContext ctx){
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		if (allowOrigin != null) {
			response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
			response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, PublicConfig.HEADER_PEERID+","+PublicConfig.HEADER_SESSIONID + "," + HttpHeaderNames.CONTENT_TYPE);
			response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST");
		}
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    private void sendResult(ChannelHandlerContext ctx, String responseContent){
    	//logger.info("responseContent:" + responseContent);
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
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

	public String checkRequiredParameters(JSONArray requires){
		if(requires != null && requires.size() > 0){
			String pn = null;
			List<String> lackedParams = new ArrayList<String>();
			for(int i=0,n=requires.size(); i<n; i++){
				pn = requires.getString(i);
				if(!requestParameters.containsKey(pn)){
					lackedParams.add(pn);
				}
			}
			if(lackedParams.size() > 0){
				return "PARAMETER_LACKED"+lackedParams.toString();
			}
		}
		return null;
	}
	
	private void handleBusiness(ChannelHandlerContext ctx){

		LogicRule logicRule = XClientManager.getLogicRule(this.requestModule, this.requestAction, this.requestVersion);
		if(logicRule == null){
			this.sendResult(ctx, XLogic.errorRequestResult());
			return;
		}
		JSONArray ipList = logicRule.getAllowIPList();
		boolean isForbidden = false;
		if(ipList.size() > 0){
			isForbidden = true;
			if(ipList.contains(requestIP)) {
				isForbidden = false;
			}
			if(isForbidden){
				logger.info("handleBusiness.IP_FORBIDDEN:"+requestIP);
				this.sendResult(ctx, XLogic.errorRequestResult());
				return;
			}
		}
		if(logicRule.getMethod() != requestMethod){
			this.sendResult(ctx, XLogic.errorMethodResult());
			return;
		}
		
		if(this.requestBody != null) {
			try {
				this.requestParameters.putAll(JSONObject.parseObject(this.requestBody.toString()));
				this.requestBody = null;
			} catch (Exception e) {
				this.sendResult(ctx, XLogic.errorParameterResult(this.requestBody.toString()));
			}
		}

		if(logicRule.getRequiredParameters().size() > 0){
			String errinfo = this.checkRequiredParameters(logicRule.getRequiredParameters());
			if(errinfo != null){
				this.sendResult(ctx, XLogic.errorParameterResult(errinfo));
				return;
			}
		}
		
		logger.info("requestParameters="+requestParameters.toString());
		if(logicRule.isRequiredPeerid()){
			String peerid = this.requestParameters.getString(DataKey.PEERID);
			if(!PublicConfig.checkPeerid(peerid)){
				this.sendResult(ctx, XLogic.errorValidationResult());
				return;
			}
		}

		try {
			XClientManager.callService(ctx.channel(), this.allowOrigin, this.requestModule, this.requestAction, this.requestVersion, requestParameters);
		}catch(Exception e){
			logger.error("handleBusiness.Exception:clientManager.callService: {}, {}, {}", this.requestModule, this.requestAction, this.requestVersion, e);
			this.sendResult(ctx, XLogic.errorRequestResult());
		}
	}
}
