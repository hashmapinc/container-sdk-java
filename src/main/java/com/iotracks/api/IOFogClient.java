package com.iotracks.api;

import com.iotracks.api.client.*;
import com.iotracks.api.listener.*;
import com.iotracks.utils.IOFogLocalAPIURL;
import com.iotracks.elements.IOMessage;
import com.iotracks.api.handler.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;
import io.netty.util.internal.StringUtil;

import javax.json.Json;
import javax.json.JsonObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * IOFogClient implements all methods to communicate with ioFog (via local API).
 *
 * @author ilaryionava
 */
public class IOFogClient {

    private static final Logger log = Logger.getLogger(IOFogClient.class.getName());

    private final String ID_PARAM_NAME = "id";
    private final String TIMEFRAME_START_PARAM_NAME = "timeframestart";
    private final String TIMEFRAME_END_PARAM_NAME = "timeframeend";
    private final String PUBLISHERS_PARAM_NAME = "publishers";

    private String server;
    private int port;
    private boolean ssl;
    private String elementID = "NOT_DEFINED";
    private IOContainerWSAPIHandler wsMessageHandler = null;
    private IOContainerWSAPIHandler wsControlHandler = null;
    private static IOWebSocketConnector wsMessageConnector = null;
    private static IOWebSocketConnector wsControlConnector = null;
    private static Thread wsMessageThread = null;
    private static Thread wsControlThread = null;
    private final int wsConnectAttemptsLimit = 5;
    public int wsReconnectMessageSocketAttempts = 0;
    public int wsReconnectControlSocketAttempts = 0;
    private final int wsConnectAttemptDelay = 1000;
    private Timer timer;

    /**
     * @param host - the server name or ip address (by default "router")
     * @param port - the listening port (bye default 54321)
     * @param containerId - container's ID that will be used for all requests
     */
    public IOFogClient(String host, int port, String containerId) {
        if (!StringUtil.isNullOrEmpty(host)) {
            this.server = host;
        } else {
            this.server = "iofog";
            if(!isHostReachable()){
                log.warning("Host: " + server + " - is not reachable. Changing to default value: 127.0.0.1.");
                this.server = "127.0.0.1";
            }
        }
        this.port = port != 0 ? port : 54321;
        this.ssl = System.getProperty("ssl") != null;
        String selfname = System.getProperty("SELFNAME");
        if(!StringUtil.isNullOrEmpty(containerId)){
            this.elementID = containerId;
        } else if(!StringUtil.isNullOrEmpty(selfname)) {
            this.elementID = selfname;
        }
        timer = new Timer();
    }

    /**
     * Method sends REST request to ioFog based on parameters.
     *
     * @param url - request url
     * @param content - json representation of request's content
     * @param listener - listener for REST communication with ioFog
     *
     */
    private void sendRequest(IOFogLocalAPIURL url, JsonObject content, IOFogAPIListener listener){
        IOContainerRESTAPIHandler handler = new IOContainerRESTAPIHandler(listener);
        IOFogAPIConnector localAPIConnector = new IOFogAPIConnector(handler, ssl);
        Channel channel;
        try {
            channel = localAPIConnector.initConnection(server, port);
            if(channel != null) {
                channel.writeAndFlush(getRequest(url, HttpMethod.POST, content.toString().getBytes()));
                channel.closeFuture().sync();
            }
        } catch (ConnectException e) {
            log.warning("Connection exception. Probably ioFog is not reachable.");
        } catch (InterruptedException e) {
            log.warning("Error closing and synchronizing request channel.");
        }
    }

    /**
     * Method creates request with necessary headers.
     *
     * @param url - request url
     * @param httpMethod - HTTP method type for request
     *
     * @return HttpRequest
     */
    private FullHttpRequest getRequest(IOFogLocalAPIURL url, HttpMethod httpMethod, byte[] content){
        ByteBuf contentBuf = Unpooled.copiedBuffer(content);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getURI(url, false).getRawPath(), contentBuf);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, contentBuf.readableBytes());
        request.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
        request.headers().set(HttpHeaders.Names.HOST, server);
        return request;
    }

    /**
     * Method opens a WebSocket connection to ioFog in a separate thread.
     *
     * @param wsType - WebSocket type for connection
     * @param listener - listener for communication with ioFog
     *
     */
    private void openWebSocketConnection(IOFogLocalAPIURL wsType, IOFogAPIListener listener){
        IOContainerWSAPIHandler handler = new IOContainerWSAPIHandler(listener, getURI(wsType, true), elementID, wsType, this);
        IOWebSocketConnector wsConnector = new IOWebSocketConnector(handler, ssl, server, port);
        Thread thread = new Thread(wsConnector);
        thread.start();
        if (wsType == IOFogLocalAPIURL.GET_MSG_WEB_SOCKET_LOCAL_API) {
            wsMessageHandler = handler;
            wsMessageConnector = wsConnector;
            wsMessageThread = thread;
        } else if (wsType == IOFogLocalAPIURL.GET_CONTROL_WEB_SOCKET_LOCAL_API) {
            wsControlHandler = handler;
            wsControlConnector = wsConnector;
            wsControlThread = thread;
        } else {
            log.warning("No WS type defined. No WS opened.");
        }
        synchronized (wsConnector.lock) {
            try {
                wsConnector.lock.wait();
                if (wsConnector.isCaughtException()) {
                    reconnect(wsType, listener);
                }
            } catch (InterruptedException e) {
                log.warning("Error while opening WebSocket connection: " + e.getMessage());
            }
        }
    }

    /**
     * Method sends IOMessage to ioFog in case Message WebSocket connection is open.
     *
     * @param message - IOMessage to send
     *
     */
    public void sendMessageToWebSocket(IOMessage message){
        if(message != null) {
            message.setPublisher(elementID);
            if(wsMessageHandler != null) {
                wsMessageHandler.sendMessage(elementID, message);
            } else {
                log.warning("Message can be sent to ioFog only if MessageWebSocket connection is established.");
            }
        }
    }

    /**
     * Method sends request for current Container's configurations.
     *
     * @param listener - listener for communication with ioFog
     *
     */
    public void fetchContainerConfig(IOFogAPIListener listener){
        sendRequest(IOFogLocalAPIURL.GET_CONFIG_REST_LOCAL_API, Json.createObjectBuilder().add(ID_PARAM_NAME, elementID).build(), listener);
    }

    /**
     * Method sends request for all Container's unread messages.
     *
     * @param listener - listener for communication with ioFog
     *
     */
    public void fetchNextMessage(IOFogAPIListener listener){
        sendRequest(IOFogLocalAPIURL.GET_NEXT_MSG_REST_LOCAL_API, Json.createObjectBuilder().add(ID_PARAM_NAME, elementID).build(), listener);
    }

    /**
     * Method sends request to post Container's new IOMessage to the system.
     *
     * @param message - new IOMessage
     * @param listener - listener for communication with ioFog
     *
     */
    public void pushNewMessage(IOMessage message , IOFogAPIListener listener){
        if(message != null) {
            message.setPublisher(elementID);
            sendRequest(IOFogLocalAPIURL.POST_MSG_REST_LOCAL_API, message.getJson(true), listener);
        }
    }

    /**
     * Method sends request for all Container's messages for specified publishers and period.
     *
     * @param startDate - start date of period
     * @param endDate - end date of period
     * @param publishers - set of publisher's IDs
     * @param listener - listener for communication with ioFog
     *
     */
    public void fetchMessagesByQuery(Date startDate, Date endDate,
                                                Set<String> publishers, IOFogAPIListener listener){
        JsonObject json = Json.createObjectBuilder().add(ID_PARAM_NAME, elementID)
                .add(TIMEFRAME_START_PARAM_NAME, startDate.getTime())
                .add(TIMEFRAME_END_PARAM_NAME, endDate.getTime())
                .add(PUBLISHERS_PARAM_NAME, publishers.toString())
                .build();
        sendRequest(IOFogLocalAPIURL.GET_MSGS_QUERY_REST_LOCAL_API, json, listener);
    }

    /**
     * Method opens a Control WebSocket connection to ioFog in a separate thread.
     *
     * @param listener - listener for communication with ioFog
     *
     */
    public void openControlWebSocket(IOFogAPIListener listener){
        openWebSocketConnection(IOFogLocalAPIURL.GET_CONTROL_WEB_SOCKET_LOCAL_API, listener);
    }

    /**
     * Method opens a Message WebSocket connection to ioFog in a separate thread.
     *
     * @param listener - listener for communication with ioFog
     *
     */
    public void openMessageWebSocket(IOFogAPIListener listener){
        openWebSocketConnection(IOFogLocalAPIURL.GET_MSG_WEB_SOCKET_LOCAL_API, listener);
    }

    /**
     * Method constructs a URL for request.
     *
     * @param url - url for request
     * @param isWS - weather url os for WS request<
     *
     * @return URI
     */
    private URI getURI(IOFogLocalAPIURL url, boolean isWS){
        StringBuilder urlBuilder = new StringBuilder();
        String protocol = isWS ? "ws" : "http";
        urlBuilder.append(protocol);
        if (ssl) {
            urlBuilder.append("s");
        }
        urlBuilder.append("://").append(server).append(":").append(port).append(url.getURL());
        if(isWS) { urlBuilder.append(elementID); }
        try {
            return new URI(urlBuilder.toString());
        } catch (URISyntaxException e){
            log.warning("Error constructing URL for request.");
            return null;
        }
    }

    /**
     * Method checks if the host of IOFogClient is reachable.
     *
     * @return boolean
     */
    private boolean isHostReachable(){
        try {
            return InetAddress.getByName(server).isReachable(1000);
        } catch (UnknownHostException e){
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public void reconnect(IOFogLocalAPIURL wsType, IOFogAPIListener listener) {
        int delay = 0;
        TimerTask wsTask = new WSTimerTask(wsType, listener);
        BigInteger constPow = new BigInteger("2");
        try{
            if (wsType == IOFogLocalAPIURL.GET_MSG_WEB_SOCKET_LOCAL_API) {
                wsMessageConnector.terminate();
                wsMessageThread.join();
                if(wsReconnectMessageSocketAttempts < wsConnectAttemptsLimit) {
                    delay = wsConnectAttemptDelay * constPow.pow(wsReconnectMessageSocketAttempts).intValue();
                }
                wsReconnectMessageSocketAttempts++;
            } else if (wsType == IOFogLocalAPIURL.GET_CONTROL_WEB_SOCKET_LOCAL_API) {
                wsControlConnector.terminate();
                wsControlThread.join();
                if(wsReconnectControlSocketAttempts < wsConnectAttemptsLimit) {
                    delay = wsConnectAttemptDelay * constPow.pow(wsReconnectControlSocketAttempts).intValue();
                }
                wsReconnectControlSocketAttempts++;
            } else {
                log.warning("No WS type defined. No WS opened.");
            }
        } catch (InterruptedException e) {
            log.warning("Exception when closing threads for WS connection.");
        }
        if(delay == 0) {
            delay = wsConnectAttemptDelay * constPow.pow(wsConnectAttemptsLimit - 1).intValue();
        }
        timer.schedule(wsTask, delay);
    }


    class WSTimerTask extends TimerTask {

        private IOFogLocalAPIURL wsType;
        private IOFogAPIListener listener;

        public WSTimerTask(IOFogLocalAPIURL wsType, IOFogAPIListener listener){
            this.wsType = wsType;
            this.listener = listener;
        }

        @Override
        public void run() {
            openWebSocketConnection(wsType, listener);
        }
    }
}
