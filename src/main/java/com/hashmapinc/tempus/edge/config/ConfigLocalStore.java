package com.hashmapinc.tempus.edge.config;

import com.hashmapinc.tempus.edge.rest.Configuration;

import java.util.logging.Logger;

/**
 *
 * Local store most recent configuration
 *
 * @author Prateek
 *
 */

public class ConfigLocalStore {
    private static final Logger log = Logger.getLogger(ConfigLocalStore.class.getName());

    private Configuration recentConfig;
    private static ConfigLocalStore configLocalStore;

    private ConfigLocalStore()
    {

    }
    public static synchronized ConfigLocalStore getInstance()
    {
        if(configLocalStore==null)
        {
            configLocalStore= new ConfigLocalStore();
        }
        return configLocalStore;
    }

    /**
     * Only update configuration if configuration is latest
     * @param config
     * @return true: if updated else false
     */
    public Boolean addRecentConfig(Configuration config)
    {
        if(recentConfig==null)
        {
            log.info("Setting Configuration for first time");
            recentConfig=config;
            return true;
        }
        else if(config.getUpdateTimestamp()>(recentConfig.getUpdateTimestamp()))
        {
            log.info("Setting Configuration to latest from server");

            recentConfig=config;
            return true;
        }
        log.info("Configuration are up to date");
        return false;
    }
}
