package com.cheercent.xnetty.httpgateway.base;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private static final int ZK_SESSION_TIMEOUT = 60000;
    
    private CountDownLatch latch = new CountDownLatch(1);

    private volatile List<String> dataList = new ArrayList<String>();

    private String registryAddress;
    private ZooKeeper zookeeper;
    
    private String product;
    private String business;

    public ServiceDiscovery(String registryAddress, String product, String business) {
        this.registryAddress = registryAddress;
        this.product = product;
        this.business = business;
        zookeeper = connectServer();
        if (zookeeper != null) {
        	checkRootNode(zookeeper);
            watchNode(zookeeper);
        }
    }

    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(registryAddress, ZK_SESSION_TIMEOUT, new Watcher() {
  
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        latch.countDown();
                    }
                }
            });
            latch.await();
        } catch (Exception e) {
        	logger.error("connectServer.Exception", e);
        }
        return zk;
    }
    
    private void checkRootNode(ZooKeeper zk){
        try {
        	String rootPath = "/" + this.product;
            Stat s = zk.exists(rootPath, false);
            if (s == null) {
                zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            rootPath = rootPath + "/" + this.business;
            s = zk.exists(rootPath, false);
            if (s == null) {
                zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
        	logger.error("checkRootNode.Exception", e);
        }
    }

    private void watchNode(final ZooKeeper zk) {
        try {
        	String rootPath = "/" + this.product + "/" + this.business;
            List<String> serverList = zk.getChildren(rootPath, new Watcher() {

                public void process(WatchedEvent event) {
                	EventType type = event.getType();
                    if (type == Event.EventType.NodeCreated || type == Event.EventType.NodeCreated || 
                    		type == Event.EventType.NodeDataChanged || type == Event.EventType.NodeChildrenChanged) {
                        watchNode(zk);
                    }
                }
            });
            List<String> dataList = new ArrayList<String>();
        	for (String server : serverList) {
                byte[] bytes = zk.getData(rootPath + "/" + server, false, null);
                dataList.add(new String(bytes));
        	}
            this.dataList = dataList;

            //logger.info("Service discovery triggered updating connected server node, node data: {}", dataList);
            updateConnectedServer();
        } catch (Exception e) {
        	logger.error("watchNode.Exception", e);
        }
    }

    private void updateConnectedServer(){
    	ServiceManager.getInstance().updateConnectedServer(this.dataList);
    }

    public void stop(){
        if(zookeeper!=null){
            try {
                zookeeper.close();
            } catch (Exception e) {
                logger.error("stop.Exception", e);
            }
        }
    }
}
