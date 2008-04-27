package org.sipfoundry.sipxbridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.XmlRpcRequest;

/**
 * The SIPXbridge XML RPC handler.
 * 
 * @author M. Ranganathan
 * 
 */
public class SipXbridgeServer implements Symmitron {

    private static Logger logger = Logger.getLogger(SipXbridgeServer.class);

    /*
     * Map that pairs the SymSession id with the SymSession
     */
    private static HashMap<String, Sym> sessionMap = new HashMap<String, Sym>();

    /*
     * Map that pairs the rtpSesion owner with the rtp session
     */
    private static HashMap<String, HashSet<Sym>> sessionResourceMap = new HashMap<String, HashSet<Sym>>();

    /*
     * A map that pairs the RtpBridge id with the rtp Bridge.
     */
    private static HashMap<String, Bridge> bridgeMap = new HashMap<String, Bridge>();

    /*
     * A map that pairs the owner with the RtpBridge
     */
    private static HashMap<String, HashSet<Bridge>> bridgeResourceMap = new HashMap<String, HashSet<Bridge>>();

    /*
     * A map of component name to instance handle.
     */
    private static HashMap<String, String> instanceTable = new HashMap<String, String>();

    /*
     * My Instance handle
     */
    private static String myHandle = "sipxbridge:"
            + Math.abs(new Random().nextLong());

    private Map<String, Object> createErrorMap(int errorCode, String reason) {
        Map<String, Object> retval = new HashMap<String, Object>();
        retval.put(STATUS_CODE, ERROR);
        retval.put(ERROR_CODE, errorCode);
        retval.put(ERROR_INFO, reason);

        retval.put(INSTANCE_HANDLE, myHandle);

        return retval;
    }

    private Map<String, Object> createSuccessMap() {
        Map<String, Object> retval = new HashMap<String, Object>();
        retval.put(STATUS_CODE, OK);
        retval.put(INSTANCE_HANDLE, myHandle);
        return retval;
    }

    private void addSymResource(String controllerId, Sym sym) {
        sessionMap.put(sym.getId(), sym);
        HashSet<Sym> syms = sessionResourceMap.get(controllerId);
        if (syms != null) {
            syms.add(sym);
        } else {
            syms = new HashSet<Sym>();
            syms.add(sym);
            sessionResourceMap.put(controllerId, syms);
        }
    }

    /**
     * The RPC handler for sipxbridge.
     */
    public SipXbridgeServer() {

    }

    /**
     * Current bridge State.
     * 
     * @return the current bridge state.
     */
    public String getStatus() {
        return Gateway.getState().toString();
    }

    /**
     * Start the bridge.
     * 
     */
    public String start() {
        try {
            Gateway.start();
        } catch (Exception ex) {
            logger.error("Exception starting bridge ", ex);

        }
        return Gateway.getState().toString();
    }

    /**
     * Stop the bridge
     */
    public String stop() {
        try {
            Gateway.stop();
        } catch (Exception ex) {
            logger.error("Exception in stopping bridge", ex);
        }
        return Gateway.getState().toString();
    }

    /**
     * Get the Port Range that is handled by the Bridge.
     * 
     * @return the port range supported by the bridge.
     */
    public Map getRtpPortRange() {

        PortRange portRange = new PortRange(
                Gateway.getRtpPortRangeLowerBound(), Gateway
                        .getRtpPortRangeUpperBound());

        return portRange.toMap();

    }

    /**
     * Check for client reboot.
     * 
     * @param controllerHandle -
     *            a client handle in the form componentName:instanceId
     */
    public void checkForControllerReboot(String controllerHandle) {
        String[] handleParts = controllerHandle.split(":");
        if (handleParts.length != 2) {
            Map<String, Object> retval = this.createErrorMap(ILLEGAL_ARGUMENT,
                    "handle must have the format componentName:instance");
        }
        String componentName = handleParts[0];

        String previousInstance = instanceTable.get(componentName);
        if (previousInstance == null) {
            instanceTable.put(componentName, controllerHandle);
        } else if (!previousInstance.equals(controllerHandle)) {
            HashSet<Bridge> rtpBridges = bridgeResourceMap
                    .get(previousInstance);
            for (Bridge rtpBridge : rtpBridges) {
                rtpBridge.stop();
            }
            bridgeResourceMap.remove(previousInstance);
            bridgeResourceMap.put(controllerHandle, new HashSet<Bridge>());
            HashSet<Sym> rtpSessions = sessionResourceMap.get(previousInstance);
            for (Sym rtpSession : rtpSessions) {
                rtpSession.close();
            }
            sessionResourceMap.remove(previousInstance);
            sessionResourceMap.put(controllerHandle, new HashSet<Sym>());

            instanceTable.put(componentName, controllerHandle);
        }
    }

    /**
     * Sign in a controller.
     */
    public Map<String, Object> signIn(String remoteHandle) {

        logger.debug("signIn " + remoteHandle);
        try {
            checkForControllerReboot(remoteHandle);
            return createSuccessMap();
        } catch (Exception ex) {
            logger.error("Error occured during processing ", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sipfoundry.sipxbridge.Symmitron#createSyms(java.lang.String,
     *      int, int)
     */
    public Map<String, Object> createSyms(String controllerHandle, int count,
            int parity) {
        try {
            this.checkForControllerReboot(controllerHandle);

            PortRange portRange = Gateway.getPortManager().allocate(count,
                    parity == ODD ? Parity.ODD : Parity.EVEN);
            if (portRange == null)
                return createErrorMap(PORTS_NOT_AVAILABLE,
                        "Ports not available");

            Map<String, Object> retval = createSuccessMap();
            HashSet<Object> hset = new HashSet<Object>();
            for (int i = 0; i < count; i++) {
                Sym sym = new Sym();
                SymReceiverEndpoint rtpEndpoint = new SymReceiverEndpoint(
                        portRange.getLowerBound() + i);
                sym.setReceiver(rtpEndpoint);
                hset.add(sym.getId());
                this.addSymResource(controllerHandle, sym);
                logger.debug("createSym : " + sym.getId());
            }

            retval.put(SYM_SESSION, hset.toArray(new String[hset.size()]));

            return retval;
        } catch (Exception ex) {
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());

        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sipfoundry.sipxbridge.Symmitron#getSym(java.lang.String,
     *      java.lang.String)
     */
    public Map<String, Object> getSym(String controllerHandle, String symId) {
        
        logger.debug("getSym " + symId);
        try {
            this.checkForControllerReboot(controllerHandle);

            if (sessionMap.get(symId) != null) {
                Map<String, Object> retval = createSuccessMap();
                Sym sym = sessionMap.get(symId);
                retval.put(SYM_SESSION, sym.toMap());
                logger.debug("returning " + retval);
                return retval;

            } else {
                return createErrorMap(SESSION_NOT_FOUND, "");

            }
        } catch (Exception ex) {
            logger.error("Error processing request " + symId);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sipfoundry.sipxbridge.Symmitron#setDestination(java.lang.String,
     *      java.lang.String, java.lang.String, int , int, java.lang.String,
     *      byte[], boolean)
     */
    public Map<String, Object> setDestination(String controllerHandle,
            String symId, String ipAddress, int port, int keepAliveTime,
            String keepaliveMethod, String keepAlivePacketData,
            boolean autoDiscoveryFlag) {
        try {
            this.checkForControllerReboot(controllerHandle);

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("setDestination : "
                        + " controllerHande %s " + " symId %s "
                        + " ipAddress %s " + " port %d "
                        + " keepAliveMethod = %s " + " keepAliveTime %d "
                        + " autoDiscoverFlag %s ", controllerHandle, symId,
                        ipAddress, port, keepaliveMethod, keepAliveTime,
                        autoDiscoveryFlag));
            }

            Sym sym = sessionMap.get(symId);
            if (sym == null) {
                return createErrorMap(SESSION_NOT_FOUND, "");
            }

            Map<String, Object> retval = createSuccessMap();

            // Allocate a new session if needed.
            SymTransmitterEndpoint transmitter = sym.getTransmitter() != null ? sym
                    .getTransmitter()
                    : new SymTransmitterEndpoint();

            transmitter.setIpAddressAndPort(ipAddress, port);
            transmitter.setMaxSilence(keepAliveTime, keepaliveMethod);
            // BUGBUG -- fix this.

            byte[] keepAliveBytes = null;
            if (keepAlivePacketData.equals("")) {
                keepAliveBytes = null;
            } else {
                // TODO -- uudecode it.
            }

            transmitter.setKeepalivePayload(keepAliveBytes);

            transmitter.setRemoteAddressAutoDiscovery(autoDiscoveryFlag);
            if (sym.getTransmitter() == null) {
                sym.setTransmitter(transmitter);
            }
            return retval;

        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("Processing Error", ex);

            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }

    }

    public Map<String, Object> startBridge(String controllerHandle,
            String bridgeId) {
        try {
            this.checkForControllerReboot(controllerHandle);

            Bridge rtpBridge = bridgeMap.get(bridgeId);
            if (rtpBridge == null) {
                return createErrorMap(SESSION_NOT_FOUND, "");
            }
            rtpBridge.start();
            return createSuccessMap();
        } catch (Exception ex) {
            logger.error("Processing Error", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    public Map<String, Object> holdSym(String controllerHandle, String sessionId) {
        try {
            this.checkForControllerReboot(controllerHandle);
            Sym rtpSession = sessionMap.get(sessionId);
            if (rtpSession == null) {
                return this.createErrorMap(SESSION_NOT_FOUND,
                        "Specified RTP Session was not found " + sessionId);
            }
            if (rtpSession.getTransmitter() == null) {
                return this.createErrorMap(ILLEGAL_STATE,
                        "transmitter is not assigned for rtp session "
                                + sessionId);
            }
            rtpSession.getTransmitter().setOnHold(true);
            return this.createSuccessMap();
        } catch (Exception ex) {
            logger.error("Processing Error", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sipfoundry.sipxbridge.Symmitron#removeSym(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public Map<String, Object> removeSym(String controllerHandle,
            String bridgeId, String rtpSessionId) {
        try {
            this.checkForControllerReboot(controllerHandle);
            Bridge rtpBridge = bridgeMap.get(bridgeId);
            if (rtpBridge == null) {
                return this.createErrorMap(SESSION_NOT_FOUND,
                        "Specified RTP Bridge was not found " + bridgeId);
            }

            Sym rtpSession = sessionMap.get(rtpSessionId);

            if (rtpSession == null) {
                return this.createErrorMap(SESSION_NOT_FOUND,
                        "Specified RTP Session was not found " + rtpSessionId);
            }

            rtpBridge.removeSym(rtpSession);
            return this.createSuccessMap();
        } catch (Exception ex) {
            logger.error("Processing Error", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see org.sipfoundry.sipxbridge.Symmitron#addSym(java.lang.String, java.lang.String, java.lang.String)
     */
    public Map<String, Object> addSym(String controllerHandle, String bridgeId,
            String symId) {
        try {
            this.checkForControllerReboot(controllerHandle);
            Bridge bridge = bridgeMap.get(bridgeId);
            if (bridge == null) {
                return this.createErrorMap(SESSION_NOT_FOUND,
                        "Specified Bridge was not found " + bridgeId);
            }

            Sym sym = sessionMap.get(symId);

            if (sym == null) {
                return this.createErrorMap(SESSION_NOT_FOUND,
                        "Specified sym was not found " + symId);
            }

            bridge.addSym(sym);
            return this.createSuccessMap();
        } catch (Exception ex) {
            logger.error("Processing Error", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.sipfoundry.sipxbridge.Symmitron#createBridge(java.lang.String,
     *      boolean)
     */
    public Map<String, Object> createBridge(String controllerHandle) {

        this.checkForControllerReboot(controllerHandle);

        Bridge bridge = new Bridge();
        bridgeMap.put(bridge.getId(), bridge);
        HashSet<Bridge> bridges = bridgeResourceMap.get(controllerHandle);
        if (bridges != null) {
            bridges.add(bridge);
        } else {
            bridges = new HashSet<Bridge>();
            bridges.add(bridge);
            bridgeResourceMap.put(controllerHandle, bridges);
        }

        Map<String, Object> retval = this.createSuccessMap();
        retval.put(BRIDGE_ID, bridge.getId());
        return retval;

    }

    public Map<String, Object> pauseBridge(String controllerHandle,
            String bridgeId) {
        this.checkForControllerReboot(controllerHandle);
        Bridge rtpBridge = bridgeMap.get(bridgeId);
        if (rtpBridge == null) {
            return this.createErrorMap(SESSION_NOT_FOUND,
                    "Bridge corresponding to " + bridgeId + " not found");
        }
        rtpBridge.pause();
        return this.createSuccessMap();
    }

    public Map<String, Object> resumeBridge(String controllerHandle,
            String bridgeId) {
        this.checkForControllerReboot(controllerHandle);
        Bridge rtpBridge = bridgeMap.get(bridgeId);
        if (rtpBridge == null) {
            return this.createErrorMap(SESSION_NOT_FOUND,
                    "Bridge corresponding to " + bridgeId + " not found");
        }
        rtpBridge.resume();
        return this.createSuccessMap();
    }

    public Map<String, Object> resumeSym(String controllerHandle,
            String sessionId) {
        // TODO Auto-generated method stub
        this.checkForControllerReboot(controllerHandle);
        Sym rtpSession = sessionMap.get(sessionId);
        if (rtpSession == null) {
            return this.createErrorMap(SESSION_NOT_FOUND,
                    "Specified RTP Session was not found " + sessionId);
        }
        if (rtpSession.getTransmitter() == null) {
            return this.createErrorMap(ILLEGAL_STATE,
                    "transmitter is not assigned for rtp session " + sessionId);
        }
        rtpSession.getTransmitter().setOnHold(false);
        return this.createSuccessMap();
    }

    public Map<String, Object> getSymStatistics(String controllerHandle,
            String rtpSessionId) {

        return null;
    }

    public Map<String, Object> getBridgeStatistics(String controllerHandle,
            String bridgeId) {

        return null;
    }

    public Map<String, Object> signOut(String controllerHandle) {
        try {
            HashSet<Bridge> rtpBridges = bridgeResourceMap
                    .get(controllerHandle);
            if (rtpBridges != null) {
                for (Bridge rtpBridge : rtpBridges) {
                    rtpBridge.stop();
                }
            }
            bridgeResourceMap.remove(controllerHandle);

            HashSet<Sym> rtpSessions = sessionResourceMap.get(controllerHandle);
            if (rtpSessions != null) {
                for (Sym rtpSession : rtpSessions) {
                    rtpSession.close();
                }
            }
            sessionResourceMap.remove(controllerHandle);

            return this.createSuccessMap();
        } catch (Exception ex) {
            logger.error("Processing Error", ex);
            return createErrorMap(PROCESSING_ERROR, ex.getMessage());
        }
    }

    public Map<String, Object> destroySym(String controllerHandle, String symId) {
        logger.debug("destroySym: " + symId);
        this.checkForControllerReboot(controllerHandle);

        if (sessionMap.containsKey(symId)) {
            Sym sym = sessionMap.get(symId);
            HashSet<Sym> syms = sessionResourceMap.get(controllerHandle);
            syms.remove(sym);
            if (syms.isEmpty()) {
                sessionResourceMap.remove(controllerHandle);
            }
            sym.close();
            Map<String, Object> retval = this.createSuccessMap();
            return retval;
        } else {

            return this.createErrorMap(SESSION_NOT_FOUND,
                    "Sym with the given Id was not found");
        }
    }

    public Map<String, Object> destroyBridge(String controllerHandle,
            String bridgeId) {
        logger.debug("destroyBridge: " + bridgeId);
        this.checkForControllerReboot(controllerHandle);
        Bridge bridge = bridgeMap.get(bridgeId);
        if (bridge != null) {
            bridgeMap.remove(bridgeId);
            HashSet<Bridge> bridges = bridgeResourceMap.get(controllerHandle);
            bridges.remove(bridge);
            if (bridges.isEmpty()) {
                bridgeResourceMap.remove(controllerHandle);
            }
            for (Sym sym : bridge.getSyms()) {
                sym.close();
                sessionMap.remove(sym.getId());
                HashSet<Sym> syms = sessionResourceMap.get(controllerHandle);
                syms.remove(sym);
                if (syms.isEmpty()) {
                    sessionResourceMap.remove(controllerHandle);
                }

            }
            return this.createSuccessMap();
        } else {
            return this.createErrorMap(SESSION_NOT_FOUND,
                    "Bridge with the given ID was not found");

        }
    }

}
