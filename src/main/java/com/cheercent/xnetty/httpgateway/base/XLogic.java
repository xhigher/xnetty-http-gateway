package com.cheercent.xnetty.httpgateway.base;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;



/*
 * @copyright (c) xhigher 2015 
 * @author xhigher    2015-3-26 
 */
public abstract class XLogic {

	public static final String REQUEST_KEY_CALLBACK = "callback";
	public static final String RESULT_KEY_ERRCODE = "errcode";
	public static final String RESULT_KEY_ERRINFO = "errinfo";
	public static final String RESULT_KEY_DATA = "data";
	
	public interface ErrorCode {
		
		public static final int OK = 0;
		public static final int NOK = 1;
		
		public static final int INTERNAL_ERROR = 4000;
		public static final int REQEUST_ERROR = 4001;
		public static final int METHOD_ERROR = 4002;
		public static final int PARAMETER_ERROR = 4003;
		public static final int VALIDATION_ERROR  = 4004;
		public static final int SERVICE_BUSY = 4005;
	
	}
	
	public static String staticOutputResult(int code,String info, Object obj){
		if(info == null){
			info = "";
		}
		if(obj == null){
			obj = new JSONObject();
		}
		JSONObject result = new JSONObject();
		result.put(RESULT_KEY_ERRCODE, code);
		result.put(RESULT_KEY_ERRINFO, info);
		result.put(RESULT_KEY_DATA, obj);
		return JSONObject.toJSONString(result, SerializerFeature.DisableCircularReferenceDetect);
	}
	
	public static String errorResult(){
		return staticOutputResult(ErrorCode.NOK, null, null);
	}

	public static String errorInternalResult(){
		return staticOutputResult(ErrorCode.INTERNAL_ERROR, "INTERNAL_ERROR", null);
	}
	
	public static String errorRequestResult(){
		return staticOutputResult(ErrorCode.REQEUST_ERROR, "REQUEST_ERROR", null);
	}
	
	public static String errorMethodResult(){
		return staticOutputResult(ErrorCode.METHOD_ERROR, "METHOD_ERROR", null);
	}
	
	public static String errorParameterResult(String info){
		return staticOutputResult(ErrorCode.PARAMETER_ERROR, info, null);
	}

	public static String errorValidationResult(){
		return staticOutputResult(ErrorCode.VALIDATION_ERROR, "VALIDATION_ERROR", null);
	}

}
