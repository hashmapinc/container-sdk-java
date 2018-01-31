package com.hashmapinc.tempus.edge;

import com.hashmapinc.tempus.edge.config.ConfigLocalStore;
import com.hashmapinc.tempus.edge.config.PollConfig;
import com.hashmapinc.tempus.edge.rest.Configuration;
import com.hashmapinc.tempus.edge.rest.RestClient;
import com.iotracks.api.IOFogClient;

import javax.json.stream.JsonParsingException;
import java.util.Timer;
import java.util.logging.Logger;

/**
 *
 * Extending IOFogClient to get local configuration from Rest Service.
 *
 * @author Prateek
 *
 */
public class PollIOFogClient extends IOFogClient {

    String deviceId="";
    String configURL="";
    private static final Logger log = Logger.getLogger(IOFogClient.class.getName());


    public PollIOFogClient(String host, int port, String containerId, String deviceId, String configPollURL) {
        super(host, port, containerId);
        this.deviceId=deviceId;
        this.configURL=configPollURL;
    }

    public void fetchLocalContainerConfig(PollIOFogAPIListener listener,long pollInterval)
    {
        log.info("Fetching Config from local config url");
        Configuration config = RestClient.getConfig(configURL);
        Timer timer = new Timer();
        timer.schedule(new PollConfig(deviceId, listener,configURL), pollInterval,pollInterval);
        if(config==null)
            return;
        if(ConfigLocalStore.getInstance().addRecentConfig(config))
            listener.onNewLocalConfig(config.getConfig());
    }

    public static void main(String args[])
    {


    }
}
