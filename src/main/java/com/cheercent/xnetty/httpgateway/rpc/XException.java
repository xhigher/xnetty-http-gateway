package com.cheercent.xnetty.httpgateway.rpc;

public class XException extends RuntimeException {

    private static final long serialVersionUID = 5196421433506179782L;

    private final ExceptionType type;
    
    public enum ExceptionType{
    	internal,
    	timeout,
    	connection,
    	interrupted,
    }
    
    public XException() {
        super();
    	this.type = ExceptionType.internal;
    }

    public XException(ExceptionType type, String message, Throwable cause) {
        super(message, cause);
    	this.type = type;
    }

    public XException(ExceptionType type, String message) {
        super(message);
    	this.type = type;
    }

    public XException(ExceptionType type, Throwable cause) {
        super(cause);
    	this.type = type;
    }
    
    public XException(ExceptionType type) {
        super();
    	this.type = type;
    }
    
    public boolean isInternalError() {
    	return this.type == ExceptionType.internal;
    }
    
    public boolean isTimeoutError() {
    	return this.type == ExceptionType.timeout;
    }

}
