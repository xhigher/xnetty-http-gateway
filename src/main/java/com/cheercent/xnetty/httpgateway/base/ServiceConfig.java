package com.cheercent.xnetty.httpgateway.base;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpgateway.rpc.XClient;

public class ServiceConfig {

	private static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

	public static final Pattern ALLOW_DOMAIN_PATTERN = Pattern.compile("^http(s?):\\/\\/(([\\.\\d\\w]+\\.zdd\\.zhongshare\\.com)|(localhost))(.*?)$");

	private ConcurrentHashMap<String, LogicRule> ruleConfig = new ConcurrentHashMap<String, LogicRule>();
	
	private ConcurrentHashMap<String, CopyOnWriteArrayList<XClient>> routerConfig = new ConcurrentHashMap<String, CopyOnWriteArrayList<XClient>>();
	
	private ConcurrentHashMap<String, String> md5Config = new ConcurrentHashMap<String, String>();
	private ConcurrentHashMap<String, String> tempMd5Config = new ConcurrentHashMap<String, String>();
	
	private ConcurrentHashMap<String, JSONObject> tempConfig = new ConcurrentHashMap<String, JSONObject>();
	
	private static boolean clearClosedClientLocked = false;
	
	public enum ActionMethod {
		GET, POST
	}
	
	public class LogicRule {
		
		private final String version;
		
		private final ActionMethod method;
	    
		private final JSONArray requiredParameters;
	    
		private final boolean requiredPeerid;
	    
		private final JSONArray allowIPList;
		
		
		public LogicRule(JSONObject data){
			this.version = data.getString("version");
			this.method = ActionMethod.valueOf(data.getString("method"));
			this.requiredParameters = data.getJSONArray("requiredParameters");
			this.requiredPeerid = data.getBoolean("requiredPeerid");
			this.allowIPList = data.getJSONArray("allow");
		}
		
		public JSONArray getAllowIPList(){
			return this.allowIPList;
		}
		
		public String getVersion(){
			return this.version;
		}
		
		public ActionMethod getMethod(){
			return this.method;
		}
		
		public JSONArray getRequiredParameters(){
			return this.requiredParameters;
		}
		
		public boolean isRequiredPeerid(){
			return this.requiredPeerid;
		}
	}
	
	public void clearTempConfig(){
		this.tempConfig.clear();
		this.tempMd5Config.clear();
	}
	
	public void clear(){
		this.tempConfig.clear();
		this.ruleConfig.clear();
		this.routerConfig.clear();
		
		this.md5Config.clear();
		this.tempMd5Config.clear();
	}
	
	public void loadTempConfig(String serverid, JSONObject config, String md5){
		tempConfig.put(serverid, config);
		tempMd5Config.put(serverid, md5);
	}
	
	public Set<String> getNewServerIdList(){
		return tempConfig.keySet();
	}
	
	public boolean hasNewServerIdList(String serverid){
		return tempConfig.containsKey(serverid);
	}
	
	public boolean isDiffConfig(String serverid){
		if(this.md5Config.containsKey(serverid) && 
				this.tempMd5Config.containsKey(serverid) &&
				this.md5Config.get(serverid).equals(this.tempMd5Config.get(serverid))){
			return false;
		}
		return true;
	}
	
	public void clearClosedClient(){
		if(clearClosedClientLocked){
			return;
		}
		clearClosedClientLocked = true;
		XClient client = null;
		CopyOnWriteArrayList<XClient> tempClients = new CopyOnWriteArrayList<XClient>();
		for (CopyOnWriteArrayList<XClient> clients : routerConfig.values()) {
			for(int i=0; i<clients.size(); i++){
				client = clients.get(i);
				if(!client.isClosed()){
					tempClients.add(client);
				}
			}
			clients.clear();
			clients.addAll(tempClients);
			tempClients.clear();
        }
		clearClosedClientLocked = false;
	}
	
	public String getServiceName(String module, String action, int version) {
		return module + "#" + action + "@" + version;
	}
	
	public CopyOnWriteArrayList<XClient> cloneClientList(String serviceName){
		if(this.routerConfig.containsKey(serviceName)){
			return (CopyOnWriteArrayList<XClient>)this.routerConfig.get(serviceName).clone();
		}
		return null;
	}
	
	public void updateAfterConnected(String serverid, XClient client){
		if(this.tempConfig.containsKey(serverid)){
			JSONObject config = this.tempConfig.get(serverid);
			JSONArray serviceList = config.getJSONArray("services");
			JSONObject serviceInfo = null;
			String serviceName = null;
			for(int i=0,n=serviceList.size(); i<n; i++){
				serviceInfo = serviceList.getJSONObject(i);
				serviceName = serviceInfo.getString("module")+"#"+serviceInfo.getString("action")+"@"+serviceInfo.getString("version");
				this.ruleConfig.put(serviceName, new LogicRule(serviceInfo));
				
				if(!this.routerConfig.containsKey(serviceName)){
					this.routerConfig.put(serviceName, new CopyOnWriteArrayList<XClient>());
				}
				logger.info("updateAfterConnected:serviceName="+serviceName+", client="+client);
				this.routerConfig.get(serviceName).add(client);
			}
			this.md5Config.put(serverid, this.tempMd5Config.get(serverid));
		}
	}
	
	public LogicRule getLogicRule(String module, String action, int version){
		return ruleConfig.get(module+"#"+action+"@"+version);
	}
}
