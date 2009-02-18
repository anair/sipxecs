// 
// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////

#ifndef _SipSubscriptionMgr_h_
#define _SipSubscriptionMgr_h_

// SYSTEM INCLUDES

// APPLICATION INCLUDES

#include <os/OsDefs.h>
#include <os/OsMsgQ.h>
#include <os/OsMutex.h>
#include <utl/UtlDefs.h>
#include <utl/UtlHashMap.h>
#include <utl/UtlHashBag.h>
#include <net/SipDialogMgr.h>
#include <net/SipSubscribeServerEventHandler.h>

// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// FORWARD DECLARATIONS
class SipMessage;
class UtlString;
class SipDialogMgr;

// TYPEDEFS

//! Class for maintaining SUBSCRIBE dialog information in subscription server
/*! 
 *
 * \par 
 */
class SipSubscriptionMgr
{
/* //////////////////////////// PUBLIC //////////////////////////////////// */
public:

/* ============================ CREATORS ================================== */

    //! Default constructor
    SipSubscriptionMgr();

    //! Destructor
    virtual
    ~SipSubscriptionMgr();

/* ============================ MANIPULATORS ============================== */

    //! Add/Update subscription for the given SUBSCRIBE request
    /** The resourceId, eventTypeKey, and eventType are set based on
     *  the subscription.  If the subscription already existed, they
     *  are extracted from the subscription state.  If this is a new
     *  subscription, handler.getKeys() is called to compute them.
     */
    virtual UtlBoolean updateDialogInfo(/// the incoming SUBSCRIBE request
                                        const SipMessage& subscribeRequest,
                                        /// the canonical URI of the resource
                                        UtlString& resourceId,
                                        /// the event type key for the events
                                        UtlString& eventTypeKey,
                                        /// the event type for the events
                                        UtlString& eventType,
                                        /// the dialog handle for the subscription (out)
                                        UtlString& subscribeDialogHandle,
                                        /// TRUE if the subscription is new (out)
                                        UtlBoolean& isNew,
                                        /// TRUE if the subscription is not ongoing (out)
                                        UtlBoolean& isExpired,
                                        /** response for the SUBSCRIBE (out)
                                         *  Any errors will be logged into
                                         *  subscribeResponse.
                                         */
                                        SipMessage& subscribeResponse,
                                        /** Event-specific handler, which provides
                                         *  handler.getKeys().
                                         */
                                        SipSubscribeServerEventHandler& handler
       );
    
    //! Insert subscription dialog info without checking for the existence of the dialog
    /*! This method blindly inserts dialog information and should only be called from
     *  from the SipPersistentSubscriptionMgr.  It is intended to insert subscription
     *  information into memory from the IMDB.  (Thus, e.g., it does not randomize
     *  subscription times.)
     */ 
    virtual UtlBoolean insertDialogInfo(/// the incoming SUBSCRIBE request
                                        const SipMessage& subscribeRequest,
                                        /// the canonical URI of the resource
                                        const UtlString& resourceId,
                                        /// the event type key for the events
                                        const UtlString& eventTypeKey,
                                        /// the dialog handle for the subscription (out)
                                        UtlString& subscribeDialogHandle,
                                        /// TRUE if the subscription is new (out)
                                        UtlBoolean& isNew,
                                        /// TRUE if the subscription is not ongoing (out)
                                        UtlBoolean& isExpired
       );

    //! Set the subscription dialog information and cseq for the next NOTIFY request
    virtual UtlBoolean getNotifyDialogInfo(const UtlString& subscribeDialogHandle,
                                           SipMessage& notifyRequest);

    //! Construct a NOTIFY request for each subscription/dialog subscribed to the given resourceId and eventTypeKey
    /*! Allocates a SipMessage* array and allocates a SipMessage and sets the
     * dialog information for the NOTIFY request for each subscription.
     *  \param numNotifiesCreated - number of pointers to NOTIFY requests in
     *         the returned notifyArray
     *  \param notifyArray - if numNotifiesCreated > 0 array is allocated of
     *         sufficient size to hold a SipMessage for each subscription.
     */
    virtual UtlBoolean createNotifiesDialogInfo(const char* resourceId,
                                                const char* eventTypeKey,
                                                int& numNotifiesCreated,
                                                UtlString**& acceptHeaderValuesArray,
                                                SipMessage**& notifyArray);

    //! frees up the notifies created in createNotifiesDialogInfo
    virtual void freeNotifies(int numNotifies,
                              UtlString** acceptHeaderValues,
                              SipMessage** notifiesArray);

    //! End the dialog for the subscription indicated, by the dialog handle
    /*! Finds a matching dialog and expires the subscription if it has
     *  not already expired.
     *  \param dialogHandle - a fully established SIP dialog handle
     *  Returns TRUE if a matching dialog was found regardless of
     *  whether the subscription was already expired or not.
     */
    virtual UtlBoolean endSubscription(const UtlString& dialogHandle);

    //! Remove old subscriptions that expired before given date
    virtual void removeOldSubscriptions(long oldEpochTimeSeconds);

    //! Set stored value for the next NOTIFY CSeq and version.
    virtual void setNextNotifyCSeq(const UtlString& dialogHandleString,
                                   int nextLocalCseq,
                                   int version);

   /** Update the saved value of the NOTIFY CSeq (now in notifyRequest) and
    *  the XML version (as specified).
    */
   virtual void updateVersion(SipMessage& notifyRequest,
                              int version);

/* ============================ ACCESSORS ================================= */


    //! Get the dialog manager
    /*! WARNING: the application must be aware of the lifetime of
     *  the dialog manager, as no reference counting is done on
     *  the dialog manager.  The application is responsible for
     *  knowing when the dialog manager will go way.
     */
    SipDialogMgr* getDialogMgr();

/* ============================ INQUIRY =================================== */

    //! inquire if the dialog exists
    virtual UtlBoolean dialogExists(UtlString& dialogHandle);

    //! inquire if the dialog has already expired
    virtual UtlBoolean isExpired(UtlString& dialogHandle);

    /** get the next notify body "version" value that is allowed
     *  for a resource (as far as is known by this SipSubscriptionMgr).
     *  If no information is available, returns 0.
     */
    virtual int getNextAllowedVersion(const UtlString& resourceId);

    //! Dump the object's internal state.
    void dumpState();

/* //////////////////////////// PROTECTED ///////////////////////////////// */
protected:

/* //////////////////////////// PRIVATE /////////////////////////////////// */
private:
    //! Copy constructor NOT ALLOWED
    SipSubscriptionMgr(const SipSubscriptionMgr& rSipSubscriptionMgr);

    //! Assignment operator NOT ALLOWED
    SipSubscriptionMgr& operator=(const SipSubscriptionMgr& rhs);

    //! lock for single thread use
    void lock();

    //! unlock for use
    void unlock();

    int mEstablishedDialogCount;
    OsMutex mSubscriptionMgrMutex;
    SipDialogMgr mDialogMgr;
    int mMinExpiration;
    int mDefaultExpiration;
    int mMaxExpiration;

    // Container for the SubscriptionServerState's, which are indexed
    // by dialog handles.
    UtlHashBag mSubscriptionStatesByDialogHandle;

    // Index to subscription states in mSubscriptionStatesByDialogHandle
    // indexed by the resourceId and eventTypeKey
    // Members are SubscriptionServerStateIndex's, which are indexed by
    // resourceId concatenated with eventTypeKey.
    UtlHashBag mSubscriptionStateResourceIndex;
};

/* ============================ INLINE METHODS ============================ */

#endif  // _SipSubscriptionMgr_h_
