/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
/**
 * This is in charge of shuffling data. There is a single thread that shuffles data for all bridges.
 * 
 */
package org.sipfoundry.sipxbridge.symmitron;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.sipfoundry.sipxbridge.BridgeState;

class DataShuffler implements Runnable {

    // The buffer into which we'll read data when it's available
    private static ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    private static Selector selector;
    private static Logger logger = Logger.getLogger(DataShuffler.class.getPackage().getName());

    private static boolean initializeSelectors = true;

    public DataShuffler() {

    }

    private static void initializeSelector() {

        try {
            if ( selector != null ) selector.close();
            selector = Selector.open();
            

            for (Bridge bridge : ConcurrentSet.getBridges()) {

                for (Sym session : bridge.sessions) {
                    try {
                        if (session.getReceiver() != null
                                && session.getReceiver().getDatagramChannel()
                                        .isOpen()) {
                            session.getReceiver().getDatagramChannel()
                                    .configureBlocking(false);
                            session.getReceiver().getDatagramChannel()
                                    .register(selector, SelectionKey.OP_READ);
                        }
                    } catch (ClosedChannelException ex) {
                        // Avoid loading any closed channels in our select set.
                        continue;
                    }
                }
                initializeSelectors = false;
            }

        } catch (IOException ex) {
            logger.error("Unepxected exception", ex);
            return;
        }

    }

    public void run() {

        // Wait for an event one of the registered channels
        logger.debug("Starting Shuffler");

        while (true) {
            Bridge bridge = null;
            try {

                if (initializeSelectors) {
                    initializeSelector();
                }

                selector.select();

                // Iterate over the set of keys for which events are
                // available
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys()
                        .iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isReadable()) {
                        readBuffer.clear();
                        DatagramChannel datagramChannel = (DatagramChannel) key
                                .channel();
                        if (!datagramChannel.isOpen()) {
                            selector.keys().remove(key);
                            continue;
                        }
                        bridge = ConcurrentSet.getBridge(datagramChannel);
                        if (bridge == null)
                            continue;
                        InetSocketAddress remoteAddress = (InetSocketAddress) datagramChannel
                                .receive(readBuffer);

                        bridge.processingCount++;
                        if (bridge.getState() != BridgeState.RUNNING) {
                            if (logger.isDebugEnabled()) {
                                logger
                                        .debug("RtpBridge:Discarding packet. Bridge state is "
                                                + bridge.getState());
                            }
                            continue;
                        }

                        try {

                            for (Sym sym : bridge.sessions) {
                                if (datagramChannel == sym.getReceiver()
                                        .getDatagramChannel()) {
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("got something on "
                                                + sym.getReceiver()
                                                        .getIpAddress() + ":"
                                                + sym.getReceiver().getPort());
                                        if (remoteAddress != null) {
                                            if (logger.isDebugEnabled())
                                                logger
                                                        .debug("remoteIpAddressAndPort : "
                                                                + remoteAddress
                                                                        .getAddress()
                                                                        .getHostAddress()
                                                                + ":"
                                                                + remoteAddress
                                                                        .getPort());
                                        }
                                    }
                                    sym.lastPacketTime = System
                                            .currentTimeMillis();
                                    sym.packetsReceived++;

                                    bridge.lastPacketTime = sym.lastPacketTime;

                                    /*
                                     * Set the remote port of the transmitter
                                     * side of the connection. This allows for
                                     * NAT reboots ( port can change while in
                                     * progress. This is not relevant for the
                                     * LAN side.
                                     */
                                    if (sym.getTransmitter() != null) {
                                        AutoDiscoveryFlag autoDiscoveryFlag = sym
                                                .getTransmitter()
                                                .getAutoDiscoveryFlag();

                                        if (autoDiscoveryFlag != AutoDiscoveryFlag.NO_AUTO_DISCOVERY) {
                                            if (remoteAddress != null) {
                                                if (autoDiscoveryFlag == AutoDiscoveryFlag.IP_ADDRESS_AND_PORT) {
                                                    sym
                                                            .getTransmitter()
                                                            .setIpAddressAndPort(
                                                                    remoteAddress
                                                                            .getAddress()
                                                                            .getHostAddress(),
                                                                    remoteAddress
                                                                            .getPort());
                                                } else if (autoDiscoveryFlag == AutoDiscoveryFlag.PORT_ONLY) {
                                                    sym
                                                            .getTransmitter()
                                                            .setPort(
                                                                    remoteAddress
                                                                            .getPort());
                                                }
                                            }
                                        }

                                    }

                                    continue;
                                }
                                SymTransmitterEndpoint writeChannel = sym
                                        .getTransmitter();
                                if (writeChannel == null)
                                    continue;

                                try {

                                    /*
                                     * No need for header rewrite. Just flip and
                                     * push out.
                                     */
                                    if (!writeChannel.isOnHold()) {
                                        writeChannel
                                                .send((ByteBuffer) readBuffer
                                                        .flip());
                                        sym.getTransmitter().packetsSent++;

                                    } else {
                                        if (logger.isDebugEnabled()) {
                                            logger
                                                    .debug("WriteChannel on hold."
                                                            + writeChannel
                                                                    .getIpAddress()
                                                            + ":"
                                                            + writeChannel
                                                                    .getPort()
                                                            + " Not forwarding");
                                        }
                                    }

                                } catch (Exception ex) {
                                    logger.error(
                                            "Unexpected error shuffling bytes",
                                            ex);
                                }
                            }
                        } finally {

                        }

                    }
                }
            } catch (Exception ex) {
                logger.error("Unexpected exception occured", ex);
                if (bridge != null && bridge.sessions != null) {
                    for (Sym rtpSession : bridge.sessions) {
                        rtpSession.close();
                    }
                }
                if ( bridge != null )
                    bridge.setState(BridgeState.TERMINATED);
                continue;
            }

        }

    }

    public static void initializeSelectors() {
        initializeSelectors = true;
        if (selector != null) {
            selector.wakeup();
        }

    }

}