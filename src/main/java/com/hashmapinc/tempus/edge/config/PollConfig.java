package com.hashmapinc.tempus.edge.config;

import com.hashmapinc.tempus.edge.PollIOFogAPIListener;
import com.hashmapinc.tempus.edge.rest.Configuration;
import com.hashmapinc.tempus.edge.rest.RestClient;

import java.util.TimerTask;
import java.util.logging.Logger;


/**
 *
 *Poll task to keep on polling for change in local config
 *
 * @author Prateek
 *
 */
public class PollConfig extends TimerTask{
    private String deviceId;
    private String configURL;
    private PollIOFogAPIListener listener;
    private static final Logger log = Logger.getLogger(PollConfig.class.getName());

    public PollConfig(String deviceId, PollIOFogAPIListener listener, String configURL) {
        this.deviceId = deviceId;
        this.listener=listener;
        this.configURL=configURL;
    }

    @Override
    public void run() {
        log.info("Polling for changes in config");
        Configuration config = RestClient.getConfig(configURL);
        if(config!=null) {
            if (ConfigLocalStore.getInstance().addRecentConfig(config))
                listener.onNewLocalConfig(config.getConfig());
        }
    }
}
