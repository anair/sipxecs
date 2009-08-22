package org.sipfoundry.callcontroller;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import javax.sip.address.Hop;
import javax.sip.address.SipURI;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import org.restlet.Filter;
import org.restlet.Restlet;
import org.restlet.data.ChallengeRequest;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.sipfoundry.commons.siprouter.FindSipServer;
import org.sipfoundry.commons.userdb.User;

public class CallControllerFilter extends Filter {
    
    private static Logger logger = Logger.getLogger(CallControllerFilter.class);
    
   
    private CallControllerRestlet callController;

  
    public CallControllerFilter() {
    }
  
  
    
    @Override
    protected int beforeHandle(Request request, Response response) {
      String remoteAddr = request.getClientInfo().getAddress();  
      try {

      
          SipURI sipUri = SipStackBean.getInstance().getProxySipURI();
          
          logger.debug("Authentication request " + remoteAddr );

         
          if ( ! request.getProtocol().equals(Protocol.HTTPS) && CallController.isSecure) {
              logger.debug("Request was not recieved over HTTPS protocol");
              return Filter.STOP;
          }
          
          Collection<Hop> hops = new FindSipServer(logger).getSipxProxyAddresses(sipUri);
          
          for (Hop hop : hops) {
              if (InetAddress.getByName(hop.getHost()).getHostAddress().equals(remoteAddr)) {
                  logger.debug("Authenticated request from sipx domain");
                  return Filter.CONTINUE;
              }
          }
          
          
        
       
          String agentName = ((String) request.getAttributes().get(CallControllerParams.AGENT));
       
          
          logger.debug("AgentName = " + agentName);
          if ( agentName == null ) {
              agentName = (String)request.getAttributes().get(CallControllerParams.CALLING_PARTY);
          }

          User user = CallController.getAccountManager().getUser(agentName);
          
         
          if (user == null) {
              logger.debug("User not found");
              response.setEntity("User Not found " + agentName, MediaType.TEXT_PLAIN);
              response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
              return Filter.STOP;
          }
          
          ChallengeResponse challengeResponse = request.getChallengeResponse();
          
          String passWord = new String(challengeResponse.getSecret());
          
          if ( passWord == null ) {
              logger.debug("Requesting BASIC credentials");
              ChallengeRequest challengeRequest = new ChallengeRequest(ChallengeScheme.HTTP_BASIC,
                      CallController.getCallControllerConfig().getSipxProxyDomain());
              response.setChallengeRequest(challengeRequest);
              response.setStatus(Status.CLIENT_ERROR_PROXY_AUTHENTIFICATION_REQUIRED);
              return Filter.STOP;
          }


          
          String userName = user.getUserName();
          logger.debug("userName = " + userName);

          String userDomainPassword =  userName +":" + 
          CallController.getCallControllerConfig().getSipxProxyDomain() + ":" +
          passWord;

          String hashVal = DigestUtils.md5Hex(userDomainPassword);
          logger.debug("pintoken " + user.getPintoken());
          logger.debug("hashval "  + hashVal);
          if (user.getPintoken() == null || hashVal.equals(user.getPintoken())) {
              return Filter.CONTINUE;
          } else {
              logger.debug("User not authenticated - token mismatch ");
              response.setEntity("pin mismatch", MediaType.TEXT_PLAIN);
              response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
              return Filter.STOP;
          }
      } catch (Exception ex) {
          logger.error("Exception in processing request", ex);
          response.setEntity("Processing Error " + ex.getMessage(), MediaType.TEXT_PLAIN);
          response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
          return Filter.STOP;
      } 
    }
    
    
    public void setNext(CallControllerRestlet callControllerRestlet) {
        this.callController = callControllerRestlet;
    }
    @Override 
    public Restlet getNext() {
        return this.callController;
        
    }
    
    

}
