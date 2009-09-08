package org.sipfoundry.commons.jainsip;

import gov.nist.javax.sip.ClientTransactionExt;
import gov.nist.javax.sip.ListeningPointExt;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.TransactionExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.SecureAccountManager;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.MessageFactoryExt;
import gov.nist.javax.sip.message.SIPRequest;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Timer;

import javax.sip.ClientTransaction;
import javax.sip.ListeningPoint;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.sipfoundry.commons.log4j.ServerLoggerImpl;
import org.sipfoundry.commons.log4j.StackLoggerImpl;


/**
 * 
 * Abstract class that encapsulates a SIP stack. This initilizes the stack.
 * Extend this class to effortlessy construct a SIP  stack with all the trappings we
 * need. 
 */

public abstract class AbstractSipStackBean {

    private static final Logger logger = Logger.getLogger(AbstractSipStackBean.class);

    private Properties m_properties;

    private AddressFactory m_addressFactory;

    private HeaderFactory m_headerFactory;

    private MessageFactory m_messageFactory; 

    private SipStack m_sipStack;

    private AuthenticationHelper m_authenticationHelper = null;
    
    private HashSet<SipProvider> sipProviders = new HashSet<SipProvider>();


    /**
     * Initialized stack: binds to a port. It should be called before any other operation on the
     * stack.
     */
    public void init() throws Exception {
        SipFactory factory = SipFactory.getInstance();
        factory.setPathName("gov.nist");
       
        m_properties = new Properties();
       
        // add more properties here if needed
        m_properties.setProperty("javax.sip.STACK_NAME", getStackName() );
        m_properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "1");
        m_properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", Boolean.TRUE.toString());
        m_properties.setProperty("javax.sip.ROUTER_PATH",
                org.sipfoundry.commons.siprouter.ProxyRouter.class.getName());
        m_properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", StackLoggerImpl.class.getName());
        m_properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER", ServerLoggerImpl.class.getName());
       
        String logLevel =  getLogLevel();
        Logger serverLogger = Logger.getLogger(StackLoggerImpl.class);
        serverLogger.addAppender(getStackAppender());
        
        m_properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL",logLevel);
      
        if (!logLevel.equalsIgnoreCase("TRACE")) {
            if (logLevel.equalsIgnoreCase("DEBUG")) {
                serverLogger.setLevel(Level.INFO);
                m_properties.setProperty(
                        "gov.nist.javax.sip.LOG_STACK_TRACE_ON_MESSAGE_SEND", "true");
            } else {
                serverLogger.setLevel(Level.toLevel(logLevel));
                m_properties.setProperty(
                        "gov.nist.javax.sip.LOG_STACK_TRACE_ON_MESSAGE_SEND", "false");
            }

        } else {
            serverLogger.setLevel(Level.DEBUG);
           
        }
       
        try {
            m_sipStack = factory.createSipStack(m_properties);
           
           
            SipStackImpl stack = (SipStackImpl) m_sipStack;
            m_addressFactory = factory.createAddressFactory();
            m_headerFactory = factory.createHeaderFactory();
            m_messageFactory = factory.createMessageFactory();
            RouteList.setPrettyEncode(true);
            ViaList.setPrettyEncode(true);
            
            for ( ListeningPointAddress hpt : this.getListeningPointAddresses()) {
              ListeningPoint listeningPoint = stack.createListeningPoint(hpt.getHost(), hpt.getPort(), hpt.getTransport());
              hpt.listeningPoint = listeningPoint;
              SipProvider sipProvider = stack.createSipProvider(listeningPoint);
              hpt.sipProvider = sipProvider;
              if (!this.sipProviders.contains(sipProvider)) {
                  this.sipProviders.add(sipProvider); 
                  sipProvider.addSipListener(getSipListener(this));
              }
            
            }
          
            SipStackExt impl = (SipStackExt) stack;
            if ( getHashedPasswordAccountManager() != null ) {
                m_authenticationHelper = impl.getSecureAuthenticationHelper(getHashedPasswordAccountManager(), m_headerFactory);
            } else if ( getPlainTextPasswordAccountManager() != null ) {
                m_authenticationHelper = impl.getAuthenticationHelper(getPlainTextPasswordAccountManager(), m_headerFactory);
            }
            
        } catch (Exception e) {
            throw new SipxSipException("JainSip initialization exception", e);
        }
    }
   
   
    public ClientTransaction handleChallenge(Response response, ClientTransaction tid) throws SipException    {
    
        SipProvider sipProvider = ((TransactionExt)tid).getSipProvider();
        ClientTransaction ctx =  m_authenticationHelper.handleChallenge(response, tid,
                sipProvider, 1800);
        ctx.sendRequest();
        ctx.setApplicationData(tid.getApplicationData());
        return ctx;
    }
    
  
    public HeaderFactory getHeaderFactory() {
        return m_headerFactory;
    }
    
    public MessageFactory getMessageFactory() {
        return m_messageFactory;
    }
    
    public AddressFactory getAddressFactory() {
        return m_addressFactory;
    }
    
  
    public SipStackExt getSipStack() {
        return (SipStackExt) this.m_sipStack;
    }
    
    public ListeningPoint getListeningPoint(ListeningPointAddress hostPortTransport) {
        return hostPortTransport.listeningPoint;
    }
    
    public AuthenticationHelper getAuthenticationHelper() {
        return m_authenticationHelper;
    }

    public abstract String getLogLevel();
    /**
     * @return Secure Account manager or null if hashed passwords are not supported.
     */
    public abstract SecureAccountManager getHashedPasswordAccountManager(); 
    
    /**
     * 
     * @return plain text password account manager or null if plain text passwords
     * not supported. Note that only one type of account is supported.
     */
    public abstract AccountManager getPlainTextPasswordAccountManager();
    public abstract SipListener getSipListener(AbstractSipStackBean abstactSipStackBean);
    public abstract String getStackName() ;    
    public abstract Appender getStackAppender() ;
    public abstract Collection<ListeningPointAddress> getListeningPointAddresses();
    
}
