package org.sipfoundry.sipxbridge;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import java.util.TimerTask;

import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;

import org.apache.log4j.Logger;

/**
 * Transmitter endpoint.
 * 
 * @author mranga
 * 
 */
public class SymTransmitterEndpoint extends SymEndpoint {

    private long lastPacketSentTime;

    private transient byte[] keepalivePayload = null;

    private ByteBuffer keepAliveBuffer = ByteBuffer.allocate(0);

    private  boolean remoteAddressAutoDiscovered = false;

    private String keepaliveMethod = "USE-EMPTY-PACKET";

    private  KeepaliveTimerTask keepaliveTimerTask;

    private static Logger logger = Logger
            .getLogger(SymTransmitterEndpoint.class);

    private boolean onHold = false;

    private  boolean earlyMediaStarted = false;

    private int maxSilence;

    /**
     * The keepalive timer task.
     * 
     */

    class KeepaliveTimerTask extends TimerTask {

        KeepaliveTimerTask() {

            logger
                    .debug("Starting early media sender TimerTask : remote endpoint = "
                            + getIpAddress() + "/ " + port);

        }

        public void run() {
            try {
               
                if (datagramChannel == null)
                    return;
                long now = System.currentTimeMillis();
                if (now - lastPacketSentTime < Gateway
                        .getMediaKeepaliveMilisec())
                    return;
                if (keepaliveMethod.equals("REPLAY-LAST-SENT-PACKET")
                        || keepaliveMethod.equals("USE-EMPTY-PACKET")) {
                    datagramChannel.send(keepAliveBuffer, socketAddress);
                }
            } catch (ClosedChannelException ex) {
                logger
                        .warn(
                                "Exitting early media thread due to closed channel",
                                ex);
                this.cancel();

            } catch (Exception ex) {
                logger
                        .fatal("Unexpected exception in sending early media ",
                                ex);
                this.cancel();
            }

        }

    }

    // /////////////////////////////////////////////////
    // Private methods.
    // /////////////////////////////////////////////////

    private void startKeepaliveTimer() {

        logger.debug("startEarlyMediaThread " + this.toString());
        if (earlyMediaStarted)
            return;
        this.earlyMediaStarted = true;
        this.keepaliveTimerTask = new KeepaliveTimerTask();
        Gateway.timer.schedule(this.keepaliveTimerTask, this.maxSilence,
                this.maxSilence);

    }

    // ///////////////////////////////////////////////
    // Public methods.
    // ///////////////////////////////////////////////
    public SymTransmitterEndpoint() {
        super();
    }

    /**
     * @return the earlyMediaStarted
     */
    public boolean isKeepaliveStarted() {
        return earlyMediaStarted;
    }

    public void send(ByteBuffer byteBuffer) throws IOException {

        if (logger.isDebugEnabled())
            logger.debug("Sending to " + this.socketAddress);
        this.lastPacketSentTime = System.currentTimeMillis();
        if (keepaliveMethod.equals("REPLAY-LAST-SENT-PACKET")) {
            this.keepAliveBuffer = byteBuffer;
        }
        this.datagramChannel.send(byteBuffer, this.socketAddress);

    }

    public void setMaxSilence(int maxSilence, String keepaliveMethod) {
        logger.debug("RtpEndpoint : setMaxSilence " + maxSilence);
        if (this.earlyMediaStarted) {
            logger.debug("early media started !");
            return;
        }
        this.maxSilence = maxSilence;
        this.keepaliveMethod = keepaliveMethod;
        if (maxSilence != 0 && !keepaliveMethod.equals("NONE"))
            this.startKeepaliveTimer();
    }

    public void setKeepalivePayload(byte[] keepAlivePacketData) {
        this.keepalivePayload = keepAlivePacketData;

    }

    public byte[] getKeepalivePayload() {
        return this.keepalivePayload;
    }

    public void stopKeepalive() {
        if (this.keepaliveTimerTask != null)
            this.keepaliveTimerTask.cancel();

    }

    
    @Override
    public void setSessionDescription(SessionDescription sessionDescription) {
        try {
            this.sessionDescription = sessionDescription;

            if (sessionDescription.getConnection() != null)
                this.ipAddress = sessionDescription.getConnection()
                        .getAddress();

            // TODO -- we need to sort through different media types here.
            MediaDescription mediaDescription = (MediaDescription) sessionDescription
                    .getMediaDescriptions(true).get(0);

            if (mediaDescription.getConnection() != null) {

                ipAddress = mediaDescription.getConnection().getAddress();

            }

            this.port = mediaDescription.getMedia().getMediaPort();

            if (logger.isDebugEnabled()) {
                logger.debug("isTransmitter = true : Setting ipAddress : "
                        + ipAddress);
                logger.debug("isTransmitter = true : Setting port " + port);
            }

            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            this.socketAddress = new InetSocketAddress(inetAddress, this.port);

            /*
             * We use the same datagram channel for sending and receiving.
             */
            this.datagramChannel = this.getSym().getReceiver()
                    .getDatagramChannel();
            if (this.datagramChannel == null) {
                logger.error("Setting datagram channel to NULL! ");
            }

            this.onHold = false;

            assert this.datagramChannel != null;

        } catch (Exception ex) {
            logger.error("Unexpected exception ", ex);
            throw new RuntimeException("Unexpected exception setting sdp", ex);
        }
    }

    public void setRemoteAddressAutoDiscovered(boolean autoDiscoverFlag) {
        this.remoteAddressAutoDiscovered = autoDiscoverFlag;

    }
    
   

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;

    }

    public boolean isOnHold() {
        return this.onHold;
    }

    
    /**
     * Set the remote IP address and port.
     * 
     * @param ipAddress
     * @param port
     * @throws UnknownHostException
     */
    public void setIpAddressAndPort(String ipAddress, int port)
        throws UnknownHostException {
      super.ipAddress = ipAddress;
      super.port = port;
      super.socketAddress = new InetSocketAddress( InetAddress.getByName(ipAddress), port);
        
    }

    /**
     * @return the remoteAddressAutoDiscovered
     */
    public boolean isRemoteAddressAutoDiscovered() {
        return remoteAddressAutoDiscovered;
    }
    
    
}
