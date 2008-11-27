// 
// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
// $$
//////////////////////////////////////////////////////////////////////////////

#ifndef _ResourceListServer_h_
#define _ResourceListServer_h_

// SYSTEM INCLUDES
// APPLICATION INCLUDES

#include <utl/UtlContainableAtomic.h>
#include <utl/UtlString.h>
#include <utl/UtlSList.h>
#include <net/SipPublishContentMgr.h>
#include <net/SipSubscribeClient.h>
#include <net/SipSubscribeServer.h>
#include <net/SipDialogMgr.h>
#include <net/SipUserAgent.h>
#include <persist/SipPersistentSubscriptionMgr.h>
#include <os/OsBSem.h>
#include <os/OsTime.h>
#include "RlsSubscribePolicy.h"
#include "ResourceListTask.h"
#include "ResourceListFileReader.h"
#include "ResourceListSet.h"

// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS
// FORWARD DECLARATIONS


/**
 * This class holds the machinery for generating resource list events,
 * as described in RFC 4662.  It contains a ResourceListSet, which holds
 * the list of URIs and the content returned by subscriptions to those URIs.
 * It contains numerous SIP processing objects to handle SIP requests.
 * It contains a ResourceListTask, which receives timer messages and executes
 * the appropriate changes on the ResourceListSet.
 */

class ResourceListServer : public UtlContainableAtomic
{
/* //////////////////////////// PUBLIC //////////////////////////////////// */
  public:

   //! Construct a resource list server.
   ResourceListServer(/** The host-part of the canonical form of the resource list
                        *  URIs, which is the sipX domain. */
                      const UtlString& domainName,
                      /// The realm used for authentication
                      const UtlString& realm,
                      /// Container for user definitions and their credentials.
                      SipLineMgr* lineMgr,
                      /// The event-type to handle.
                      const char* eventType,
                      /// The content-type to handle.
                      const char* contentType,
                      /// The TCP port to listen on.
                      int tcpPort,
                      /// The UDP port to listen on.
                      int udpPort,
                      /// The TLS port to listen on.
                      int tlsPort,
                      /// IP address to bind on.
                      const UtlString& bindIp,
                      /// The file describing the resource lists.
                      UtlString* resourceListFile,
                      /// The refresh interval.
                      int refreshInterval,
                      /// The resubscribe interval.
                      int resubscribeInterval,
                      /// The minimum value for resubcribe interval
                      int minResubscribeInterval,
                      /// Publishing delay, in msec.
                      int publishingDelay,
                      /// The maximum number of reg subscriptions per resource.
                      int maxRegSubscInResource,
                      /// The maximum number of contacts per reg subscription.
                      int maxContInRegSubsc,
                      /// The maximum number of resource instances per contact.
                      int maxResInstInCont,
                      /// The maximum number of dialogs per resource instance.
                      int maxDialogsInResInst,
                      /// Name of the subscription DB to use (for testing purposes)
                      const UtlString&  subscriptionDbName = "subscription",
                      /// Name of the credentials DB to use (for testing purposes)
                      const UtlString&  credentialsDbName = "credential"
      );

   virtual ~ResourceListServer();

   //! Shut down the call processing components.
   void shutdown();

   //! Refresh the subscriptions of all resources in all resource lists.
   void refreshAllResources();

   //! Create and add a resource list.
   void addResourceList(/// The user-part of the resource list URI for "full" events.
                        const char* user,
                        /// The user-part of the resource list URI for "consolidated" events.
                        const char* userCons,
                        /// The XML for the name of the resource list.
                        const char* nameXml);

   //! Delete all resources from a resource list.
   void deleteAllResources(/// The user-part of the resource list URI.
                           const char* user);

   //! Get a list of the user-parts of all resource lists.
   void getAllResourceLists(/// The list to add the user-parts to.
                            UtlSList& list);

   //! Create and add a resource to a resource list.
   void addResource(/// The user-part of the resource list URI.
                    const char* user,
                    /// The resource URI.
                    const char* URI,
                    /// The XML for the name of the resource.
                    const char* nameXml,
                    /// The display name for consolidated event notices
                    const char* display_name);

   //! Dump the object's internal state.
   void dumpState();

   //! Get the event type.
   // Return value is valid as long as the ResourceListServer exists.
   const char* getEventType() const;

   //! Get the content type.
   // Return value is valid as long as the ResourceListServer exists.
   const char* getContentType() const;

   //! Get the canonical SIP domain name.
   // Return value is valid as long as the ResourceListServer exists.
   const char* getDomainName() const;

   //! Get the server local host-part.
   // Return value is valid as long as the ResourceListServer exists.
   const char* getServerLocalHostPart() const;
 
   //! Get the From URI to be used for SUBSCRIBEs.
   const char* getClientFromURI() const;

   //! Get the Contact URI to be used for SUBSCRIBEs.
   const char* getClientContactURI() const;

   //! Get the event publisher.
   SipPublishContentMgr& getEventPublisher();

   //! Get the subscribe client.
   SipSubscribeClient& getSubscribeClient();

   // Get the subscribe server manager.
   SipSubscriptionMgr& getSubscriptionMgr();

   //! Get the ResourceListTask.
   ResourceListTask& getResourceListTask();

   //! Get the ResourceListSet.
   ResourceListSet& getResourceListSet();

   //! Get the ResourceListFileReader.
   ResourceListFileReader& getResourceListFileReader();

   //! Get the refresh interval.
   int getRefreshInterval() const;

   //! Get the resubscribe interval.
   int getResubscribeInterval() const;

   //! Get the publishing delay.
   const OsTime& getPublishingDelay() const;

   //! Get the maximum number of reg subscriptions allwed for a resource.
   int getMaxRegSubscInResource() const;

   //! Get the maximum number of contacts allowed for a reg subscription.
   int getMaxContInRegSubsc() const;

   //! Get the maximum number of resource instances (subscriptions) allowed for a contact.
   int getMaxResInstInCont() const;

   //! Get the maximum number of dialogs allowed for a resource instance.
   int getMaxDialogsInResInst() const;

   //! Get the server user agent.
   SipUserAgent& getServerUserAgent();

   /**
    * Get the ContainableType for a UtlContainable-derived class.
    */
   virtual UtlContainableType getContainableType() const;

   static const UtlContainableType TYPE;    /** < Class type used for runtime checking */

/* //////////////////////////// PROTECTED ///////////////////////////////// */
  protected:
   
/* //////////////////////////// PRIVATE /////////////////////////////////// */
  private:

   //! SIP domain name.
   UtlString mDomainName;
   //! The server local host-part.
   UtlString mServerLocalHostPart;
   //! Event type.
   UtlString mEventType;
   //! Content type.
   UtlString mContentType;
   //! From URI for mClientSipUserAgent.
   UtlString mClientFromURI;
   //! Contact URI for mClientSipUserAgent.
   UtlString mClientContactURI;
   //! Refresh interval for reinitializing connection to resource URIs, in seconds..
   int mRefreshInterval;
   //! Resubscription interval for subscriptions, in seconds.
   int mResubscribeInterval;
   //! Minimum resubscription interval for subscriptions, in seconds.
   int mMinResubscribeInterval;
   //! Interval to delay after a content change before publishing, in msec.
   OsTime mPublishingDelay;
   //! The maximum number of reg subscriptions allwed for a resource.
   int mMaxRegSubscInResource;
   //! The maximum number of contacts allowed for a reg subscription.
   int mMaxContInRegSubsc;
   //! The maximum number of resource instances (subscriptions) allowed for a contact.
   int mMaxResInstInCont;
   //! The maximum number of dialogs allowed for a resource instance.
   int mMaxDialogsInResInst;

   // The call processing objects.

   //! The SipUserAgent for receiving subscriptions.
   SipUserAgent mServerUserAgent;

   //! The SipUserAgent for making subscriptions.
   //  We have two different user agents so that mClientUserAgent does not
   //  advertise "Supported: eventlist".  This prevents users from inserting
   //  resource list URIs into resource lists.
   SipUserAgent mClientUserAgent;

   //! The SipPublishContentMgr.
   // This will contain the event content for every resource list URI
   // and event type that the RLS services.
   SipPublishContentMgr mEventPublisher;

   //! Component for holding the subscription data.
   SipPersistentSubscriptionMgr mSubscriptionMgr; 

   //! Component for granting subscription rights
   RlsSubscribePolicy mPolicyHolder;

   //! The SIP Subscribe Server.
   SipSubscribeServer mSubscribeServer;

   //! Support objects for SipSubscribeClient.
   SipDialogMgr mDialogManager;
   SipRefreshManager mRefreshMgr;

   //! The SipSubscribeClient for subscriptions we make to resources.
   SipSubscribeClient mSubscribeClient;

   //! The ResourceListTask, which processes asynchronous events.
   ResourceListTask mResourceListTask;

   //! The ResourceListSet.
   ResourceListSet mResourceListSet;

   //! The ResourceListFileReader.
   ResourceListFileReader mResourceListFileReader;

   //! Disabled copy constructor
   ResourceListServer(const ResourceListServer& rResourceListServer);

   //! Disabled assignment operator
   ResourceListServer& operator=(const ResourceListServer& rhs);

   friend class ResourceListServerTest;
};

/* ============================ INLINE METHODS ============================ */

// Refresh the subscriptions of all resources in all resource lists.
inline void ResourceListServer::refreshAllResources()
{
   mResourceListSet.refreshAllResources();
}

// Create and add a resource list.
inline void ResourceListServer::addResourceList(const char* user,
                                                const char* userCons,
                                                const char* nameXml)
{
   mResourceListSet.addResourceList(user, userCons, nameXml);
}

// Delete all resources from a resource list.
inline void ResourceListServer::deleteAllResources(const char* user)
{
   mResourceListSet.deleteAllResources(user);
}

// Get a list of the user-parts of all resource lists.
inline void ResourceListServer::getAllResourceLists(UtlSList& list)
{
   mResourceListSet.getAllResourceLists(list);
}

// Create and add a resource to a resource list.
inline void ResourceListServer::addResource(const char* user,
                                            const char* URI,
                                            const char* nameXml,
                                            const char* display_name)
{
   mResourceListSet.addResource(user, URI, nameXml, display_name);
}

//! Get the event type.
inline const char* ResourceListServer::getEventType() const
{
   return mEventType.data();
}

//! Get the content type.
inline const char* ResourceListServer::getContentType() const
{
   return mContentType.data();
}

//! Get the SIP domain name.
inline const char* ResourceListServer::getDomainName() const
{
   return mDomainName.data();
}

// Get the server local host-part.
inline const char* ResourceListServer::getServerLocalHostPart() const
{
   return mServerLocalHostPart.data();
}

//! Get the client From URI.
inline const char* ResourceListServer::getClientFromURI() const
{
   return mClientFromURI.data();
}

//! Get the client Contact URI.
inline const char* ResourceListServer::getClientContactURI() const
{
   return mClientContactURI.data();
}

//! Get the event publisher.
inline SipPublishContentMgr& ResourceListServer::getEventPublisher()
{
   return mEventPublisher;
}

//! Get the subscribe client.
inline SipSubscribeClient& ResourceListServer::getSubscribeClient()
{
   return mSubscribeClient;
}

// Get the subscribe server manager.
inline SipSubscriptionMgr& ResourceListServer::getSubscriptionMgr()
{
   return mSubscriptionMgr;
}

// Get the ResourceListTask.
inline ResourceListTask& ResourceListServer::getResourceListTask()
{
   return mResourceListTask;
}

// Get the ResourceListSet.
inline ResourceListSet& ResourceListServer::getResourceListSet()
{
   return mResourceListSet;
}

// Get the ResourceListFileReader.
inline ResourceListFileReader& ResourceListServer::getResourceListFileReader()
{
   return mResourceListFileReader;
}

// Get the refresh interval.
inline int ResourceListServer::getRefreshInterval() const
{
   return mRefreshInterval;
}

// Get the resubscribe interval.
inline int ResourceListServer::getResubscribeInterval() const
{
   return ( ( rand() % (mResubscribeInterval - mMinResubscribeInterval) ) + mMinResubscribeInterval);
}

// Get the publishing delay.
inline const OsTime& ResourceListServer::getPublishingDelay() const
{
   return mPublishingDelay;
}

// Get the maximum number of reg subscriptions allwed for a resource.
inline int ResourceListServer::getMaxRegSubscInResource() const
{
   return mMaxRegSubscInResource;
}

// Get the maximum number of contacts allowed for a reg subscription.
inline int ResourceListServer::getMaxContInRegSubsc() const
{
   return mMaxContInRegSubsc;
}

// Get the maximum number of resource instances (subscriptions)
// allowed for a contact.
inline int ResourceListServer::getMaxResInstInCont() const
{
   return mMaxResInstInCont;
}

// Get the maximum number of dialogs allowed for a resource instance.
inline int ResourceListServer::getMaxDialogsInResInst() const
{
   return mMaxDialogsInResInst;
}

// Get the server user agent.
inline SipUserAgent& ResourceListServer::getServerUserAgent()
{
   return mServerUserAgent;
}

#endif  // _ResourceListServer_h_
