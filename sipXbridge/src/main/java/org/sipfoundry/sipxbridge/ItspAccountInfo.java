/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxbridge;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;

import javax.sip.address.SipURI;

import org.apache.log4j.Logger;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * The information pertaining to an ITSP account resides in this class.
 * 
 * @author M. Ranganathan
 */

public class ItspAccountInfo implements gov.nist.javax.sip.clientauthutils.UserCredentials {

    private static Logger logger = Logger.getLogger(ItspAccountInfo.class);
    
    private String outboundRegistrar;

    /**
     * The proxy + registrar for the account.
     */
    private String outboundProxy;

    /**
     * The proxy + registrar port.
     */
    private int proxyPort = 5060;

    /**
     * User name for the account.
     */
    private String userName;

    /**
     * The display name for the account.
     */
    private String displayName;

    /**
     * The display name for the account.
     */
    private String password;

    /**
     * The Sip Domain for the register request.
     */
    private String proxyDomain;

    /**
     * The authentication realm
     */
    private String authenticationRealm;

    /**
     * Whether or not to use stun for the contact address.
     */
    private boolean globalAddressingUsed;

    /**
     * What transport to use for signaling.
     */
    private String outboundTransport = "udp";

    /**
     * Use rport setting ( if stun is not used )
     */
    private boolean rportUsed = false;

    /**
     * Route inbound calls to auto attendant.
     */
    private String autoAttendantName = null;
    /**
     * Whether or not to register on gateway initialization
     */
    private boolean registerOnInitialization = true;

    /**
     * Registration Interval (seconds)
     */
    private int registrationInterval = 120;

    /*
     * The state of the account.
     */
    private AccountState state = AccountState.INIT;

    /*
     * The call Id table.
     */
    private HashMap<String, FailureCounter> failureCountTable = new HashMap<String, FailureCounter>();

    
    /*
     * NAT keepalive method.
     */
    private String sipKeepaliveMethod = "CR-LF";

    private CrLfTimerTask crlfTimerTask;

    private String rtpKeepaliveMethod = "USE-EMPTY-PACKET";

    private boolean useRegistrationForCallerId = true;

    private int maxCalls = -1;

    private int callCount = 0;

    /**
     * This task runs periodically depending upon the timeout of the lookup specified.
     * 
     */
    class Scanner extends TimerTask {

        public void run() {
            try {
                Record[] records = new Lookup("_sip._" + getOutboundTransport() + "."
                        + getSipDomain(), Type.SRV).run();
                logger.debug("Did a successful DNS SRV lookup");
                SRVRecord record = (SRVRecord) records[0];
                int port = record.getPort();
                setPort(port);
                long time = record.getTTL() * 1000;
                String resolvedName = record.getTarget().toString();
                setOutboundProxy(resolvedName);
                HopImpl proxyHop = new HopImpl(InetAddress.getByName(getOutboundProxy())
                        .getHostAddress(), getProxyPort(), getOutboundTransport(),
                        ItspAccountInfo.this);
                Gateway.getAccountManager().setHopToItsp(getSipDomain(), proxyHop);
                Gateway.timer.schedule(new Scanner(), time);
            } catch (Exception ex) {
                logger.error("Error looking up domain " + "_sip._" + getOutboundTransport() + "."
                        + getSipDomain());
            }

        }

    }

    class FailureCounterScanner extends TimerTask {

        public FailureCounterScanner() {

        }

        @Override
        public void run() {

            for (Iterator<FailureCounter> it = failureCountTable.values().iterator(); it
                    .hasNext();) {
                FailureCounter fc = it.next();
                long now = System.currentTimeMillis();
                if (now - fc.creationTime > 30000) {
                    it.remove();
                }
            }
        }

    }

    class FailureCounter {
        long creationTime;
        int counter;

        FailureCounter() {
            creationTime = System.currentTimeMillis();
            counter = 0;
        }

        int increment() {
            counter++;
            return counter;
        }
    }

    public ItspAccountInfo() {

    }

    public void startFailureCounterScanner() {
        FailureCounterScanner fcs = new FailureCounterScanner();
        Gateway.timer.schedule(fcs, 5000, 5000);
    }

    public String getOutboundProxy() {
        if (outboundProxy != null)
            return outboundProxy;
        else
            return this.getSipDomain();

    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getUserName() {
        return userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPassword() {
        return password;
    }

    public String getSipDomain() {
        return proxyDomain;
    }

    public boolean isGlobalAddressingUsed() {
        return this.globalAddressingUsed;
    }

    public boolean isRportUsed() {
        return this.rportUsed;
    }

    /**
     * Set the proxy port
     * 
     * @param port
     */

    public void setPort(int port) {
        this.proxyPort = port;

    }

    public void setOutboundProxy(String resolvedName) {
        logger.debug("setOutboundProxy" + resolvedName);
        this.outboundProxy = resolvedName;

    }

    public void startDNSScannerThread(long time) {
        Gateway.timer.schedule(new Scanner(), time);

    }

    public void lookupAccount() throws GatewayConfigurationException {
        // User has already specified an outbound proxy so just bail out.
        if ( this.outboundProxy != null ) return;
        try {
            String outboundDomain = this.getSipDomain();
            Record[] records = new Lookup("_sip._" + this.getOutboundTransport() + "."
                    + outboundDomain, Type.SRV).run();

            if (records == null || records.length == 0) {
                // SRV lookup failed, use the outbound proxy directly.
                logger
                        .debug("SRV lookup returned nothing -- we are going to just use the domain name directly");
            } else {
                logger.debug("Did a successful DNS SRV lookup");
                SRVRecord record = (SRVRecord) records[0];
                int port = record.getPort();
                this.setPort(port);
                long time = record.getTTL() * 1000;
                String resolvedName = record.getTarget().toString();
                if ( resolvedName.endsWith(".") ) {
                    resolvedName = resolvedName.substring(0, resolvedName.lastIndexOf('.'));
                }
                this.setOutboundProxy(resolvedName);
                this.startDNSScannerThread(time);
            }

        } catch (TextParseException ex) {

            throw new GatewayConfigurationException("Problem with domain name lookup", ex);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            logger.fatal("Exception in processing -- could not add ITSP account ", ex);
            throw ex;
        }
    }

    public boolean isInboundCallsRoutedToAutoAttendant() {
        return this.autoAttendantName != null;
    }

    public void setAutoAttendantName(String autoAttendantName) {
        this.autoAttendantName = autoAttendantName;
    }

    public String getAutoAttendantName() {
        return this.autoAttendantName;
    }

    public int getRegistrationInterval() {
        return registrationInterval;
    }

    /**
     * @param proxyPort the proxyPort to set
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @param proxyDomain the proxyDomain to set
     */
    public void setProxyDomain(String proxyDomain) {
        logger.debug("setProxyDomain " + proxyDomain);
        this.proxyDomain = proxyDomain;
    }

    /**
     * @return the proxyDomain
     */
    public String getProxyDomain() {
        return proxyDomain;
    }

    /**
     * @param authenticationRealm the authenticationRealm to set
     */
    public void setAuthenticationRealm(String authenticationRealm) {
        logger.debug("setAuthenticationRealm : " + authenticationRealm);
        this.authenticationRealm = authenticationRealm;
    }

    /**
     * Note that authentication realm is an optional configuration parameter. We return the
     * authenticationRealm if set else we return the outbound proxy if set else we return the
     * domain.
     * 
     * @return the authenticationRealm
     */
    public String getAuthenticationRealm() {
        if (authenticationRealm != null) {
            return authenticationRealm;
        } else {
            return proxyDomain;
        }
    }

    /**
     * @param outboundTransport the outboundTransport to set
     */
    public void setOutboundTransport(String outboundTransport) {
        this.outboundTransport = outboundTransport;
    }

    /**
     * @return the outboundTransport
     */
    public String getOutboundTransport() {
        return outboundTransport;
    }

    /**
     * @param registerOnInitialization the registerOnInitialization to set
     */
    public void setRegisterOnInitialization(boolean registerOnInitialization) {
        this.registerOnInitialization = registerOnInitialization;
    }

    /**
     * @return the registerOnInitialization
     */
    public boolean isRegisterOnInitialization() {
        return registerOnInitialization;
    }

    /**
     * @param rportUsed the rportUsed to set
     */
    public void setRportUsed(boolean rportUsed) {
        this.rportUsed = rportUsed;
    }

    /**
     * @param globalAddressingUsed the globalAddressingUsed to set
     */
    public void setGlobalAddressingUsed(boolean globalAddressingUsed) {
        this.globalAddressingUsed = globalAddressingUsed;
    }

    /**
     * @param userName the userName to set
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int incrementFailureCount(String callId) {
        FailureCounter fc = this.failureCountTable.get(callId);
        if (fc == null) {
            fc = new FailureCounter();
            this.failureCountTable.put(callId, fc);
        }
        int retval = fc.increment();
        logger.debug("incrementFailureCount : " + retval);

        return retval;

    }

    /**
     * @param state the state to set
     */
    public void setState(AccountState state) {
        this.state = state;
    }

    /**
     * @return the state
     */
    public AccountState getState() {
        return state;
    }

    /**
     * @param sipKeepaliveMethod the sipKeepaliveMethod to set
     */
    public void setSipKeepaliveMethod(String sipKeepaliveMethod) {
        this.sipKeepaliveMethod = sipKeepaliveMethod;
    }

    /**
     * @return the sipKeepaliveMethod
     */
    public String getSipKeepaliveMethod() {
        return sipKeepaliveMethod;
    }

    public void startCrLfTimerTask() {
        this.crlfTimerTask = new CrLfTimerTask(Gateway.getWanProvider("udp"), this);
        Gateway.timer.schedule(crlfTimerTask, Gateway.getSipKeepaliveSeconds() * 1000);

    }

    /**
     * @param rtpKeepaliveMethod the rtpKeepaliveMethod to set
     */
    public void setRtpKeepaliveMethod(String rtpKeepaliveMethod) {
        this.rtpKeepaliveMethod = rtpKeepaliveMethod;
    }

    /**
     * @return the rtpKeepaliveMethod
     */
    public String getRtpKeepaliveMethod() {
        return rtpKeepaliveMethod;
    }

    /**
     * @param useRegistrationForCallerId the useRegistrationForCallerId to set
     */
    public void setUseRegistrationForCallerId(boolean useRegistrationForCallerId) {
        this.useRegistrationForCallerId = useRegistrationForCallerId;
    }

    /**
     * @return the useRegistrationForCallerId
     */
    public boolean isUseRegistrationForCallerId() {
        return useRegistrationForCallerId;
    }

    /**
     * @param maxCalls the maxCalls to set
     */
    public void setMaxCalls(String maxCalls) {
        try {
            this.maxCalls = Integer.parseInt(maxCalls);
        } catch (NumberFormatException ex) {
            logger.error("Illegal Argument " + maxCalls);

        }
    }

    /**
     * @return the maxCalls
     */
    public int getMaxCalls() {
        return maxCalls;
    }

    public void decrementCallCount() {
        if (this.callCount > 0) {
            this.callCount--;
        }

    }

    public int getCallCount() {
        return this.callCount;
    }

    public void incrementCallCount() {
        this.callCount++;

    }

    /**
     * @param outboundRegistrar the outboundRegistrar to set
     */
    public void setOutboundRegistrar(String outboundRegistrar) {
        this.outboundRegistrar = outboundRegistrar;
    }

    /**
     * @return the outboundRegistrar
     */
    public String getOutboundRegistrar() {
        return this.outboundRegistrar;
    }

}
