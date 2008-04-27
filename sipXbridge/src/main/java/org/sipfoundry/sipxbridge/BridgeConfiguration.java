/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxbridge;

import java.net.InetAddress;

/**
 * A class that represents the configuration of the SipxBridge. IMPORTANT -- the
 * methods of this class are tied to sipxbridge.xsd. Do not change method names
 * or signatures unless you also edit schema/sipxbridge.xsd.
 * 
 * 
 * @author M. Ranganathan
 * 
 */
public class BridgeConfiguration {
    private String externalAddress;
    private String localAddress;
    private int externalPort = 5080;
    private int localPort = 5090;
    private String sipxProxyDomain;
    private String stunServerAddress =null;
    private String logLevel = "INFO";
    private int rtpPortLowerBound = 25000;
    private int rtpPortUpperBound = 25500;
    private String musicOnHoldName = "~~mh~";
    private boolean musicOnHoldEnabled = false;
    private int xmlRpcPort = 0;
    private int sipKeepalive = 20 * 1000; // Miliseconds for SIP keepalive.
    private int mediaKeepalive = 160; // milisec for media keepalive.
    private String logFileDirectory = "/var/log/sipxpbx/";
    private int globalAddressRediscoveryPeriod = 30;
    private String codecName = "PCMU";

    /**
     * @param externalAddress
     *            the externalAddress to set
     */
    public void setExternalAddress(String externalAddress) {
        try {
            this.externalAddress = InetAddress.getByName(externalAddress)
                    .getHostAddress();
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid address : "
                    + externalAddress);
        }
    }

    /**
     * @return the externalAddress
     */
    public String getExternalAddress() {
        return externalAddress;
    }

    /**
     * @param localAddress
     *            the localAddress to set
     */
    public void setLocalAddress(String localAddress) {
        try {
            this.localAddress = InetAddress.getByName(localAddress)
                    .getHostAddress();
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid address : "
                    + externalAddress);
        }
    }

    /**
     * @return the localAddress
     */
    public String getLocalAddress() {
        return localAddress;
    }

    /**
     * @param externalPort
     *            the externalPort to set
     */
    public void setExternalPort(int externalPort) {
        this.externalPort = externalPort;
    }

    /**
     * @return the externalPort
     */
    public int getExternalPort() {
        return externalPort;
    }

    /**
     * @param localPort
     *            the localPort to set
     */
    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    /**
     * @return the localPort
     */
    public int getLocalPort() {
        return localPort;
    }

    /**
     * @param sipxProxyDomain
     *            the sipxProxyDomain to set
     */
    public void setSipxProxyDomain(String sipxProxyDomain) {
        this.sipxProxyDomain = sipxProxyDomain;
    }

    /**
     * @return the sipxProxyDomain
     */
    public String getSipxProxyDomain() {
        return sipxProxyDomain;
    }

    /**
     * @param stunServerAddress
     *            the stunServerAddress to set
     */
    public void setStunServerAddress(String stunServerAddress) {

        this.stunServerAddress = stunServerAddress;
    }

    /**
     * @return the stunServerAddress
     */
    public String getStunServerAddress() {
        return stunServerAddress;
    }

    /**
     * set the global address rediscovery period.
     */
    public void setGlobalAddressRediscoveryPeriod(int period) {
        this.globalAddressRediscoveryPeriod = period;
    }

    /**
     * @return the globalAddressRediscoveryPeriod
     */
    public int getGlobalAddressRediscoveryPeriod() {
        return globalAddressRediscoveryPeriod;
    }

    /**
     * @param logLevel
     *            the logLevel to set
     */
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * @param portRange
     */
    public void setPortRange(String portRange) {
        String[] ports = portRange.split(":");
        if (ports.length != 2) {
            throw new IllegalArgumentException(
                    "Must have format lower:upper bound");
        }
        String lowBound = ports[0];
        String highBound = ports[1];
        this.rtpPortLowerBound = new Integer(lowBound).intValue();
        this.rtpPortUpperBound = new Integer(highBound).intValue();
        if (this.rtpPortLowerBound >= this.rtpPortUpperBound
                || this.rtpPortLowerBound < 0 || this.rtpPortUpperBound < 0) {
            throw new IllegalArgumentException(
                    "Port range should be lower:upper bound integers and postivie");
        }
    }

    /**
     * @return the logLevel
     */
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * @return the lower bound of the rtp port space.
     */
    public int getRtpPortLowerBound() {
        return this.rtpPortLowerBound;
    }

    /**
     * @return the upper bound of the rtp port space.
     */
    public int getRtpPortUpperBound() {
        return this.rtpPortUpperBound;
    }

    /**
     * @return Return the MOH UserName or null if no MOH is supported.
     */
    public String getMusicOnHoldName() {
        if (!this.isMusicOnHoldSupportEnabled())
            return null;
        else
            return this.musicOnHoldName;
    }

    /**
     * @param musicOnHoldEnabled
     *            the musicOnHoldEnabled to set
     */
    public void setMusicOnHoldSupportEnabled(boolean musicOnHoldEnabled) {
        this.musicOnHoldEnabled = musicOnHoldEnabled;
    }

    /**
     * @return the musicOnHoldEnabled
     */
    public boolean isMusicOnHoldSupportEnabled() {
        return musicOnHoldEnabled;
    }

    /**
     * @param xmlRpcPort
     *            the xmlRpcPort to set
     */
    public void setXmlRpcPort(int xmlRpcPort) {
        this.xmlRpcPort = xmlRpcPort;
    }

    /**
     * @return the xmlRpcPort
     */
    public int getXmlRpcPort() {
        return xmlRpcPort;
    }

    /**
     * @param sipKeepalive
     *            the sipKeepalive to set
     */
    public void setSipKeepalive(int sipKeepalive) {
        this.sipKeepalive = sipKeepalive;
    }

    /**
     * @return the sipKeepalive
     */
    public int getSipKeepalive() {
        return sipKeepalive;
    }

    /**
     * @param mediaKeepalive
     *            the mediaKeepalive to set
     */
    public void setMediaKeepalive(int mediaKeepalive) {
        this.mediaKeepalive = mediaKeepalive * 1000;
    }

    /**
     * @return the mediaKeepalive
     */
    public int getMediaKeepalive() {
        return mediaKeepalive;
    }

    /**
     * @param logFileName
     *            the logFileName to set
     */
    public void setLogFileDirectory(String logFileDirectory) {
        this.logFileDirectory = logFileDirectory;
    }

    /**
     * @return the logFileName
     */
    public String getLogFileDirectory() {
        return logFileDirectory;
    }

    public void setCodecName(String codecName) {
        this.codecName = codecName;
    }

    public String getCodecName() {
        return this.codecName;
    }

}
