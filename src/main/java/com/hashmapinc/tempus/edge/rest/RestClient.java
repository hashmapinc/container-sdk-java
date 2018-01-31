package com.hashmapinc.tempus.edge.rest;
import com.hashmapinc.tempus.edge.PollConstants;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Rest client to get local config
 *
 * @author Prateek
 */
public class RestClient {

    private static final Logger log = Logger.getLogger(RestClient.class.getName());

    public static Configuration getConfig(String configURL)
    {
        String output="";

        Configuration returnObject=null;

        try {
            URL url = new URL(configURL);
            System.out.println(url.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String temp;
            while ((temp = br.readLine()) != null) {
                output=output+temp;
            }
            conn.disconnect();
            JsonReader reader = Json.createReader(new StringReader(output));
            JsonObject jsonConfig = reader.readObject();
            if(jsonConfig.getJsonObject(PollConstants.CONFIG_PARAM)!=null && jsonConfig.getJsonNumber(PollConstants.TIMESTAMP_PARAM).longValue()>0)
              returnObject= new Configuration(jsonConfig.getJsonObject(PollConstants.CONFIG_PARAM),jsonConfig.getJsonNumber(PollConstants.TIMESTAMP_PARAM).longValue());
            else
                throw new Exception("Illegal format of rest call :"+output);
        }
        catch (Exception e)
        {
            log.warning("Rest call for config failed :"+e.getMessage());
        }

        return returnObject;
    }

}