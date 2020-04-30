package com.cheercent.xnetty.httpgateway.base;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.cheercent.xnetty.httpgateway.base.ServiceConfig.LogicRule;
import com.cheercent.xnetty.httpgateway.rpc.XClient;
import com.cheercent.xnetty.httpgateway.util.CommonUtils;

public class ServiceManager {
    private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);
    
    private volatile static ServiceManager serviceManager;
    private final ServiceConfig serviceConfig;
    
    private ConcurrentHashMap<String, XClient> serviceConnectedClients = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, AtomicInteger> serviceRoundRobinMap = new ConcurrentHashMap<>();
    
    private ServiceManager() {
    	serviceConfig = new ServiceConfig();
    }

    public static ServiceManager getInstance() {
        if (serviceManager == null) {
            synchronized (ServiceManager.class) {
                if (serviceManager == null) {
                    serviceManager = new ServiceManager();
                }
            }
        }
        return serviceManager;
    }

    public LogicRule getLogicRule(String module, String action, int version) {
        return this.serviceConfig.getLogicRule(module, action, version);
    }

    public void updateConnectedServer(List<String> allServerConfig) {
        if (allServerConfig != null) {
            if (allServerConfig.size() > 0) {
                //update local serverNodes cache
                serviceConfig.clearTempConfig();
                String configString = null;
                String configMd5 = null;
                JSONObject configInfo = null;
                for (int i = 0; i < allServerConfig.size(); ++i) {
                    configString = allServerConfig.get(i);
                    configInfo = JSONObject.parseObject(configString);
                    if (configInfo != null) {
                        configMd5 = CommonUtils.md5(configString);
                        serviceConfig.loadTempConfig(configInfo.getString("host") + ":" + configInfo.getIntValue("port"), configInfo, configMd5);
                    }
                }
                // Add new server node
                for (String serverid : serviceConfig.getNewServerIdList()) {
                    if (!serviceConnectedClients.containsKey(serverid)) {
                        logger.info("Connect new server node " + serverid);
                        connectServerNode(serverid);
                    } else {
                    	XClient client = serviceConnectedClients.get(serverid);
                        if (client.isClosed()) {
                        	connectServerNode(serverid);
                        }
                        if (serviceConfig.isDiffConfig(serverid)) {
                            updateServerNode(serverid);
                        }
                    }
                }

                // Close and remove invalid server nodes
                for (String serverid : serviceConnectedClients.keySet()) {
                    if (!serviceConfig.hasNewServerIdList(serverid)) {
                        logger.warn("Remove invalid server node " + serverid);
                        XClient client = serviceConnectedClients.get(serverid);
                        if (client != null) {
                            client.shutdown();
                        }
                        serviceConnectedClients.remove(serverid);
                    }
                }

                serviceConfig.clearTempConfig();
            } else { // No available server node ( All server nodes are down )
                logger.error("No available server node. All server nodes are down !!!");
                for (String serverid : serviceConnectedClients.keySet()) {
                	XClient client = serviceConnectedClients.get(serverid);
                    if (client != null) {
                        client.shutdown();
                    }
                }
                serviceConnectedClients.clear();
            }
        }
    }

    private void connectServerNode(String serverid) {
        String[] ip_port = serverid.split(":");
        XClient client = new XClient(ip_port[0], Integer.parseInt(ip_port[1]));
        serviceConnectedClients.put(serverid, client);
        this.serviceConfig.updateAfterConnected(serverid, client);
    }

    private void updateServerNode(String serverid) {
        this.serviceConfig.updateAfterConnected(serverid, serviceConnectedClients.get(serverid));
    }

    private int getClientRoundRobin(String serviceName) {
    	if(this.serviceRoundRobinMap.containsKey(serviceName)) {
    		return this.serviceRoundRobinMap.get(serviceName).getAndAdd(1);
    	}
    	return 0;
    }

    public XClient chooseClient(String module, String action, int version) {
    	String serviceName = this.serviceConfig.getServiceName(module, action, version);
        CopyOnWriteArrayList<XClient> clients = this.serviceConfig.cloneClientList(serviceName);
        if (clients != null && clients.size() > 0) {
            int size = clients.size();
            int closedNum = 0;
            XClient client = null;
            for (int index = 0, i = 0; i < size; i++) {
                index = (this.getClientRoundRobin(serviceName) + size) % size;
                client = clients.get(index);
                logger.info("client: address=" + client.getAddress());
                if (client.isClosed()) {
                    closedNum++;
                } else {
                	return client;
                }
            }
            if (closedNum > 0) {
                notifyClearClosedClient();
            }
        }
        return null;
    }

    private void notifyClearClosedClient() {
        this.serviceConfig.clearClosedClient();
    }

    public void stop() {
        this.serviceConfig.clear();
        for (XClient client : serviceConnectedClients.values()) {
            client.shutdown();
        }
    }
}
