package org.sipfoundry.sipxbridge.symmitron;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import org.apache.log4j.Logger;
import org.sipfoundry.sipxbridge.Gateway;

/**
 * Receiver Endpoint.
 * 
 * @author mranga
 * 
 */
public class SymReceiverEndpoint extends SymEndpoint {

    private static Logger logger = Logger.getLogger(SymReceiverEndpoint.class);
    
    private  InetSocketAddress socketAddress;

    public SymReceiverEndpoint(int port) throws IOException {
        super();

        // Our IP address is the address where we are listening.

        this.ipAddress = Gateway.getLocalAddress();
        
        // Our port where we listen for stuff.
        
        this.port = port;

        if ( logger.isDebugEnabled())
            logger.debug("Creating SymReceiverEndpoint " + ipAddress + " port " + port);

        // The datagram channel which is used to receive packets.
        this.datagramChannel = DatagramChannel.open();
        
        this.datagramChannel.socket().setReuseAddress(true);

        // Set up to be non blocking
        this.datagramChannel.configureBlocking(false);

        InetAddress inetAddress = Gateway.getLocalAddressByName();

        // Allocate the datagram channel on which we will listen.
        socketAddress = new InetSocketAddress(inetAddress, port);

        datagramChannel.socket().bind(socketAddress);

    }

   

}
