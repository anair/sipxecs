package org.sipfoundry.sipxrest;

import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.extensions.ReferredByHeader;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.sdp.SessionDescription;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogState;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.Hop;
import javax.sip.address.SipURI;
import javax.sip.header.AllowHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ReferToHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.sipfoundry.commons.siprouter.FindSipServer;

public class SipHelper {
    private static final Logger logger = Logger.getLogger(SipHelper.class);

    private static final String APPLICATION = "application";

    private static final String SDP = "sdp";
    
    private int m_maxForwards = 70;
    
    private AbstractSipListener abstractListener;
    
   
   
    SipHelper(AbstractSipListener abstractListener ) {
      this.abstractListener = abstractListener;
    }
    
    public final ClientTransaction getNewClientTransaction(Request request) throws TransactionUnavailableException  {
        return abstractListener.getNewClientTransaction(request);
    }
    
    public final ServerTransaction getNewServerTransaction(Request request) throws TransactionAlreadyExistsException, TransactionUnavailableException  {
        return this.abstractListener.getNewServerTransaction(request);
    }
    
    
  

    public final FromHeader createFromHeader(String fromDisplayName, SipURI fromAddress) throws ParseException {
        Address fromNameAddress = RestServer.getSipStack().getAddressFactory().createAddress(fromDisplayName, fromAddress);
        return getStackBean().getHeaderFactory().createFromHeader(fromNameAddress, Integer.toString(Math.abs(new Random().nextInt())));
    }

    final public ContactHeader createContactHeader() throws ParseException {
        ContactHeader contactHeader = getStackBean().getListeningPoint("udp").createContactHeader();
        Address address = contactHeader.getAddress();
        SipURI sipUri = (SipURI) address.getURI();
        sipUri.setUser(abstractListener.getMetaInf().getSipUserName());
        return contactHeader;
    }

    final public long getSequenceNumber(Message sipMessage) {
        CSeqHeader cseqHeader = (CSeqHeader) sipMessage.getHeader(CSeqHeader.NAME);
        return cseqHeader.getSeqNumber();
    }
    
    final public SipProvider getSipProvider() {
        return getStackBean().getSipProvider("udp");
    }
    
    final public ToHeader createToHeader(SipURI toURI) throws ParseException {
        Address toAddress = getStackBean().getAddressFactory().createAddress(toURI);   
        return getStackBean().getHeaderFactory().createToHeader(toAddress, null);
    }

    final public ViaHeader createViaHeader() throws ParseException, InvalidArgumentException {

        String host = RestServer.getRestServerConfig().getIpAddress();
        int port = RestServer.getRestServerConfig().getSipPort();
        String transport = "UDP";
        // Leave the via header branch Id null.
        // The transaction layer will assign the via header branch Id.
        return getStackBean().getHeaderFactory().createViaHeader(host, port, transport, null);
    }

    

    final public void addContent(Request request, String contentType, byte[] payload) throws ParseException {
        if (contentType == null) {
            return;
        }
        String[] ct = contentType.split("/", 2);
        ContentTypeHeader contentTypeHeader = getStackBean().getHeaderFactory().createContentTypeHeader(ct[0], ct[1]);
        if (contentTypeHeader != null) {
            request.setContent(payload, contentTypeHeader);
        }
    }

    final public void addEventHeader(Request request, String eventType) throws ParseException {
        EventHeader eventHeader = getStackBean().getHeaderFactory().createEventHeader(eventType);
        request.addHeader(eventHeader);
    }

    final public void addHeader(Request request, String name, String value) throws ParseException {
        Header header = getStackBean().getHeaderFactory().createHeader(name, value);
        request.addHeader(header);
    }

    final public void setContent(Message message, SessionDescription sessionDescription) {
        try {
            ContentTypeHeader cth = getStackBean().getHeaderFactory().createContentTypeHeader(APPLICATION, SDP);
            String sd = sessionDescription.toString();
            message.setContent(sd, cth);
        } catch (ParseException ex) {
            throw new SipxRestException(ex);
        }

    }

    final public Response createResponse(Request request, int responseCode) throws ParseException {
        return getStackBean().getMessageFactory().createResponse(responseCode, request);
    }

    final public String getCSeqMethod(Response response) {
        return ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();
    }

    final public ReferToHeader createReferToHeader(String referToAddrSpec) {
        try {
            String referToUri = "sip:" + referToAddrSpec;
            SipURI sipUri = (SipURI) getStackBean().getAddressFactory().createURI(referToUri);
            Address address = getStackBean().getAddressFactory().createAddress(sipUri);
            ReferToHeader referToHeader = getStackBean().getHeaderFactory().createReferToHeader(address);
            return referToHeader;
        } catch (ParseException ex) {
            logger.error("Unexpected exception creating header",ex);
            throw new SipxRestException(ex);
        }
    }

    final public void tearDownDialog(Dialog dialog) {
        logger.debug("Tearinging Down Dialog : " + dialog);
        if (dialog == null) {
            return;
        }
        try {
            if (dialog.getState() == DialogState.CONFIRMED) {
                Request request = dialog.createRequest(Request.BYE);
                ClientTransaction ctx = this.getNewClientTransaction(request);
                dialog.sendRequest(ctx);
            } else if (dialog.getState() != DialogState.TERMINATED) {
                dialog.delete();
            }
        } catch (SipException e) {
            logger.error("Unexpected exception sending BYE", e);
        }
    }

    final public ContentTypeHeader createContentTypeHeader() throws ParseException {
        return getStackBean().getHeaderFactory().createContentTypeHeader(APPLICATION, SDP);
    }

    final public ReferredByHeader createReferredByHeader(String addrSpec) throws ParseException {
        String referredByUri = "sip:" + addrSpec;
        Address address = getStackBean().getAddressFactory().createAddress(getStackBean().getAddressFactory().createURI(referredByUri));
        return ((HeaderFactoryImpl)getStackBean().getHeaderFactory()).createReferredByHeader(address);
    }

    final public ClientTransaction handleChallenge(Response response, ClientTransaction clientTransaction) {
        if (getStackBean().getAuthenticationHelper() == null) {
            return null;
        }
        try {
            return getStackBean().getAuthenticationHelper().handleChallenge(response, clientTransaction, getSipProvider(), 5);
        } catch (SipException e) {
            throw new SipxRestException(e);
        }
    }


    final public void attachAllowHeader(Request request, List<String> methods) {
        try {
            for (String method: methods) {
                AllowHeader allowHeader = getStackBean().getHeaderFactory().createAllowHeader(method);
                request.addHeader(allowHeader);
            }
        } catch (Exception ex) {
            logger.error("Could not attach allow header", ex);
            throw new SipxRestException ("could not attach allow headers", ex);
        }
    }
    
    final public String getSipDomain(String uri) throws Exception {
        SipURI sipUri = (SipURI) getStackBean().getAddressFactory().createURI(uri);
        return sipUri.getHost();    
    }
    
    final CallIdHeader getNewCallId() {
        return this.getSipProvider().getNewCallId();
    }

    final public Request createRequest(String requestType, String userName, String fromDisplayName,
            String fromAddrSpec, String addrSpec, boolean forwardingAllowed) throws ParseException {
       
        logger.debug(String.format("requestType = %s userName = %s fromDisplayName = %s " +
                "fromAddrSpec = %s addSpec = %s , forwardingAllowed = %s",
                requestType,userName,fromDisplayName,fromAddrSpec,addrSpec,forwardingAllowed));
        
        SipURI fromUri = (SipURI) getStackBean().getAddressFactory().createURI("sip:"+fromAddrSpec);
      
        try {
            FromHeader fromHeader = createFromHeader(fromDisplayName, fromUri);
            SipURI toURI = (SipURI)getStackBean().getAddressFactory().createURI("sip:"+addrSpec);
            ToHeader toHeader = createToHeader(toURI);
            SipURI requestURI = (SipURI) getStackBean().getAddressFactory().createURI("sip:"+addrSpec);
            // limit forwarding so will not route to voicemail.
            requestURI.setParameter("sipx-noroute", "Voicemail");
            // This is disabled for click to dial but enabled for conference.
            if (!forwardingAllowed) {
                requestURI.setParameter("sipx-userforward", "false");
            }
            // Dont play MOH if configured on bridge. This is relevant for INVITE to conference.
            // MOH will still play fine on the click to dial case if so configured. This is only
            // for
            // Preventing MOH playing when we transfer to conference bridge.
            MaxForwardsHeader maxForwards = getStackBean().getHeaderFactory().createMaxForwardsHeader(m_maxForwards);
            ViaHeader viaHeader = createViaHeader();
            ContactHeader contactHeader = createContactHeader();
            CallIdHeader callIdHeader = getNewCallId();

            CSeqHeader cSeqHeader = getStackBean().getHeaderFactory().createCSeqHeader(1L, requestType);
            Request request = getStackBean().getMessageFactory().createRequest(requestURI, requestType, callIdHeader, cSeqHeader,
                    fromHeader, toHeader, Collections.singletonList(viaHeader), maxForwards);

            // Set loose routing to the target.
            Hop hop =  new FindSipServer(logger).findServer(requestURI);
            SipURI sipUri = getStackBean().getAddressFactory().createSipURI(null, hop.getHost());
            sipUri.setPort(hop.getPort());
            sipUri.setLrParam();
            Address address = getStackBean().getAddressFactory().createAddress(sipUri);

            RouteHeader routeHeader = getStackBean().getHeaderFactory().createRouteHeader(address);
            request.setHeader(routeHeader);
            request.addHeader(contactHeader);
            return request;
        } catch (InvalidArgumentException e) {
            throw new SipxRestException(e);
        }
    }
    
     public static String getToTag(Message message) {
        return ((ToHeader)message.getHeader(ToHeader.NAME)).getTag();
     }

     public static String getCallId(Message message) {
        return ((CallIdHeader) message.getHeader(CallIdHeader.NAME)).getCallId();
     }

     public static String getFromTag(Message message) {
        return((FromHeader)message.getHeader(FromHeader.NAME)).getTag();
     }


    /**
     * @return the m_stackBean
     */
    public SipStackBean getStackBean() {
        return RestServer.getSipStack();
     
    }

}
