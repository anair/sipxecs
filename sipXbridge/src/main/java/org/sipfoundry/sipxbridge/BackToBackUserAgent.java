/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxbridge;

import gov.nist.javax.sip.DialogExt;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sdp.MediaDescription;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ReferToHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

/**
 * A class that represents an ongoing call. Each call Id points at one of these
 * structures. It can be a many to one mapping. When we receive an incoming
 * request we retrieve the corresponding backtobackuseragent and route the
 * request to it. It keeps the necessary state to handle subsequent requests
 * that are related to this call.
 * 
 * @author M. Ranganathan
 * 
 */
public class BackToBackUserAgent {

    /*
     * The ITSP account for outbound calls.
     */
    private ItspAccountInfo itspAccountInfo;

    private Bridge rtpBridge;

    /*
     * This is just a table of dialogs that reference this B2bua. When the table
     * is empty the b2bua is GCd.
     */
    HashSet<Dialog> dialogTable = new HashSet<Dialog>();

    /*
     * The REFER dialog currently in progress.
     */
    private Dialog referingDialog;

    private Dialog referingDialogPeer;

    private static Logger logger = Logger.getLogger(BackToBackUserAgent.class);

    private static final String ORIGINATOR = "originator";

    private static final String SIPXBRIDGE = "sipxbridge";

    /**
     * Terminate the two sides of the bridge
     */
    private void tearDown() throws Exception {

        for (Dialog dialog : this.dialogTable) {

            Request byeRequest = dialog.createRequest(Request.BYE);
            SipProvider provider = ((DialogExt) dialog).getSipProvider();
            ClientTransaction ct = provider.getNewClientTransaction(byeRequest);
            dialog.sendRequest(ct);
        }

    }

    private void pairDialogs(Dialog dialog1, Dialog dialog2) {
        DialogApplicationData dad1 = DialogApplicationData.get(dialog1);

        DialogApplicationData dad2 = DialogApplicationData.get(dialog2);

        dad1.peerDialog = dialog2;
        dad2.peerDialog = dialog1;
    }

    private Sym getLanRtpSession(Dialog dialog) throws IOException {
        try {
            DialogApplicationData dialogApplicationData = DialogApplicationData
                    .get(dialog);
            if (dialogApplicationData.rtpSession == null) {
                Sym rtpSession = new Sym();
                SymEndpoint endpoint = new SymEndpoint(false);
                endpoint.setIpAddress(Gateway.getLocalAddress());
                rtpSession.setMyEndpoint(endpoint);
                this.getRtpBridge().addSym(rtpSession);
                dialogApplicationData.rtpSession = rtpSession;
                SessionDescription sd = SdpFactory.getInstance()
                        .createSessionDescription(
                                this.rtpBridge.sessionDescription.toString());
                rtpSession.getReceiver().setSessionDescription(sd);
                this.rtpBridge.addSym(rtpSession);

            }
            return dialogApplicationData.rtpSession;
        } catch (SdpParseException ex) {
            logger.error("unexpected parse exception ", ex);
            throw new RuntimeException("Unexpected parse exception", ex);
        }
    }

    /**
     * RTP session that is connected to the WAN Side.
     * 
     * @return
     */
    public Sym getWanRtpSession(Dialog dialog) {
        try {
            Sym rtpSession = DialogApplicationData.getRtpSession(dialog);
            if (rtpSession == null) {
                rtpSession = new Sym();

                /*
                 * Allocate a receiver.
                 */
                SymEndpoint mediaEndpoint = new SymEndpoint(false);
                mediaEndpoint.setIpAddress(itspAccountInfo
                        .isGlobalAddressingUsed() ? Gateway.getGlobalAddress()
                        : Gateway.getLocalAddress());
                rtpSession.setMyEndpoint(mediaEndpoint);
                SessionDescription sd = SdpFactory.getInstance()
                        .createSessionDescription(
                                this.rtpBridge.sessionDescription.toString());
                rtpSession.getReceiver().setSessionDescription(sd);
                DialogApplicationData.get(dialog).rtpSession = rtpSession;
                this.rtpBridge.addSym(rtpSession);
            }
            return rtpSession;
        } catch (SdpParseException ex) {
            throw new RuntimeException("Unexpected exception -- FIXME", ex);
        } catch (IOException ex) {
            return null;
        }

    }

    /**
     * This method handles an Invite with a replaces header in it. It is invoked
     * for consultative transfers. It does a Dialog splicing and media splicing
     * on the Refer dialog. Here is the reference call flow for this case:
     * 
     * <pre>
     * 
     *  Transferor           Transferee             Transfer
     *              |                    |                  Target
     *              |                    |                    |
     *    dialog1   | INVITE/200 OK/ACK F1 F2                 |
     *             	|&lt;-------------------|                    |
     *    dialog1   | INVITE (hold)/200 OK/ACK                |
     *              |-------------------&gt;|                    |
     *    dialog2   | INVITE/200 OK/ACK F3 F4                 |
     *              |----------------------------------------&gt;|
     *    dialog2   | INVITE (hold)/200 OK/ACK                |
     *              |----------------------------------------&gt;|
     *    dialog3   | REFER (Target-Dialog:2,                 |
     *              |  Refer-To:sips:Transferee?Replaces=1) F5|
     *              |----------------------------------------&gt;|
     *    dialog3   | 202 Accepted       |                    |
     *              |&lt;----------------------------------------|
     *    dialog3   | NOTIFY (100 Trying)|                    |
     *              |&lt;----------------------------------------|
     *    dialog3   |                    |            200 OK  |
     *              |----------------------------------------&gt;|
     *    dialog4   |         INVITE (Replaces:dialog1)/200 OK/ACK F6
     *              |                    |&lt;-------------------|
     *    dialog1   | BYE/200 OK         |                    |
     *              |&lt;-------------------|                    |
     *    dialog3   | NOTIFY (200 OK)    |                    |
     *              |&lt;----------------------------------------|
     *    dialog3   |                    |            200 OK  |
     *              |----------------------------------------&gt;|
     *    dialog2   | BYE/200 OK         |                    |
     *              |----------------------------------------&gt;|
     *              |              (transferee and target converse)
     *    dialog4   |                    |  BYE/200 OK        |
     *              |                    |-------------------&gt;|
     * </pre>
     * 
     * 
     * @param serverTransaction
     * @param toDomain
     * @param isphone
     * @throws SipException
     * @throws InvalidArgumentException
     */
    private void handleSpriralInviteWithReplaces(RequestEvent requestEvent,
            ServerTransaction serverTransaction, String toDomain,
            boolean isphone) throws SipException {
        /* The inbound INVITE */
        Request incomingRequest = serverTransaction.getRequest();
        SipProvider provider = (SipProvider) requestEvent.getSource();
        try {

            ReplacesHeader replacesHeader = (ReplacesHeader) incomingRequest
                    .getHeader(ReplacesHeader.NAME);

            Dialog replacedDialog = ((SipStackExt) ProtocolObjects.sipStack)
                    .getReplacesDialog(replacesHeader);

            if (replacedDialog == null) {

                Response response = ProtocolObjects.messageFactory
                        .createResponse(Response.NOT_FOUND, incomingRequest);
                response.setReasonPhrase("Replaced Dialog not found");
                serverTransaction.sendResponse(response);
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("replacedDialogPeerDialog = "
                        + ((DialogApplicationData) replacedDialog
                                .getApplicationData()).peerDialog);
                logger.debug("referingDialogPeerDialog = "
                        + this.referingDialogPeer);
            }

            this.pairDialogs(((DialogApplicationData) replacedDialog
                    .getApplicationData()).peerDialog, this.referingDialogPeer);

            Dialog mohDialog = ((DialogApplicationData) referingDialog
                    .getApplicationData()).musicOnHoldDialog;

            /*
             * Tear down the Music On Hold Dialog if any.
             */
            if (mohDialog != null
                    && mohDialog.getState() != DialogState.TERMINATED) {
                Request byeRequest = mohDialog.createRequest(Request.BYE);
                SipProvider mohDialogProvider = ((DialogExt) mohDialog)
                        .getSipProvider();
                ClientTransaction ctx = mohDialogProvider
                        .getNewClientTransaction(byeRequest);
                mohDialog.sendRequest(ctx);
            }

            /* The replaced dialog is about ready to die so he has no peer */
            ((DialogApplicationData) replacedDialog.getApplicationData()).peerDialog = null;

            if (logger.isDebugEnabled()) {
                logger.debug("referingDialog = " + referingDialog);
                logger.debug("rtpBridgeDump = " + this.getRtpBridge());
                logger.debug("replacedDialog = " + replacedDialog);

                DialogApplicationData dat = (DialogApplicationData) replacedDialog
                        .getApplicationData();
                logger.debug("replacedDialog rtpBridgeDump = "
                        + dat.backToBackUserAgent.getRtpBridge().toString());

            }

            /*
             * We need to form a new bridge. Remove the refering dialog from our
             * rtp bridge. Remove the replacedDialog from its rtpBridge and form
             * a new bridge.
             */
            this.getRtpBridge().pause();
            Set<Sym> myrtpSessions = this.getRtpBridge().getSessions();
            this.rtpBridge.initializeSelectors = true;

            DialogApplicationData replacedDialogApplicationData = (DialogApplicationData) replacedDialog
                    .getApplicationData();
            Bridge hisRtpBridge = replacedDialogApplicationData.backToBackUserAgent
                    .getRtpBridge();
            hisRtpBridge.pause();
            Set<Sym> hisRtpSessions = hisRtpBridge.getSessions();
            Bridge newRtpBridge = new Bridge(true);
          
            
            for (Sym sym: myrtpSessions) {
                if ( sym != DialogApplicationData
                    .getRtpSession(replacedDialog) && sym != DialogApplicationData
                            .getRtpSession(referingDialog)) {
                    newRtpBridge.addSym(sym);
                }
            }
            
            for ( Sym sym : hisRtpSessions) {
                if ( sym != DialogApplicationData
                        .getRtpSession(replacedDialog) && sym != DialogApplicationData
                                .getRtpSession(referingDialog)) {
                        newRtpBridge.addSym(sym);
                 }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("---> After replacement :");
                logger.debug("newRtpBridge = " + newRtpBridge);
                logger.debug("myRtpBridge = " + this.getRtpBridge());
                logger.debug("hisRtpBridge = " + hisRtpBridge);
            }
            
           
            
            /*
             * Let the RTP Bridge initialize its selector table.
             */
            hisRtpBridge.initializeSelectors = true;

            this.rtpBridge.resume();
            hisRtpBridge.resume();
           
            newRtpBridge.start();

            if (logger.isDebugEnabled()) {
                logger.debug("replacedDialog State "
                        + replacedDialog.getState());
            }

            if (replacedDialog.getState() != DialogState.TERMINATED) {
                Request byeRequest = replacedDialog.createRequest(Request.BYE);
                ClientTransaction byeCtx = ((DialogExt) replacedDialog)
                        .getSipProvider().getNewClientTransaction(byeRequest);
                TransactionApplicationData tad = new TransactionApplicationData(
                        Operation.SPIRAL_CONSULTATION_TRANSFER_INVITE_TO_ITSP);
                byeCtx.setApplicationData(tad);
                replacedDialog.sendRequest(byeCtx);

            }

            if (logger.isDebugEnabled()) {

                logger.debug("rtpBridgeDump = " + this.getRtpBridge());

                DialogApplicationData appdata = (DialogApplicationData) replacedDialog
                        .getApplicationData();
                logger
                        .debug("replacedDialog rtpBridgeDump = "
                                + appdata.backToBackUserAgent.getRtpBridge()
                                        .toString());
            }

            Response response = ProtocolObjects.messageFactory.createResponse(
                    Response.OK, incomingRequest);
            ContactHeader contactHeader = SipUtilities.createContactHeader(
                    null, provider);
            response.setHeader(contactHeader);

            serverTransaction.sendResponse(response);

        } catch (ParseException ex) {
            logger.error("Unexpected internal error occured", ex);

            CallControlManager.sendInternalError(serverTransaction, ex);

        } catch (InvalidArgumentException ex) {
            logger.error("Unexpected internal error occured", ex);

            CallControlManager.sendInternalError(serverTransaction, ex);
        }

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public methods.
    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private BackToBackUserAgent(Request request, ItspAccountInfo itspAccountInfo)
            throws IOException {
        this.itspAccountInfo = itspAccountInfo;
        rtpBridge = new Bridge(request);

    }

    public BackToBackUserAgent(SipProvider provider, Request request,
            Dialog dialog, ItspAccountInfo itspAccountInfo) throws IOException {
        this(request, itspAccountInfo);
        this.itspAccountInfo = itspAccountInfo;
        dialogTable.add(dialog);

    }

    /**
     * Remove a dialog from the table ( the dialog terminates ).
     */
    public void removeDialog(Dialog dialog) {

        this.dialogTable.remove(dialog);
        if (this.dialogTable.isEmpty()) {
            this.rtpBridge.stop();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Remove Dialog " + dialog);
            logger.debug("Dialog table size = " + this.dialogTable.size());
        }

        if (dialogTable.size() == 0) {
            Gateway.getCallControlManager().removeBackToBackUserAgent(this);
        }
    }

    /**
     * Get the itsp account info.
     * 
     * @return the itsp account info.
     */
    public ItspAccountInfo getItspAccountInfo() {
        return itspAccountInfo;
    }

    /**
     * Add a dialog entry to the b2bua.
     * 
     * @param provider
     * @param dialog
     */
    public void addDialog(Dialog dialog) {
        this.dialogTable.add(dialog);

    }

    /**
     * This method is called when the REFER is received at the B2BUA. We need to
     * redirect the INVITE to the contact mentioned in the Refer.
     */
    public void referInviteToSipxProxy(Request referRequest, Dialog dialog) {
        logger
                .debug("referInviteToSipxProxy: sendingReInvite to refered-to location");
        try {
            /*
             * Start the early media thread so the remote end does not drop the
             * call while phone rings at the new location.
             */

            ReferToHeader referToHeader = (ReferToHeader) referRequest
                    .getHeader(ReferToHeader.NAME);
            SipURI uri = (SipURI) referToHeader.getAddress().getURI();

            String replacesParam = uri.getHeader(ReplacesHeader.NAME);
            ReplacesHeader replacesHeader = null;
            if (replacesParam != null) {
                URLDecoder decoder = new URLDecoder();
                String decodedReplaces = decoder.decode(replacesParam, "UTF-8");
                replacesHeader = (ReplacesHeader) ProtocolObjects.headerFactory
                        .createHeader("Replaces", decodedReplaces);
            }
            uri.removeParameter("Replaces");
            ((gov.nist.javax.sip.address.SipURIExt) uri).removeHeaders();

            Request newRequest = dialog.createRequest(Request.INVITE);
            newRequest.setRequestURI(uri);
            if (replacesHeader != null) {
                newRequest.addHeader(replacesHeader);
            }

            ContactHeader contactHeader = SipUtilities.createContactHeader(
                    null, Gateway.getLanProvider());
            newRequest.setHeader(contactHeader);
            /*
             * Create a new out of dialog request.
             */
            ToHeader toHeader = (ToHeader) newRequest.getHeader(ToHeader.NAME);
            toHeader.removeParameter("tag");
            FromHeader fromHeader = (FromHeader) newRequest
                    .getHeader(FromHeader.NAME);
            fromHeader.setTag(Integer
                    .toString(Math.abs(new Random().nextInt())));
            ContentTypeHeader cth = ProtocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");

            Sym lanRtpSession = this.getLanRtpSession(dialog);

            newRequest.setContent(lanRtpSession.getReceiver()
                    .getSessionDescription().toString(), cth);
            /*
             * Create a new client transaction.
             */
            ClientTransaction ct = Gateway.getLanProvider()
                    .getNewClientTransaction(newRequest);

            DialogApplicationData newDialogApplicationData = DialogApplicationData
                    .attach(this, ct.getDialog());
            DialogApplicationData dialogApplicationData = (DialogApplicationData) dialog
                    .getApplicationData();

            newDialogApplicationData.peerDialog = dialogApplicationData.peerDialog;
            newDialogApplicationData.musicOnHoldDialog = dialogApplicationData.musicOnHoldDialog;
            logger.debug("referInviteToSipxProxy peerDialog = "
                    + newDialogApplicationData.peerDialog);

            this.referingDialogPeer = dialogApplicationData.peerDialog;

            DialogApplicationData dat = (DialogApplicationData) this.referingDialogPeer
                    .getApplicationData();
            dat.peerDialog = ct.getDialog();

            /*
             * We terminate the dialog. There is no peer.
             */
            dialogApplicationData.peerDialog = null;
            /*
             * Record the referDialog so that when responses for the Client
             * transaction come in we can NOTIFY the referrer.
             */
            this.referingDialog = dialog;

            ct.getDialog().setApplicationData(newDialogApplicationData);
            TransactionApplicationData tad = new TransactionApplicationData(
                    Operation.REFER_INVITE_TO_SIPX_PROXY);
            tad.backToBackUa = ((DialogApplicationData) dialog
                    .getApplicationData()).backToBackUserAgent;
            tad.referingDialog = dialog;
            ct.setApplicationData(tad);
            // Stamp the via header with our stamp so that we know we Referred
            // this request. we will use this for spiral detection.
            ViaHeader via = (ViaHeader) newRequest.getHeader(ViaHeader.NAME);
            // This is our signal that we originated the redirection. We use
            // this in the INVITE processing below.
            via.setParameter(ORIGINATOR, SIPXBRIDGE);

            ct.sendRequest();

        } catch (ParseException ex) {
            logger.error("Unexpected parse exception", ex);
            throw new RuntimeException("Unexpected parse exception", ex);

        } catch (Exception ex) {
            logger
                    .error("Error while processing the request - hanging up ",
                            ex);
            try {
                this.tearDown();
            } catch (Exception e) {
                logger.error("Unexpected exception tearing down session", e);
            }
        }

    }

    /**
     * This method sends an INVITE to SIPX proxy server.
     * 
     * @param requestEvent --
     *            The incoming RequestEvent ( from the ITSP side ) for which we
     *            are generating the request outbound to the sipx proxy server.
     * 
     * @param serverTransaction --
     *            The SIP Server transaction that we created to service this
     *            request.
     * @param itspAccountInfo --
     *            the ITSP account that is sending this inbound request.
     */

    public void sendInviteToSipxProxy(RequestEvent requestEvent,
            ServerTransaction serverTransaction) {
        Request request = requestEvent.getRequest();

        try {
            /*
             * This is a request I got from the external provider. Route this
             * into the network. The SipURI is the sipx proxy URI. He takes care
             * of the rest.
             */

            SipURI incomingRequestURI = (SipURI) request.getRequestURI();
            Dialog inboundDialog = serverTransaction.getDialog();

            SipURI uri = null;
            if (!this.itspAccountInfo.isInboundCallsRoutedToAutoAttendant()) {
                uri = ProtocolObjects.addressFactory.createSipURI(
                        incomingRequestURI.getUser(), Gateway
                                .getSipxProxyAddress());
            } else {
                uri = ProtocolObjects.addressFactory.createSipURI(
                        this.itspAccountInfo.getAutoAttendantName(), Gateway
                                .getSipxProxyAddress());
            }

            String callId = ((CallIdHeader) request
                    .getHeader(CallIdHeader.NAME)).getCallId();
            CallIdHeader callIdHeader = ProtocolObjects.headerFactory
                    .createCallIdHeader(callId);

            CSeqHeader cseqHeader = ProtocolObjects.headerFactory
                    .createCSeqHeader(1L, Request.INVITE);

            FromHeader fromHeader = (FromHeader) request.getHeader(
                    FromHeader.NAME).clone();

            fromHeader.setParameter("tag", Long.toString(Math.abs(new Random()
                    .nextLong())));

            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME)
                    .clone();

            toHeader.removeParameter("tag");

            ViaHeader viaHeader = SipUtilities.createViaHeader(Gateway
                    .getLanProvider(), Gateway.getSipxProxyTransport());

            List<ViaHeader> viaList = new LinkedList<ViaHeader>();

            viaList.add(viaHeader);

            MaxForwardsHeader maxForwards = (MaxForwardsHeader) request
                    .getHeader(MaxForwardsHeader.NAME);

            maxForwards.decrementMaxForwards();
            Request newRequest = ProtocolObjects.messageFactory.createRequest(
                    uri, Request.INVITE, callIdHeader, cseqHeader, fromHeader,
                    toHeader, viaList, maxForwards);
            ContactHeader contactHeader = SipUtilities.createContactHeader(
                    incomingRequestURI.getUser(), Gateway.getLanProvider());
            newRequest.setHeader(contactHeader);
            /*
             * The incoming session description.
             */
            SessionDescription sessionDescription = SipUtilities
                    .getSessionDescription(request);

            SymEndpoint rtpEndpoint = new SymEndpoint(true);
            if ( ! this.itspAccountInfo.getRtpKeepaliveMethod().equals("NONE")) {
                rtpEndpoint.setMaxSilence(Gateway.getMediaKeepaliveMilisec());
            }
            Sym incomingSession = this.getWanRtpSession(inboundDialog);
            incomingSession.setRemoteEndpoint(rtpEndpoint);

            rtpEndpoint.setSessionDescription(sessionDescription);

            ContentTypeHeader cth = ProtocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");
            /*
             * Create a new client transaction.
             */
            ClientTransaction ct = Gateway.getLanProvider()
                    .getNewClientTransaction(newRequest);

            Dialog outboundDialog = ct.getDialog();

            DialogApplicationData.attach(this, outboundDialog);
            pairDialogs(inboundDialog, outboundDialog);

            newRequest.setContent(this.getLanRtpSession(outboundDialog)
                    .getReceiver().getSessionDescription().toString(), cth);

            TransactionApplicationData tad = new TransactionApplicationData(
                    Operation.SEND_INVITE_TO_SIPX_PROXY);
            tad.isReInvite = false;
            tad.clientTransaction = ct;
            tad.clientTransactionProvider = Gateway.getLanProvider();
            tad.incomingSession = incomingSession;
            tad.outgoingSession = this.getLanRtpSession(outboundDialog);
            tad.serverTransaction = serverTransaction;
            tad.serverTransactionProvider = Gateway.getWanProvider();
            tad.backToBackUa = this;
            tad.itspAccountInfo = this.itspAccountInfo;
            ct.setApplicationData(tad);
            serverTransaction.setApplicationData(tad);
            this.addDialog(ct.getDialog());

            ct.sendRequest();

        } catch (InvalidArgumentException ex) {
            logger.error("Unexpected exception encountered");
            throw new RuntimeException("Unexpected exception encountered", ex);
        } catch (ParseException ex) {
            logger.error("Unexpected parse exception", ex);
            throw new RuntimeException("Unexpected parse exception", ex);
        } catch (SdpParseException ex) {
            try {
                Response response = ProtocolObjects.messageFactory
                        .createResponse(Response.BAD_REQUEST, request);
                serverTransaction.sendResponse(response);
            } catch (Exception e) {
                logger.error("Unexpected exception", e);
            }

        } catch (IOException ex) {
            for (Dialog dialog : this.dialogTable) {
                dialog.delete();
            }
        } catch (Exception ex) {
            logger.error("Error while processing the request", ex);
            try {
                Response response = ProtocolObjects.messageFactory
                        .createResponse(Response.SERVICE_UNAVAILABLE, request);
                serverTransaction.sendResponse(response);
            } catch (Exception e) {
                logger.error("Unexpected exception ", e);
            }
        }

    }

    /**
     * Send an INVITE to the MOH server on SIPX.
     * 
     * @return the dialog generated as a result of sending the invite to the MOH
     *         server.
     * 
     */
    public Dialog sendInviteToMohServer(SessionDescription sessionDescription) {

        Dialog retval = null;

        try {

            SipURI uri = Gateway.getMusicOnHoldUri();
            SipProvider lanProvider = Gateway.getLanProvider();

            CallIdHeader callIdHeader = lanProvider.getNewCallId();

            CSeqHeader cseqHeader = ProtocolObjects.headerFactory
                    .createCSeqHeader(1L, Request.INVITE);

            Address gatewayAddress = Gateway.getGatewayFromAddress();

            FromHeader fromHeader = ProtocolObjects.headerFactory
                    .createFromHeader(gatewayAddress, Long.toString(Math
                            .abs(new Random().nextLong())));

            Address mohServerAddress = Gateway.getMusicOnHoldAddress();

            ToHeader toHeader = ProtocolObjects.headerFactory.createToHeader(
                    mohServerAddress, null);

            toHeader.removeParameter("tag");

            ViaHeader viaHeader = SipUtilities.createViaHeader(Gateway
                    .getLanProvider(), Gateway.getSipxProxyTransport());

            List<ViaHeader> viaList = new LinkedList<ViaHeader>();

            viaList.add(viaHeader);

            MaxForwardsHeader maxForwards = ProtocolObjects.headerFactory
                    .createMaxForwardsHeader(20);

            maxForwards.decrementMaxForwards();
            Request newRequest = ProtocolObjects.messageFactory.createRequest(
                    uri, Request.INVITE, callIdHeader, cseqHeader, fromHeader,
                    toHeader, viaList, maxForwards);
            ContactHeader contactHeader = SipUtilities.createContactHeader(
                    Gateway.SIPXBRIDGE_USER, Gateway.getLanProvider());
            newRequest.setHeader(contactHeader);

            /*
             * Create a new client transaction.
             */
            ClientTransaction ct = Gateway.getLanProvider()
                    .getNewClientTransaction(newRequest);

            MediaDescription mediaDescription = (MediaDescription) sessionDescription
                    .getMediaDescriptions(true).get(0);
            mediaDescription.setAttribute("a", "recvonly");
            ContentTypeHeader cth = ProtocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");

            newRequest.setContent(sessionDescription.toString(), cth);

            TransactionApplicationData tad = new TransactionApplicationData(
                    Operation.SEND_INVITE_TO_MOH_SERVER);
            tad.isReInvite = false;
            tad.clientTransaction = ct;
            tad.clientTransactionProvider = Gateway.getLanProvider();
            tad.incomingSession = null;
            tad.outgoingSession = null;
            tad.serverTransaction = null;
            tad.serverTransactionProvider = null;
            tad.backToBackUa = this;
            ct.setApplicationData(tad);
            this.addDialog(ct.getDialog());

            ct.sendRequest();
            retval = ct.getDialog();

        } catch (InvalidArgumentException ex) {
            logger.error("Unexpected exception encountered");
            throw new RuntimeException("Unexpected exception encountered", ex);
        } catch (Exception ex) {
            logger.error("Unexpected parse exception", ex);
            if (retval != null)
                this.dialogTable.remove(retval);

        }
        return retval;
    }

    /**
     * Send a BYE to the Music On Hold Server.
     * 
     * @param musicOnHoldDialog
     * @throws SipException
     */
    public void sendByeToMohServer(Dialog musicOnHoldDialog)
            throws SipException {
        Request byeRequest = musicOnHoldDialog.createRequest(Request.BYE);
        SipProvider lanProvider = Gateway.getLanProvider();
        ClientTransaction ctx = lanProvider.getNewClientTransaction(byeRequest);
        musicOnHoldDialog.sendRequest(ctx);

    }

    /**
     * Set up an outgoing invite to a given ITSP.
     * 
     * @throws SipException
     * @throws Exception
     */
    public void sendInviteToItsp(RequestEvent requestEvent,
            ServerTransaction serverTransaction, String toDomain,
            boolean isphone) throws GatewayConfigurationException, SipException {
        Request incomingRequest = serverTransaction.getRequest();
        Dialog incomingDialog = serverTransaction.getDialog();
        SipProvider itspProvider = Gateway.getWanProvider();

        boolean spiral = false;

        ListIterator headerIterator = incomingRequest
                .getHeaders(ViaHeader.NAME);
        while (headerIterator.hasNext()) {
            ViaHeader via = (ViaHeader) headerIterator.next();
            String originator = via.getParameter(ORIGINATOR);
            if (originator != null && originator.equals(SIPXBRIDGE)) {
                spiral = true;
            }
        }

        ReplacesHeader replacesHeader = (ReplacesHeader) incomingRequest
                .getHeader(ReplacesHeader.NAME);

        if (logger.isDebugEnabled()) {
            logger.debug("sendInviteToItsp: spiral=" + spiral);
        }

        if (spiral && replacesHeader != null) {
            handleSpriralInviteWithReplaces(requestEvent, serverTransaction,
                    toDomain, isphone);
            return;
        }
        try {

            String toUser = ((SipURI) (((ToHeader) incomingRequest
                    .getHeader(ToHeader.NAME)).getAddress().getURI()))
                    .getUser();

            SipURI incomingRequestUri = (SipURI) incomingRequest
                    .getRequestURI();

            String user = incomingRequestUri.getUser();
            FromHeader fromHeader = (FromHeader) incomingRequest.getHeader(FromHeader.NAME).clone();
            Request outgoingRequest = SipUtilities.createInviteRequest(
                    itspProvider, itspAccountInfo, user, fromHeader, toUser, toDomain,
                    isphone);
            ClientTransaction ct = itspProvider
                    .getNewClientTransaction(outgoingRequest);
            Dialog outboundDialog = ct.getDialog();

            DialogApplicationData.attach(this, outboundDialog);

            SessionDescription sd = spiral ? DialogApplicationData
                    .getRtpSession(this.referingDialog).getReceiver()
                    .getSessionDescription() : this.getWanRtpSession(
                    outboundDialog).getReceiver().getSessionDescription();

            SipUtilities.fixupSdpAddresses(sd, itspAccountInfo
                    .isGlobalAddressingUsed());

            /*
             * Indicate that we will be transmitting first.
             */
            MediaDescription mediaDescription = (MediaDescription) sd
                    .getMediaDescriptions(true).get(0);

            mediaDescription.setAttribute("direction", "active");

            ContentTypeHeader cth = ProtocolObjects.headerFactory
                    .createContentTypeHeader("application", "sdp");

            outgoingRequest.setContent(sd.toString(), cth);

            ListeningPoint lp = itspProvider
                    .getListeningPoint(this.itspAccountInfo.getOutboundTransport());
            String sentBy = lp.getSentBy();
            if (this.getItspAccountInfo().isGlobalAddressingUsed()) {
                lp.setSentBy(Gateway.getGlobalAddress() + ":" + lp.getPort());
            }
            lp.setSentBy(sentBy);

            /*
             * If we spiraled back, then pair the refered dialog with the
             * outgoing dialog.
             */
            if (!spiral)
                pairDialogs(incomingDialog, outboundDialog);
            else
                pairDialogs(this.referingDialogPeer, outboundDialog);

            /*
             * This prepares for an authentication challenge.
             */
            TransactionApplicationData tad = new TransactionApplicationData(
                    Operation.SEND_INVITE_TO_ITSP);
            tad.serverTransaction = serverTransaction;
            tad.serverTransactionProvider = Gateway.getLanProvider();
            tad.itspAccountInfo = itspAccountInfo;
            tad.outgoingSession = this.getWanRtpSession(outboundDialog);
            tad.backToBackUa = this;
            tad.clientTransaction = ct;

            serverTransaction.setApplicationData(tad);

            SessionDescription sessionDescription = SipUtilities
                    .getSessionDescription(incomingRequest);

            if (!spiral) {
                tad.incomingSession = this.getLanRtpSession(incomingDialog);

                SymEndpoint rtpEndpoint = new SymEndpoint(true);
                // rtpEndpoint.setMaxSilence(Gateway.getMediaKeepaliveMilisec());
                this.getLanRtpSession(incomingDialog).setRemoteEndpoint(
                        rtpEndpoint);
                rtpEndpoint.setSessionDescription(sessionDescription);

            } else if (spiral && replacesHeader == null) {
                /*
                 * This is a spiral. We are going to reuse the port in the
                 * incoming INVITE (we already own this port). Note here that we
                 * set the early media flag.
                 */
                this.rtpBridge.pause(); // Pause to block inbound packets.
                tad.operation = Operation.SPIRAL_BLIND_TRANSFER_INVITE_TO_ITSP;
                Sym rtpSession = DialogApplicationData
                        .getRtpSession(this.referingDialog);
                if (rtpSession == null) {
                    Response errorResponse = ProtocolObjects.messageFactory
                            .createResponse(Response.SESSION_NOT_ACCEPTABLE,
                                    incomingRequest);
                    errorResponse
                            .setReasonPhrase("Could not RtpSession for refering dialog");
                    serverTransaction.sendResponse(errorResponse);
                    this.rtpBridge.resume();
                    return;
                }
                tad.referingDialog = referingDialog;
                SymEndpoint rtpEndpoint = new SymEndpoint(true);
                rtpSession.setRemoteEndpoint(rtpEndpoint);
                rtpEndpoint.setSessionDescription(sessionDescription);
                if (! tad.itspAccountInfo.getRtpKeepaliveMethod().equals("NONE")) {
                    rtpEndpoint.setMaxSilence(Gateway.getMediaKeepaliveMilisec());
                }
                /*
                 * The RTP session now belongs to the ClientTransaction.
                 */
                this.rtpBridge.addSym(rtpSession);
                this.rtpBridge.resume(); /* Resume operation. */

            } else {
                logger.fatal("Internal error -- case not covered");
                throw new RuntimeException("Case not covered");
            }

            ct.setApplicationData(tad);
            this.addDialog(ct.getDialog());

            /*
             * Record the call for this dialog.
             */
            this.addDialog(ct.getDialog());
            assert this.dialogTable.size() < 3;
            logger.debug("Dialog table size = " + this.dialogTable.size());
            ct.sendRequest();

        } catch (SdpParseException ex) {
            try {
                serverTransaction.sendResponse(ProtocolObjects.messageFactory
                        .createResponse(Response.BAD_REQUEST, incomingRequest));
            } catch (Exception e) {
                String s = "Unepxected exception ";
                logger.fatal(s, e);
                throw new RuntimeException(s, e);
            }
        } catch (ParseException ex) {
            String s = "Unepxected exception ";
            logger.fatal(s, ex);
            throw new RuntimeException(s, ex);
        } catch (IOException ex) {
            logger.error("Caught IO exception ", ex);
            for (Dialog dialog : this.dialogTable) {
                dialog.delete();
            }
        } catch (Exception ex) {
            logger.error("Error occurred during processing of request ", ex);
            try {
                Response response = ProtocolObjects.messageFactory
                        .createResponse(Response.SERVER_INTERNAL_ERROR,
                                incomingRequest);
                response.setReasonPhrase("Unexpected Exception occured at "
                        + ex.getStackTrace()[1].getFileName() + ":"
                        + ex.getStackTrace()[1].getLineNumber());
                serverTransaction.sendResponse(response);
            } catch (Exception e) {
                logger.error("Unexpected exception ", e);
            }
        }

    }

    /**
     * Handle a bye on one of the dialogs of this b2bua.
     * 
     * @param dialog
     * @throws SipException
     */
    public void processBye(RequestEvent requestEvent) throws SipException {
        Dialog dialog = requestEvent.getDialog();
        ServerTransaction st = requestEvent.getServerTransaction();

        DialogApplicationData dad = (DialogApplicationData) dialog
                .getApplicationData();
        Dialog peer = dad.peerDialog;

        if (peer != null && peer.getState() != DialogState.TERMINATED
                && peer.getState() != null) {
            SipProvider provider = ((gov.nist.javax.sip.DialogExt) peer)
                    .getSipProvider();

            Request bye = peer.createRequest(Request.BYE);
            if (this.itspAccountInfo != null
                    && provider == Gateway.getWanProvider()) {
                FromHeader fromHeader = (FromHeader) bye
                        .getHeader(FromHeader.NAME);
                try {
                    fromHeader.getAddress().setDisplayName(
                            itspAccountInfo.getDisplayName());
                } catch (ParseException e) {

                    logger.error("unexpected error setting display name", e);
                }
                if (itspAccountInfo.isRportUsed()) {
                    ViaHeader via = (ViaHeader) bye.getHeader(ViaHeader.NAME);
                    try {
                        via.setRPort();
                    } catch (InvalidArgumentException e) {
                        logger.error("unexpected error setting Rport", e);
                    }
                }
            }
            ClientTransaction ct = provider.getNewClientTransaction(bye);

            TransactionApplicationData tad = new TransactionApplicationData(
                    Operation.PROCESS_BYE);
            tad.serverTransaction = st;
            tad.clientTransaction = ct;
            tad.itspAccountInfo = this.itspAccountInfo;
            st.setApplicationData(tad);
            ct.setApplicationData(tad);

            peer.sendRequest(ct);
        } else {
            /*
             * Peer dialog is not yet established or is terminated.
             */
            logger.debug("BackToBackUserAgent: peerDialog = " + peer);
            if (peer != null) {
                logger.debug("BackToBackUserAgent: peerDialog state = "
                        + peer.getState());
            }
            try {
                Response ok = ProtocolObjects.messageFactory.createResponse(
                        Response.OK, st.getRequest());
                st.sendResponse(ok);

            } catch (InvalidArgumentException ex) {
                logger.error("Unexpected exception", ex);
            } catch (ParseException ex) {
                logger.error("Unexpecte exception", ex);

            }
        }

    }

    /**
     * Return true if theres stuff in the dialog table and false otherwise.
     * 
     * @return
     */
    public boolean isEmpty() {
        return this.dialogTable.isEmpty();
    }

    /**
     * @return the rtpBridge
     */
    public Bridge getRtpBridge() {
        return rtpBridge;
    }

}
