/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxbridge;

import java.util.ListIterator;
import java.util.TimerTask;

import javax.sip.ClientTransaction;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

/**
 * The registration manager. It refreshes registrations etc.
 * 
 * @author root
 * 
 */
public class RegistrationManager {

    private static Logger logger = Logger.getLogger(RegistrationManager.class);

    private SipProvider provider;

    public RegistrationManager(SipProvider sipProvider) {
        this.provider = sipProvider;

    }

    public void sendRegistrer(ItspAccountInfo itspAccount, String callId, long cseq) throws SipException {
        Request request = SipUtilities.createRegistrationRequest(provider,
                itspAccount,callId,cseq);
        ClientTransaction ct = provider.getNewClientTransaction(request);
        TransactionContext tad = new TransactionContext(ct,
                Operation.SEND_REGISTER);
        tad.setItspAccountInfo(itspAccount);
        ct.sendRequest();
        itspAccount.setState(AccountState.AUTHENTICATING);
    }

    /**
     * Send a de-register to the specified ITSP account.
     * 
     * @param itspAccount -
     *            itsp account with which we want to de-register.
     * @throws SipXbridgeException -
     *             problem with gateway configuration.
     * @throws SipException -
     *             if protocol exception occured.
     */
    public void sendDeregister(ItspAccountInfo itspAccount)
            throws SipXbridgeException, SipException {
        Request request = SipUtilities.createDeregistrationRequest(provider,
                itspAccount);
        ClientTransaction ct = provider.getNewClientTransaction(request);
        TransactionContext tad = TransactionContext.attach(ct,
                Operation.SEND_DEREGISTER);
        tad.setItspAccountInfo(itspAccount);

        ct.sendRequest();

    }

    /**
     * Sends a registration query.
     * 
     * @param itspAccount --
     *            the ITSP account.
     * @throws SipXbridgeException
     * @throws SipException
     * @throws Exception
     */
    public void sendRegisterQuery(ItspAccountInfo itspAccount)
            throws SipXbridgeException, SipException {
        Request request = SipUtilities.createRegisterQuery(provider,
                itspAccount);
        ClientTransaction ct = provider.getNewClientTransaction(request);
        TransactionContext tad = new TransactionContext(ct,
                Operation.SEND_REGISTER_QUERY);
        tad.setItspAccountInfo(itspAccount);

        ct.sendRequest();
    }

    /**
     * Handle the OK response from a Register request. If the original request
     * was a registration attempt and the response is an OK we start a timer to
     * re-register after the current registration expires.
     * 
     * @param responseEvent
     */
    @SuppressWarnings("unchecked")
    public void processResponse(ResponseEvent responseEvent)
            throws SipXbridgeException, SipException {

        Response response = responseEvent.getResponse();
        logger.debug("registrationManager.processResponse() "
                + response.getStatusCode());
        ClientTransaction ct = responseEvent.getClientTransaction();
        if ( ct == null ) {
            logger.warn("Null transaction. Probably delayed response. Dropping response");
            return;
        }
        Request request = ct.getRequest();
        ContactHeader requestContactHeader = (ContactHeader) request
                .getHeader(ContactHeader.NAME);
        SipURI requestContactUri = (SipURI) requestContactHeader.getAddress()
                .getURI();

        if (response.getStatusCode() == Response.OK) {
            ListIterator contactHeaders = (ListIterator) response
                    .getHeaders(ContactHeader.NAME);
            int time = 0;

            if (contactHeaders != null && contactHeaders.hasNext()) {
                while (contactHeaders.hasNext()) {
                    ContactHeader contactHeader = (ContactHeader) contactHeaders
                            .next();
                    SipURI responseContactUri = (SipURI) contactHeader
                            .getAddress().getURI();
                    int port = ((SipURI) contactHeader.getAddress().getURI())
                            .getPort();
                    if (port == -1) {
                        port = 5060;
                    }
                    logger.debug("checking contact " + contactHeader);
                    if (responseContactUri.getHost().equals(
                            requestContactUri.getHost())
                            && requestContactUri.getPort() == port) {
                        time = contactHeader.getExpires();
                        break;
                    }
                }
            } else {
                time = ct.getRequest().getExpires().getExpires();
            }
            ItspAccountInfo itspAccount = ((TransactionContext) ct
                    .getApplicationData()).getItspAccountInfo();
            if (itspAccount.getSipKeepaliveMethod().equals("REGISTER")) {
                time = ct.getRequest().getExpires().getExpires();
            }

            if (time > 2 * Gateway.REGISTER_DELTA) {
                time = time - Gateway.REGISTER_DELTA;
            }

            if (itspAccount.isAlarmSent()) {
                try {
                    Gateway.getAlarmClient().raiseAlarm(
                            Gateway.SIPX_BRIDGE_ACCOUNT_OK,
                            itspAccount.getProxyDomain());
                    itspAccount.setAlarmSent(false);
                } catch (Exception ex) {
                    logger.error("Could not send alarm ", ex);
                }
            }

            itspAccount.setState(AccountState.AUTHENTICATED);

            if (itspAccount.getSipKeepaliveMethod().equals("CR-LF")) {
                itspAccount.startCrLfTimerTask();

            }
            logger.debug("time = " + time + " Seconds ");
            if (time > 0) {
                if (itspAccount.registrationTimerTask != null)
                    itspAccount.registrationTimerTask.cancel();
                String callId = SipUtilities.getCallId(response);
                long cseq = SipUtilities.getSeqNumber(response);
                TimerTask ttask = new RegistrationTimerTask(itspAccount,callId,cseq);
                Gateway.getTimer().schedule(ttask, time * 1000);
            }

        } else {
            if (response.getStatusCode() == Response.FORBIDDEN) {
                ItspAccountInfo itspAccount = ((TransactionContext) ct
                        .getApplicationData()).getItspAccountInfo();
                itspAccount.setState(AccountState.AUTHENTICATION_FAILED);
                if (itspAccount.getSipKeepaliveMethod().equals("CR-LF")) {
                    itspAccount.stopCrLfTimerTask();
                }
                if (!itspAccount.isAlarmSent()) {
                    try {
                        Gateway.getAlarmClient().raiseAlarm(
                                Gateway.SIPX_BRIDGE_AUTHENTICATION_FAILED,
                                itspAccount.getSipDomain());
                        itspAccount.setAlarmSent(true);
                    } catch (Exception ex) {
                        logger.debug("Could not send alarm", ex);
                    }
                }
            } else if (response.getStatusCode() == Response.REQUEST_TIMEOUT) {
                ItspAccountInfo itspAccount = ((TransactionContext) ct
                        .getApplicationData()).getItspAccountInfo();
                if (itspAccount.getSipKeepaliveMethod().equals("CR-LF")) {
                    itspAccount.stopCrLfTimerTask();
                }

                try {
                    if (!itspAccount.isAlarmSent()) {
                        Gateway.getAlarmClient().raiseAlarm(
                                Gateway.SIPX_BRIDGE_OPERATION_TIMED_OUT,
                                itspAccount.getSipDomain());
                        itspAccount.setAlarmSent(true);
                    }
                } catch (Exception ex) {
                    logger.error("Could not send alarm.", ex);
                }
                /*
                 * Retry the server again after 60 seconds.
                 */
                if (itspAccount.registrationTimerTask == null) {
                    TimerTask ttask = new RegistrationTimerTask(itspAccount,null,1L);
                    Gateway.getTimer().schedule(ttask, 60 * 1000);
                }
            } else if (response.getStatusCode() / 100 == 5
                    || response.getStatusCode() / 100 == 6
                    || response.getStatusCode() / 100 == 4) {
                ItspAccountInfo itspAccount = ((TransactionContext) ct
                        .getApplicationData()).getItspAccountInfo();
                if (itspAccount.getSipKeepaliveMethod().equals("CR-LF")) {
                    itspAccount.stopCrLfTimerTask();
                }
                if (!itspAccount.isAlarmSent()) {
                    try {
                        Gateway.getAlarmClient().raiseAlarm(
                                Gateway.SIPX_BRIDGE_ITSP_SERVER_FAILURE,
                                itspAccount.getSipDomain());
                        itspAccount.setAlarmSent(true);
                    } catch (Exception ex) {
                        logger.debug("Could not send alarm", ex);
                    }
                }
                /*
                 * Retry the server again after 60 seconds.
                 */
                if (itspAccount.registrationTimerTask == null) {
                    TimerTask ttask = new RegistrationTimerTask(itspAccount,null,1L);
                    Gateway.getTimer().schedule(ttask, 60 * 1000);
                }
            } else {
                if (response.getStatusCode() != 100) {
                    logger
                            .warn("RegistrationManager: Unexpected Status Code seen "
                                    + response.getStatusCode());
                }
            }
        }
    }

    /**
     * Handle a timeout event ( happens when you pont this to a non existant
     * ITSP ).
     * 
     * @param timeoutEvent
     */
    public void processTimeout(TimeoutEvent timeoutEvent) {
        ClientTransaction ctx = timeoutEvent.getClientTransaction();
        ItspAccountInfo itspAccount = ((TransactionContext) ctx
                .getApplicationData()).getItspAccountInfo();
        /*
         * Try again to register after 30 seconds ( maybe somebody pulled the
         * plug).
         */
        if (itspAccount.registrationTimerTask == null) {
            TimerTask ttask = new RegistrationTimerTask(itspAccount,null,1L);
            Gateway.getTimer().schedule(ttask, 60 * 1000);
        }
        try {
            if (!itspAccount.isAlarmSent()) {
                Gateway.getAlarmClient().raiseAlarm(
                        Gateway.SIPX_BRIDGE_OPERATION_TIMED_OUT,
                        itspAccount.getSipDomain());
                itspAccount.setAlarmSent(true);
            }
        } catch (Exception ex) {
            logger.error("Could not send alarm.", ex);
        }

    }

}
