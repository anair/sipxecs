/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxbridge;

import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.clientauthutils.UserCredentials;
import gov.nist.javax.sip.header.ims.PPreferredIdentityHeader;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.xbill.DNS.TextParseException;

/**
 * Keeps a mapping of account ID to ItspAccountInfo and a mapping of account ID to sip pbx account
 * info.
 * 
 * @author M. Ranganathan
 * 
 */
public class AccountManagerImpl implements gov.nist.javax.sip.clientauthutils.AccountManager {

    private static Logger logger = Logger.getLogger(AccountManagerImpl.class);

    private HashSet<ItspAccountInfo> itspAccounts = new HashSet<ItspAccountInfo>();

 
    /*
     * The reverse name lookup map
     */
    private ConcurrentMap<String, String> addressToDomainNameMap = new ConcurrentHashMap<String, String>();

    private BridgeConfiguration bridgeConfiguration;

    private Hashtable<String, Boolean> alarmTable = new Hashtable<String, Boolean>();

    public AccountManagerImpl() {

    }

    // //////////////////////////////////////////////////////////////////
    // Package local methods.
    // //////////////////////////////////////////////////////////////////
    /**
     * @return the bridgeConfiguration
     */
    BridgeConfiguration getBridgeConfiguration() {
        return bridgeConfiguration;
    }


    /**
     * Start the failure timout timers for each of the accounts we manage.
     */
    void startAuthenticationFailureTimers() {

        for (ItspAccountInfo itspAccountInfo : this.getItspAccounts()) {
            itspAccountInfo.startFailureCounterScanner();
        }
    }

   

    /**
     * Get the default outbound ITSP account for outbound calls.
     */
    ItspAccountInfo getDefaultAccount() {
        return itspAccounts.iterator().next();
    }

    /**
     * Get the outbound ITSP account for a specific outbund SipURI.
     */
    ItspAccountInfo getAccount(Request request) {

        SipURI sipUri = (SipURI) request.getRequestURI();
        logger.debug("getItspAccount: fetching account for " + sipUri);
        ItspAccountInfo accountFound = null;
        try {

            for (ItspAccountInfo accountInfo : itspAccounts) {
                if (accountInfo.getProxyDomain() != null
                        && sipUri.getHost().endsWith(accountInfo.getProxyDomain())) {
                    if (accountInfo.getCallerId() == null) {
                        accountFound = accountInfo;
                        return accountInfo;
                    } else {
                        String callerId = accountInfo.getCallerId();
                        FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);

                        String userStr = ((SipURI) fromHeader.getAddress().getURI()).getUser();
                        String domainStr = ((SipURI) fromHeader.getAddress().getURI()).getHost();
                        if (userStr.equals("anonymous") && domainStr.equals("invalid")) {
                            PPreferredIdentityHeader pai = (PPreferredIdentityHeader) request
                                    .getHeader(PPreferredIdentityHeader.NAME);
                            if (pai == null) {
                                logger.warn("Anonymous call without P-Preferred-Identity ");
                                // BUGBUG - this is really a mistake we should reject
                                // the call if the PAI header is missing
                                accountFound = accountInfo;
                                return accountInfo;
                            }
                            userStr = ((SipURI) pai.getAddress().getURI()).getUser();
                        }
                        if (callerId.startsWith(userStr)) {
                            accountFound = accountInfo;
                            return accountInfo;
                        }

                    }
                }
            }
            // Fallback -- cannot find calling line id.
            for (ItspAccountInfo accountInfo : itspAccounts) {
                logger.warn("Could not match user part of inbound request URI");
                if (accountInfo.getProxyDomain() != null) {
                    if (sipUri.getHost().endsWith(accountInfo.getProxyDomain())) {
                        accountFound = accountInfo;
                        return accountInfo;
                    }
                }
            }
           /*
             * logger.error( Gateway.ACCOUNT_NOT_FOUND_ALARM_ID + " uri = " + sipUri ); try {
             * 
             * if (alarmTable.get(sipUri.getHost()) == null) { alarmTable.put(sipUri.getHost(),
             * true); Gateway.getAlarmClient().raiseAlarm(Gateway.ACCOUNT_NOT_FOUND_ALARM_ID,
             * sipUri.getHost()); } } catch (XmlRpcException e) { logger.error("Could not send
             * alarm " + Gateway.ACCOUNT_NOT_FOUND_ALARM_ID + " uri = " + sipUri); }
             */
            
            /*
             * If an account is not found return an account record with the
             * domain set to the outbound request domain. The INVITE will be
             * forwarded. If the other side does not like the INVITE it an
             * complain about it. See issue XX-5623 
             */
            accountFound = new ItspAccountInfo();
            accountFound.setProxyDomain(sipUri.getHost());
            accountFound.setGlobalAddressingUsed(true);
            return null;
        } finally {
            logger.debug("getItspAccount: returning " + accountFound);
        }
    }

    /**
     * Get a collection of Itsp accounts.
     * 
     * @return
     */
    Collection<ItspAccountInfo> getItspAccounts() {

        return itspAccounts;
    }

  

    /**
     * Get an ITSP account based on the host and port of the indbound request.
     * 
     * @param host
     * @param port
     * @return
     */
    ItspAccountInfo getItspAccount(String host, int port) {
        logger.debug("INVITE received on " + host + ":" + port);
        for (ItspAccountInfo accountInfo : this.getItspAccounts()) {
            if (accountInfo.isRegisterOnInitialization()) {
                // Account needs registration.
                String registrarHost = accountInfo.getOutboundRegistrar();
                logger.debug("registrarHost = " + registrarHost);

                if (host.equals(registrarHost)) {
                    logger.debug("found account ");
                    return accountInfo;
                }

            } else {
                String inBoundProxyDomain = accountInfo.getInboundProxy();
                int inBoundProxyPort = accountInfo.getInboundProxyPort();
                if (host.equals(inBoundProxyDomain) && port == inBoundProxyPort) {
                    logger.debug("found account based on inbound proxy ");
                    return accountInfo;
                }
            }
        }

        return null;
    }

    // //////////////////////////////////////////////////////////////////
    // Public methods.
    // //////////////////////////////////////////////////////////////////
    /**
     * Add the Bridge config.
     */
    public void setBridgeConfiguration(BridgeConfiguration bridgeConfiguration) {
        this.bridgeConfiguration = bridgeConfiguration;
    }

    /**
     * Add an ITSP account to the account database ( method is accessed by the digester).
     */
    public void addItspAccount(ItspAccountInfo accountInfo) throws SipXbridgeException {
        this.itspAccounts.add(accountInfo);
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nist.javax.sip.clientauthutils.AccountManager#getCredentials(javax.sip.ClientTransaction,
     *      java.lang.String)
     */
    public UserCredentials getCredentials(ClientTransaction ctx, String authRealm) {

        SipProvider provider = ((TransactionExt) ctx).getSipProvider();

        /*
         * Challenge from the LAN side.
         */
        if (provider == Gateway.getLanProvider()) {
            if (Gateway.getBridgeConfiguration().getSipxbridgePassword() != null) {
                return new UserCredentials() {
                    public String getPassword() {
                        return Gateway.getBridgeConfiguration().getSipxbridgePassword();
                    }

                    public String getSipDomain() {
                        return Gateway.getSipxProxyDomain();
                    }

                    public String getUserName() {
                        return Gateway.getBridgeConfiguration().getSipxbridgeUserName();
                    }

                };

            } else {
                /*
                 * Credentials not available.
                 */
                return null;
            }

        } else {
            TransactionContext tad = (TransactionContext) ctx.getApplicationData();
            return tad.getItspAccountInfo();
        }

    }

}
