//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//
// $$
////////////////////////////////////////////////////////////////////////
//////

#include "SipSubscribeTestSupport.h"

#include <net/CallId.h>

/* Support routines for the SipSubscribeServer and SipSubscribeClient tests. */

// Create a SipUserAgent.
void createTestSipUserAgent(UtlString& hostIp,
                            const char* user,
                            SipUserAgent*& userAgentp,
                            UtlString& aor_addr_spec,
                            UtlString& aor_name_addr,
                            UtlString& aor_contact_name_addr,
                            UtlString& resource_id
                            )
{
   // Construct a user agent.
   // Listen on only a TCP port, as we do not want to have to specify a
   // fixed port number (to allow multiple executions of the test),
   // and the code to select a port automatically cannot be told to
   // open matching UDP and TCP ports.
   // defaultSipAddress is set to force the SipUserAgent to open
   // ports on that interface.
   // publicAddress is set to force the SipUserAgent to report
   // that as its address.
   userAgentp = new SipUserAgent(PORT_DEFAULT, PORT_NONE, PORT_NONE,
                                 hostIp, NULL, hostIp);
   userAgentp->start();

   // Construct the URI of the notifier, which is also the URI of
   // the subscriber.
   // Also construct the name-addr version of the URI, which may be
   // different if it has a "transport" parameter.
   // And the resource-id to use, which is the AOR with any
   // parameters stripped off.
   {
      char buffer[100];
      sprintf(buffer, "sip:%s@%s:%d", user, hostIp.data(),
              userAgentp->getTcpPort());
      resource_id = buffer;

      // Specify TCP transport, because that's all the UA listens to.
      // Using an address with a transport parameter also exercises
      // SipDialog to ensure it handles contact addresses with parameters.
      strcat(buffer, ";transport=tcp");
      aor_addr_spec = buffer;

      Url aor_uri(buffer, TRUE);
      aor_uri.toString(aor_name_addr);

      // aor_uri.setUserId(NULL);
      aor_uri.toString(aor_contact_name_addr);
   }

   // Start the SipUserAgent running.
   userAgentp->start();
}

// Service routine to listen for messages.
void runListener(OsMsgQ& msgQueue, //< OsMsgQ to listen on
                 SipUserAgent& userAgent, //< SipUserAgent to send responses
                 OsTime timeout, //< Length of time before timing out.
                 const SipMessage*& request, //< Pointer to any request that was received
                 const SipMessage*& response, //< Pointer to any response that was received
                 int responseCode, //< Response code to give to requests
                 UtlBoolean retry, //< TRUE to add "Retry-After: 0" to responsees
                 int expires, //< Expires value to add to SUBSCRIBE responses
                 UtlString* toTagp //< To-tag that was created or used for response, or NULL
   )
{
   // Initialize the request and response pointers.
   request = NULL;
   response = NULL;

   // Because the SUBSCRIBE response and NOTIFY request can come in either order,
   // we have to read messages until no more arrive.
   OsMsg* message;
   while (msgQueue.receive(message, timeout) == OS_SUCCESS)
   {
      int msgType = message->getMsgType();
      int msgSubType = message->getMsgSubType();
      assert(msgType == OsMsg::PHONE_APP);
      assert(msgSubType == SipMessage::NET_SIP_MESSAGE);
      const SipMessage* sipMessage = ((SipMessageEvent*) message)->getMessage();
      assert(sipMessage);
      int messageType = ((SipMessageEvent*) message)->getMessageStatus();
      assert(messageType == SipMessageEvent::APPLICATION);

      if (sipMessage->isResponse())
      {
         // Check that we get only one response.
         assert(response == NULL);
         response = sipMessage;
      }
      else
      {
         // Check that we get only one request.
         assert(request == NULL);
         request = sipMessage;
         // Immediately generate a response to the request
         SipMessage requestResponse;
         requestResponse.setResponseData(request,
                                         responseCode,
                                         // Provide dummy response text
                                         "dummy");

         if (toTagp)
         {
            // Set or get the to-tag in the response.
            Url toUrl;
            requestResponse.getToUrl(toUrl);
            toUrl.getFieldParameter("tag", *toTagp);
            if (toTagp->isNull())
            {
               CallId::getNewTag(*toTagp);
               requestResponse.setToFieldTag(*toTagp);
            }
         }

         // Set "Retry-After: 0" if requested.
         if (retry)
         {
            requestResponse.setHeaderValue(SIP_RETRY_AFTER_FIELD, "0", 0);
         }

         // Set Expires header if the request is SUBSCRIBE.
         UtlString method;
         sipMessage->getRequestMethod(&method);
         if (method.compareTo(SIP_SUBSCRIBE_METHOD) == 0)
         {
            requestResponse.setExpiresField(expires);
         }

         assert(userAgent.send(requestResponse));
      }
   }
}
