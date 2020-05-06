package com.cheercent.xnetty.httpgateway.rpc;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class MessageFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(MessageFactory.class);
	
	private static final String REQUESTID_HEARTBEAT = "0";
	
	private static final int ERRCODE_OK = 0;
	
	public static boolean isHeartBeatMessage(String requestid) {
		return REQUESTID_HEARTBEAT.equals(requestid);
	}
	
	public static boolean isSuccessful(MessageResponse response) {
		return response.getErrcode() == ERRCODE_OK;
	}
	
	public static boolean isSuccessful(MessageRequest request) {
		String module = request.getModule();
		String action = request.getAction();
		if(module == null || action==null || module.isEmpty() || action.isEmpty()) {
			return false;
		}
		return true;
	}
	
	public static MessageRequest newHeartBeatMessage() {
		MessageRequest request = new MessageRequest(REQUESTID_HEARTBEAT);
		return request;
	}
	
	public static MessageRequest toMessageRequest(JSONObject data) {
		MessageRequest request = null;
		try {
			if(data != null) {
				request = data.toJavaObject(MessageRequest.class);
			}
		}catch(Exception e) {
			logger.error("toMessageRequest: data = " + data.toString(), e);
		}
		return request;
	}
	
	public static MessageResponse toMessageResponse(JSONObject data) {
		MessageResponse response = null;
		try {
			if(data != null) {
				response = data.toJavaObject(MessageResponse.class);
			}
		}catch(Exception e) {
			logger.error("toMessageResponse: data = {}" + data.toString(), e);
		}
		return response;
	}
	
	public static MessageRequest newMessageRequest(String requestid) {
		return new MessageRequest(requestid);
	}
	
	public static MessageResponse newMessageResponse(String requestid) {
		return new MessageResponse(requestid);
	}
	
	public static class MessageRequest extends Message {

		private static final long serialVersionUID = 8154173247682962410L;

	    private String module;
	    private String action;
	    private Integer version;
	    private JSONObject parameters;
	    
	    public MessageRequest() {
	    	super();
	    }
	    
	    public MessageRequest(String requestid) {
	    	super(requestid);
	    }
	    
		public String getModule() {
			return module;
		}
		
		public void setModule(String module) {
			this.module = module;
		}
		public String getAction() {
			return action;
		}
		
		public void setAction(String action) {
			this.action = action;
		}
		
		public Integer getVersion() {
			return version;
		}
		
		public void setVersion(Integer version) {
			this.version = version;
		}
		
		public JSONObject getParameters() {
			return parameters;
		}
		
		public void setParameters(JSONObject parameters) {
			this.parameters = parameters;
		}
		
		@Override
		protected void handleNullFields() {
			if(version == null) {
				version = 1;
			}
			if(parameters == null) {
				parameters = new JSONObject();
			}
		}
	}
	
	public static class MessageResponse extends Message {

		private static final long serialVersionUID = 7670819255727639811L;
		
		private int errcode;
	    private String data;
	    
	    public MessageResponse() {
	    	super();
	    }
	    
	    public MessageResponse(String requestid) {
	    	super(requestid);
	    }
	    
		public int getErrcode() {
			return errcode;
		}
		
		public void setErrcode(int errcode) {
			this.errcode = errcode;
		}
		
		public String getData() {
			return data;
		}
		
		public void setData(String data) {
			this.data = data;
		}
		
		@Override
		protected void handleNullFields() {
			if(data == null) {
				data = "{}";
			}
		}

	}
	
	public static abstract class Message implements Serializable {
		
		private static final long serialVersionUID = 5413649977189007312L;
		
		private String requestid;
		
	    public Message() {

	    }
		
	    public Message(String requestid) {
	    	this.requestid = requestid;
	    }
	    
		public String getRequestid() {
			return requestid;
		}
		public void setRequestid(String requestid) {
			this.requestid = requestid;
		}
		
		protected abstract void handleNullFields();
		
		public JSONObject toJSONObject() {
			this.handleNullFields();
			return (JSONObject) JSON.toJSON(this);
		}

		@Override
		public String toString(){
	        return JSON.toJSONString(this);
		}
	}

}
