package com.cheercent.xnetty.httpgateway.conf;

import java.util.regex.Pattern;

public class PublicConfig {
	
	public static final String HEADER_PEERID = "X-HI-PEERID";
	public static final String HEADER_SESSIONID = "X-HI-SESSIONID";
	public static final String HEADER_DEVICE = "X-HI-DEVICE";
	public static final String HEADER_VERSION = "X-HI-VERSION";
	
	public static final String HEADER_REAL_IP = "X-REAL-IP";
	public static final String HEADER_PATH = "X-HI-PATH";
	
	public static final int PEERID_LENGTH = 20;
	
	public static final Pattern ALLOW_DOMAIN_PATTERN = Pattern.compile("^http(s?):\\/\\/([\\.\\d\\w]+\\.cheercent\\.com)(.*?)$");
	
	public static final String[] PARAMETER_INT_LIST = {
		""
	};
	
	public static boolean checkPeerid(String peerid) {
		try {
			if(peerid!=null && peerid.length() == 20){
				int rn = Integer.parseInt(peerid.substring(4, 5));
				int mn = Integer.parseInt(peerid.substring(13, 14));
				String ts36 = peerid.substring(5, 13);
				long ts = Long.valueOf(ts36, 36);
				if(ts % rn == mn) {
					return true;
				}
			}
		}catch(Exception e){	
		}
		return false;
	}
	
}
