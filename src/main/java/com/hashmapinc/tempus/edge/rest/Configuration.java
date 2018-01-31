package com.hashmapinc.tempus.edge.rest;



import javax.json.JsonObject;


/**
 * Pojo to store configuration
 *
 * @author Prateek
 */
public class Configuration  {

    private long timestamp ;
    private JsonObject config;

    public long getUpdateTimestamp() {
        return timestamp;
    }

    public void setUpdateTimestamp(long updateTimestamp) {
        this.timestamp = updateTimestamp;
    }

    public JsonObject getConfig() {
        return config;
    }

    public void setConfig(JsonObject config) {
        this.config = config;
    }


    public Configuration( JsonObject config,long timestamp) {
        this.timestamp = timestamp;
        this.config = config;
    }
}
