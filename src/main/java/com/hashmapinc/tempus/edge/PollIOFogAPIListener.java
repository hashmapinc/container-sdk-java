package com.hashmapinc.tempus.edge;

import com.iotracks.api.listener.IOFogAPIListener;

/**
 *
 * Extending IOFogAPIListener to handle changes in local configuration.
 *
 * @author Prateek
 *
 */
public interface PollIOFogAPIListener extends IOFogAPIListener {

    public void onNewLocalConfig(javax.json.JsonObject jsonObject);
}
