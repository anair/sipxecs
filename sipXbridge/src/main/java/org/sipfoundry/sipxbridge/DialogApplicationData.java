/*
 *  Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 *  Contributors retain copyright to elements licensed under a Contributor Agreement.
 *  Licensed to the User under the LGPL license.
 *
 */

package org.sipfoundry.sipxbridge;

import javax.sdp.SessionDescription;
import javax.sip.Dialog;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;

/**
 * Store information that is specific to a Dialog. This is a temporary holding
 * place for dialog specific data.
 * 
 * @author M. Ranganathan
 * 
 */
class DialogApplicationData {

    private static Logger logger = Logger.getLogger(DialogApplicationData.class);
    
    /*
     * Whether or not to forward requests to the PeerDialogs of this dialog (
     * assuming the state of the Dialog is not TERMINATED).
     */
    boolean forwarding = true;

    /*
     * The Peer Dialog of this Dialog.
     */
    Dialog peerDialog;

    /*
     * The last response seen by the dialog.
     */
    Response lastResponse;

    /*
     * The B2BUA associated with the dialog.
     */
    private BackToBackUserAgent backToBackUserAgent;

    /*
     * The replaced dialog (for Consultative XFer processing ).
     */
    Dialog replacedDialog;

    /*
     * The MOH server dialog
     */
    Dialog musicOnHoldDialog;
    
    /*
     * Rtp session associated with this call leg.
     */

    private RtpSession rtpSession;
    
    
    
    boolean isSdpAnswerPending;

    
    /*
     * Account information for the outbound dialog.
     */
    ItspAccountInfo itspInfo; 
    
    
    boolean isOriginatedBySipxbridge;

   
    Request request;

    String sessionDescription;

    long lastAckSent;

    boolean sendReInviteOnResume;

  

    
    private DialogApplicationData() {

    }

    
    static BackToBackUserAgent getBackToBackUserAgent(Dialog dialog) {
        if (dialog == null) {
            logger.debug("null dialog -- returning null ");
            return null;
        } else if (dialog.getApplicationData() == null) {
            logger.debug("null dialog application data -- returning null");
            return null;
        } else {
            return ((DialogApplicationData) dialog.getApplicationData()).getBackToBackUserAgent();
        }
    }
    
    /**
     * Conveniance methods
     */
     static Dialog getPeerDialog(Dialog dialog) {
        return ((DialogApplicationData) dialog.getApplicationData()).peerDialog;
    }

     static RtpSession getRtpSession(Dialog dialog) {
        return ((DialogApplicationData) dialog.getApplicationData()).rtpSession;
    }
    
     

     static DialogApplicationData attach(
            BackToBackUserAgent backToBackUserAgent, Dialog dialog, Request request) {
        if ( backToBackUserAgent == null ) 
            throw new NullPointerException("Null back2back ua");
        DialogApplicationData dat = new DialogApplicationData();
        dat.request = request;
        dat.setBackToBackUserAgent(backToBackUserAgent);
        dialog.setApplicationData(dat);
        return dat;
    }

     static DialogApplicationData get(Dialog dialog) {
        return (DialogApplicationData) dialog.getApplicationData();
    }

    /**
     * @param rtpSession the rtpSession to set
     */
     void setRtpSession(RtpSession rtpSession) {
      
        this.rtpSession = rtpSession;
    }

    /**
     * @return the rtpSession
     */
     RtpSession getRtpSession() {
        return rtpSession;
    }

    

    /**
     * @param backToBackUserAgent the backToBackUserAgent to set
     */
     void setBackToBackUserAgent(BackToBackUserAgent backToBackUserAgent) {
        this.backToBackUserAgent = backToBackUserAgent;
    }

    /**
     * @return the backToBackUserAgent
     */
     BackToBackUserAgent getBackToBackUserAgent() {
        return backToBackUserAgent;
    }
    

   

}
