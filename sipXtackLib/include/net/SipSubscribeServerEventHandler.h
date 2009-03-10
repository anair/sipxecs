// 
// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////

#ifndef _SipSubscribeServerEventHandler_h_
#define _SipSubscribeServerEventHandler_h_

// SYSTEM INCLUDES

// APPLICATION INCLUDES

#include <os/OsDefs.h>
#include <os/OsMutex.h>
#include <utl/UtlDefs.h>
#include <utl/UtlHashMap.h>


// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// FORWARD DECLARATIONS
class SipMessage;
class UtlString;
class SipPublishContentMgr;

// TYPEDEFS

//! Class for specializing the handling of SIP Events in SipSubscribeServer
/*! This class provides the default behavior for SIP event packages
 *  handled by SipSubscribeServer.  Event packages which wish to change
 *  or extend the default behavior should create a subclass of this class and
 *  override the behavior of methods implemented by this class.
 *
 * \par 
 */
class SipSubscribeServerEventHandler
{
/* //////////////////////////// PUBLIC //////////////////////////////////// */
public:



/* ============================ CREATORS ================================== */

    //! Default Dialog constructor
    SipSubscribeServerEventHandler();


    //! Destructor
    virtual
    ~SipSubscribeServerEventHandler();


/* ============================ MANIPULATORS ============================== */

    /** Determine what the resourceId, eventTypeKey, and eventType
     *  should be for a SUBSCRIBE request that creates a subscription.
     *  The default behavior is to use the identify from the request URI
     *  as the resourceId and the event type token from the Event header
     *  as the eventTypeKey.  Some event packages may wish to override
     *  this (e.g. the host part of the resourceID, which will usually be an
     *  IP address, may make sense in some cases  to be substituted with the 
     *  domain name.  In some event packages, the content of the event state
     *  information will vary based upon some Event header parameters, in
     *  which cases it may make sense to include that event header parameter
     *  in a consistant order and format in the eventTypeKey.)
     */
    virtual UtlBoolean getKeys(const SipMessage& subscribeRequest,
                               UtlString& resourceId,
                               UtlString& eventTypeKey,
                               UtlString& eventType);

    //! Determine if the given SUBSCRIBE request is authenticated to subscribe
    /*! Default behavior is to not require any authentication.
     *  Note that if isAuthenticated returns false, it must construct a suitable
     *  failure response in subscribeResponse.  (If it returns true, it should
     *  not modify subscribeResponse.)
     */
    virtual UtlBoolean isAuthenticated(const SipMessage& subscribeRequest,
                                       SipMessage& subscribeResponse);

    //! Determine if the given SUBSCRIBE request is authorized to subscribe
    /*! Default behavior is to allow any request to subscribe
     *  Note that if isAuthorized returns false, it must construct a suitable
     *  failure response in subscribeResponse.  (If it returns true, it should
     *  not modify subscribeResponse.)
     */
    virtual UtlBoolean isAuthorized(const SipMessage& subscribeRequest,
                                    SipMessage& subscribeResponse);

    /**! Fill in the event-specific content for the identified resource
     *   and eventTypeKey into notifyRequest.
     */
    /*! The default behavior is to attach the content yielded from 
     *  contentMgr->getContent.
     */
    virtual UtlBoolean getNotifyContent(const UtlString& resourceId,
                                        const UtlString& eventTypeKey,
                                        const UtlString& eventType,
                                        SipPublishContentMgr& contentMgr,
                                        const char* allowHeaderValue,
                                        SipMessage& notifyRequest,
                                        int& version,
                                        UtlBoolean fullState);

/* ============================ ACCESSORS ================================= */


/* ============================ INQUIRY =================================== */


/* //////////////////////////// PROTECTED ///////////////////////////////// */
protected:

/* //////////////////////////// PRIVATE /////////////////////////////////// */
private:
    //! Copy constructor NOT ALLOWED
    SipSubscribeServerEventHandler(const SipSubscribeServerEventHandler& rSipSubscribeServerEventHandler);

    //! Assignment operator NOT ALLOWED
    SipSubscribeServerEventHandler& operator=(const SipSubscribeServerEventHandler& rhs);

};

/* ============================ INLINE METHODS ============================ */

#endif  // _SipSubscribeServerEventHandler_h_
