package com.iotracks.api.client;

import com.iotracks.api.handler.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Client to establish connection to ioFog API.
 *
 * @author ilaryionava
 */
public class IOFogAPIConnector {

    private static final Logger log = Logger.getLogger(IOFogAPIConnector.class.getName());

    protected Bootstrap bootstrap;
    protected EventLoopGroup workerGroup;
    private volatile Boolean connectionSuccess;
    private volatile Boolean operationComplete = false;
    private final Object lock = new Object();

    private Bootstrap init(){
        workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        return bootstrap;
    }

    /**
     * Creates a new IOFogAPIConnector for REST calls.
     * @param handler - instance of {@link IOContainerRESTAPIHandler}
     * @param ssl
     */
    public IOFogAPIConnector(IOContainerRESTAPIHandler handler, boolean ssl){
        bootstrap = init();
        bootstrap.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel channel){
                addDefaultHandlers(ssl, channel);
                channel.pipeline().addLast(handler);
            }
        });
    }

    /**
     * Creates a new IOFogAPIConnector for WebSocket transmissions.
     * @param handler - instance of {@link IOContainerWSAPIHandler}
     * @param ssl - indicates if connection should be established through secured protocol
     */
    public IOFogAPIConnector(IOContainerWSAPIHandler handler, boolean ssl){
        bootstrap = init();
        bootstrap.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel channel){
                addDefaultHandlers(ssl, channel);
                channel.pipeline().addLast(handler);
            }
        });
    }

    /**
     * Returns a channel bound to the specified server
     *
     * @param server - the server name or ip address
     * @param port - the listening port
     *
     * @return a channel bound to the specified server
     */
    public Channel initConnection(String server, int port) throws ConnectException {
        InetSocketAddress socketAddress = new InetSocketAddress(server, port);
        try {
            final ChannelFuture channelFuture = bootstrap.connect(socketAddress);
            channelFuture.addListener(new GenericFutureListener<Future<Object>>() {
                public void operationComplete(Future<Object> future){
                    synchronized (lock) {
                        if (!channelFuture.isSuccess()) {
                            connectionSuccess = false;
                        } else {
                            connectionSuccess = true;
                        }
                        operationComplete = true;
                        lock.notify();
                    }
                }
            });
            synchronized (lock) {
                while(!operationComplete) {
                    lock.wait();
                }
                if (connectionSuccess) {
                    return channelFuture.sync().channel();
                } else {
                    throw new ConnectException("Error connecting to ioFog via WebSocket.");
                }
            }
        } catch (InterruptedException e) {
            log.warning("Error connection to specified address : " + socketAddress.toString());
            return null;
        }
    }

    private void addDefaultHandlers(boolean ssl, Channel channel) {
        if(ssl) {
           try {
               /* SelfSignedCertificate ssc = new SelfSignedCertificate();
                SslContext sslCtx =  SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();*/
                SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                channel.pipeline().addLast(sslCtx.newHandler(channel.alloc()));
           } catch (SSLException e) {
               log.warning("Error building SSL context.");
           }
        }
        channel.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(Integer.MAX_VALUE));
    }

    /**
     * Shuts down connection.
     */
    public void destroyConnection(){
        workerGroup.shutdownGracefully();
    }
}
