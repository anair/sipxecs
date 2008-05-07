// 
// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
// $$
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES

// APPLICATION INCLUDES
#include <os/OsFS.h>
#include <os/OsSysLog.h>
#include <os/OsConfigDb.h>
#include <utl/UtlHashMapIterator.h>
#include <net/SipResourceList.h>
#include <net/NetMd5Codec.h>
#include <net/SipMessage.h>
#include <cp/SipPresenceMonitor.h>
#include <cp/XmlRpcSignIn.h>
#include <mi/CpMediaInterfaceFactoryFactory.h>

#ifndef EXCLUDE_STREAMING
#include <mp/MpMediaTask.h>
#include <mp/NetInTask.h>
#endif

#ifdef INCLUDE_RTCP
#include <rtcp/RTCManager.h>
#endif // INCLUDE_RTCP

// DEFINES
#define RTP_START_PORT          12000    // Starting RTP port
#define MAX_CONNECTIONS         200     // Max number of sim. conns

#define CONFIG_SETTING_HTTP_PORT              "SIP_PRESENCE_HTTP_PORT"
#define PRESENCE_DEFAULT_HTTP_PORT            8111

// The presence status we attribute to resources that we have no
// information about.
#define DEFAULT_PRESENCE_STATUS STATUS_CLOSED

// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS
// FORWARD DECLARATIONS
// STATIC VARIABLE INITIALIZATIONS

// Objects to construct default content for presence events.

class PresenceDefaultConstructor : public SipPublishContentMgrDefaultConstructor
{
  public:

   /** Generate the content for a resource and event.
    */
   virtual void generateDefaultContent(SipPublishContentMgr* contentMgr,
                                       const char* resourceId,
                                       const char* eventTypeKey,
                                       const char* eventType);

   /// Make a copy of this object according to its real type.
   virtual SipPublishContentMgrDefaultConstructor* copy();

   // Service routine for UtlContainable.
   virtual const char* const getContainableType() const;

protected:
   static UtlContainableType TYPE;    /** < Class type used for runtime checking */
};

// Static identifier for the type.
const UtlContainableType PresenceDefaultConstructor::TYPE = "PresenceDefaultConstructor";

// Generate the default content for presence status.
void PresenceDefaultConstructor::generateDefaultContent(SipPublishContentMgr* contentMgr,
							const char* resourceId,
							const char* eventTypeKey,
							const char* eventType)
{
   OsSysLog::add(FAC_SIP, PRI_DEBUG,
                 "PresenceDefaultConstructor::generateDefaultContent "
                 "generating default content for resourceId '%s', "
                 "eventTypeKey '%s', eventType '%s'",
                 resourceId, eventTypeKey, eventType);

   // Create a presence event package and store it in the publisher.
   // This code parallels SipPresenceMonitor::setStatus.
   SipPresenceEvent* sipPresenceEvent = new SipPresenceEvent(resourceId);
      
   UtlString id;
   NetMd5Codec::encode(resourceId, id);
   
   Tuple* tuple = new Tuple(id.data());
   tuple->setStatus(DEFAULT_PRESENCE_STATUS);
   tuple->setContact(resourceId, 1.0);
   
   sipPresenceEvent->insertTuple(tuple); 

   // Build its text version.
   int version;
   sipPresenceEvent->buildBody(version);

   // Publish the event (storing it for the resource), but set
   // noNotify to TRUE, because our caller will push the NOTIFYs.
   contentMgr->publish(resourceId, eventTypeKey, eventType, 1,
                       &(HttpBody*&)sipPresenceEvent, &version, TRUE);
}

// Make a copy of this object according to its real type.
SipPublishContentMgrDefaultConstructor* PresenceDefaultConstructor::copy()
{
   // Copying these objects is easy, since they have no member variables, etc.
   return new PresenceDefaultConstructor;
}

// Get the ContainableType for a UtlContainable derived class.
UtlContainableType PresenceDefaultConstructor::getContainableType() const
{
    return PresenceDefaultConstructor::TYPE;
}


// Constructor
SipPresenceMonitor::SipPresenceMonitor(SipUserAgent* userAgent,
                                       SipSubscriptionMgr* subscriptionMgr,
                                       UtlString& domainName,
                                       int hostPort,
                                       OsConfigDb* configFile,
                                       bool toBePublished)
   : mLock(OsBSem::Q_PRIORITY, OsBSem::FULL),
     mpSubscriptionMgr(subscriptionMgr)

{
   mpUserAgent = userAgent;
   mDomainName = domainName;
   mToBePublished = toBePublished;
   
   char buffer[80];
   sprintf(buffer, "@%s:%d", mDomainName.data(), hostPort);
   mHostAndPort = UtlString(buffer);

   UtlString localAddress;
   OsSocket::getHostIp(&localAddress);

   OsConfigDb configDb;
   configDb.set("PHONESET_MAX_ACTIVE_CALLS_ALLOWED", 2*MAX_CONNECTIONS);

#ifdef INCLUDE_RTCP
   CRTCManager::getRTCPControl();
#endif //INCLUDE_RTCP
   mpStartTasks();

   // Instantiate the call processing subsystem
   mpCallManager = new CallManager(FALSE,
                                   NULL,
                                   TRUE,                              // early media in 180 ringing
                                   &mCodecFactory,
                                   RTP_START_PORT,                    // rtp start
                                   RTP_START_PORT + (2*MAX_CONNECTIONS), // rtp end
                                   localAddress,
                                   localAddress,
                                   mpUserAgent, 
                                   0,                                 // sipSessionReinviteTimer
                                   NULL,                              // mgcpStackTask
                                   NULL,                              // defaultCallExtension
                                   Connection::RING,                  // availableBehavior
                                   NULL,                              // unconditionalForwardUrl
                                   -1,                                // forwardOnNoAnswerSeconds
                                   NULL,                              // forwardOnNoAnswerUrl
                                   Connection::BUSY,                  // busyBehavior
                                   NULL,                              // sipForwardOnBusyUrl
                                   NULL,                              // speedNums
                                   CallManager::SIP_CALL,             // phonesetOutgoingCallProtocol
                                   4,                                 // numDialPlanDigits
                                   CallManager::NEAR_END_HOLD,        // holdType
                                   5000,                              // offeringDelay
                                   "",                                // pLocal
                                   CP_MAXIMUM_RINGING_EXPIRE_SECONDS, // inviteExpiresSeconds
                                   QOS_LAYER3_LOW_DELAY_IP_TOS,       // expeditedIpTos
                                   MAX_CONNECTIONS,                   // maxCalls
                                   sipXmediaFactoryFactory(&configDb));    // CpMediaInterfaceFactory

   mpDialInServer = new PresenceDialInServer(mpCallManager, configFile);    
   mpCallManager->addTaoListener(mpDialInServer);
   mpDialInServer->start();

   // Start the call processing system
   mpCallManager->start();
      
   // Add self to the presence dial-in server for state change notification
   mpDialInServer->addStateChangeNotifier("Presence_Dial_In_Server", this);

   if (mToBePublished)
   {
      // Create the SIP Subscribe Server
      mpSubscribeServer = new SipSubscribeServer(*mpUserAgent, mSipPublishContentMgr,
                                                 *mpSubscriptionMgr, mPolicyHolder);
      // Arrange to generate default content for presence events.
      mSipPublishContentMgr.publishDefault(PRESENCE_EVENT_TYPE, PRESENCE_EVENT_TYPE,
                                           new PresenceDefaultConstructor);
      mpSubscribeServer->enableEventType(PRESENCE_EVENT_TYPE);
      mpSubscribeServer->start();
   }
   
   // Enable the xmlrpc sign-in/sign-out
   int HttpPort;
   if (configDb.get(CONFIG_SETTING_HTTP_PORT, HttpPort) != OS_SUCCESS)
   {
      HttpPort = PRESENCE_DEFAULT_HTTP_PORT;
   }
   
   mpXmlRpcSignIn = new XmlRpcSignIn(this, HttpPort);   
}

// Destructor
SipPresenceMonitor::~SipPresenceMonitor()
{
   // Remove itself from the presence dial-in server
   mpDialInServer->removeStateChangeNotifier("Presence_Dial_In_Server");

   if (mpSubscriptionMgr)
   {
      delete mpSubscriptionMgr;
   }
   
   if (mpSubscribeServer)
   {
      delete mpSubscribeServer;
   }
   
   if (!mMonitoredLists.isEmpty())
   {
      mMonitoredLists.destroyAll();
   }

   if (!mPresenceEventList.isEmpty())
   {
      mPresenceEventList.destroyAll();
   }

   if (!mStateChangeNotifiers.isEmpty())
   {
      mStateChangeNotifiers.destroyAll();
   }
   
   if (mpXmlRpcSignIn)
   {
      delete mpXmlRpcSignIn;
   }   
}


bool SipPresenceMonitor::addExtension(UtlString& groupName, Url& contactUrl)
{
   bool result = false;
   mLock.acquire();
   
   // Check whether the group has already existed. If not, create one.
   SipResourceList* list = dynamic_cast <SipResourceList *> (mMonitoredLists.findValue(&groupName));
   if (list == NULL)
   {
      UtlString* listName = new UtlString(groupName);
      list = new SipResourceList((UtlBoolean)TRUE, listName->data(), PRESENCE_EVENT_TYPE);
      
      mMonitoredLists.insertKeyAndValue(listName, list);
      OsSysLog::add(FAC_SIP, PRI_DEBUG, "SipPresenceMonitor::addExtension insert listName %s and object %p to the resource list",
                    groupName.data(), list);   
   }

   // Check whether the contact has already being added to the group
   UtlString resourceId;
   contactUrl.getIdentity(resourceId);
   Resource* resource = list->getResource(resourceId);
   if (resource == NULL)
   {
      resource = new Resource(resourceId);
      
      UtlString userName;
      contactUrl.getDisplayName(userName);
      resource->setName(userName);
      
      UtlString id;
      NetMd5Codec::encode(resourceId, id);
      resource->setInstance(id, STATE_PENDIND);
      list->insertResource(resource);
      
      result = true;
   }
   else
   {
      OsSysLog::add(FAC_LOG, PRI_WARNING,
                    "SipPresenceMonitor::addExtension contact %s already exists.",
                    resourceId.data());
   }

   int dummy;
   list->buildBody(dummy);
   
   mLock.release();
   return result;
}

bool SipPresenceMonitor::removeExtension(UtlString& groupName, Url& contactUrl)
{
   bool result = false;
   mLock.acquire();
   // Check whether the group has existed or not. If not, return false.
   SipResourceList* list = dynamic_cast <SipResourceList *> (mMonitoredLists.findValue(&groupName));
   if (list == NULL)
   {
      OsSysLog::add(FAC_SIP, PRI_DEBUG, "SipPresenceMonitor::removeExtension group %s does not exist",
                    groupName.data());   
   }
   else
   {
      // Check whether the contact has existed or not
      UtlString resourceId;
      contactUrl.getIdentity(resourceId);
      Resource* resource = list->getResource(resourceId);
      if (resource)
      {
         resource = list->removeResource(resource);
         delete resource;
         
         result = true;
      }
      else
      {
         OsSysLog::add(FAC_LOG, PRI_WARNING,
                       "SipPresenceMonitor::removeExtension subscription for contact %s does not exists.",
                       resourceId.data());
      }
   }

   mLock.release();   
   return result;   
}

bool SipPresenceMonitor::addPresenceEvent(UtlString& contact, SipPresenceEvent* presenceEvent)
{
   bool requiredPublish = false;
   
   if (mPresenceEventList.find(&contact) == NULL)
   {
      requiredPublish = true;
      OsSysLog::add(FAC_SIP, PRI_DEBUG, "SipPresenceMonitor::addPresenceEvent adding the presenceEvent %p for contact %s",
                    presenceEvent, contact.data());
   }
   else
   {
      OsSysLog::add(FAC_SIP, PRI_DEBUG, "SipPresenceMonitor::addPresenceEvent presenceEvent %p for contact %s already exists, just update the content.",
                    presenceEvent, contact.data());
                    
      // Get the object from the presence event list
      UtlContainable* oldKey;
      UtlContainable* foundValue;
      foundValue = mPresenceEventList.findValue(&contact);
      SipPresenceEvent* oldPresenceEvent = dynamic_cast <SipPresenceEvent *> (foundValue);
      UtlString oldStatus, status;
      UtlString id;
      NetMd5Codec::encode(contact, id);
      oldPresenceEvent->getTuple(id)->getStatus(oldStatus);
      presenceEvent->getTuple(id)->getStatus(status);
      
      if (status.compareTo(oldStatus) != 0)
      {
         requiredPublish = true;

         // Since we will be saving a new value, remove the old one.
         oldKey = mPresenceEventList.removeKeyAndValue(&contact, foundValue);
         delete oldKey;
         if (oldPresenceEvent)
         {
            delete oldPresenceEvent;
         }
      }
   }

   if (requiredPublish)
   {         
      // Insert it into the presence event list
      int dummy;
      presenceEvent->buildBody(dummy);
      mPresenceEventList.insertKeyAndValue(new UtlString(contact), presenceEvent);

      if (mToBePublished)
      { 
         // Publish the content to the resource list
         publishContent(contact, presenceEvent);
      }
      
      // Notify the state change
      notifyStateChange(contact, presenceEvent);
   }
   else
   {
      // Since this presenceEvent will not be published (it does not
      // change the state we've sent out), delete it now.
      delete presenceEvent;
   }
   
   return requiredPublish;
}


void SipPresenceMonitor::publishContent(UtlString& contact, SipPresenceEvent* presenceEvent)
{
#ifdef SUPPORT_RESOURCE_LIST
   // Loop through all the resource lists
   UtlHashMapIterator iterator(mMonitoredLists);
   UtlString* listUri;
   SipResourceList* list;
   Resource* resource;
   UtlString id, state;
   while (listUri = dynamic_cast <UtlString *> (iterator()))
   {
      bool contentChanged = false;
      int version;

      list = dynamic_cast <SipResourceList *> (mMonitoredLists.findValue(listUri));
      OsSysLog::add(FAC_SIP, PRI_DEBUG, "SipPresenceMonitor::publishContent listUri %s list %p",
                    listUri->data(), list); 

      // Search for the contact in this list
      resource = list->getResource(contact);
      if (resource)
      {
         resource->getInstance(id, state);
         
         if (presenceEvent->isEmpty())
         {
            resource->setInstance(id, STATE_TERMINATED);
         }
         else
         {
            UtlString id;
            NetMd5Codec::encode(contact, id);
            Tuple* tuple = presenceEvent->getTuple(id);
            
            UtlString status;
            tuple->getStatus(status);
            
            if (status.compareTo(STATUS_CLOSED) == 0)
            {
               resource->setInstance(id, STATE_TERMINATED);
            }
            else
            {     
               resource->setInstance(id, STATE_ACTIVE);
            }
         }
         
         list->buildBody(version);
         contentChanged = true;
      }

      if (contentChanged)
      {
         // Publish the content to the subscribe server
         // Make a copy, because mpSipPublishContentMgr will own it.
         HttpBody* pHttpBody = new HttpBody(*(HttpBody*)list);
         mSipPublishContentMgr.publish(listUri->data(),
                                       PRESENCE_EVENT_TYPE, PRESENCE_EVENT_TYPE,
                                       1, &pHttpBody, &version);
      }
   }
#endif

   // Publish the content to the subscribe server
   // Make a copy, because mpSipPublishContentMgr will own it.
   HttpBody* pHttpBody = new HttpBody(*(HttpBody*)presenceEvent);
   int version = 0;             // Presence events do not have versions.
   mSipPublishContentMgr.publish(contact.data(),
                                 PRESENCE_EVENT_TYPE, PRESENCE_EVENT_TYPE,
                                 1, &pHttpBody, &version);
}


void SipPresenceMonitor::addStateChangeNotifier(const char* fileUrl, StateChangeNotifier* notifier)
{
   mLock.acquire();
   UtlString* name = new UtlString(fileUrl);
   UtlVoidPtr* value = new UtlVoidPtr(notifier);
   mStateChangeNotifiers.insertKeyAndValue(name, value);
   mLock.release();
}

void SipPresenceMonitor::removeStateChangeNotifier(const char* fileUrl)
{
   mLock.acquire();
   UtlString name(fileUrl);
   mStateChangeNotifiers.destroy(&name);
   mLock.release();
}

void SipPresenceMonitor::notifyStateChange(UtlString& contact, SipPresenceEvent* presenceEvent)
{

   // Loop through the notifier list
   UtlHashMapIterator iterator(mStateChangeNotifiers);
   UtlString* listUri;
   StateChangeNotifier* notifier;
   Url contactUrl(contact);
   mLock.acquire();
   while ((listUri = dynamic_cast <UtlString *> (iterator())))
   {
      notifier = dynamic_cast <StateChangeNotifier *> (mStateChangeNotifiers.findValue(listUri));

      if (presenceEvent->isEmpty())
      {
         notifier->setStatus(contactUrl, StateChangeNotifier::AWAY);
      }
      else
      {
         UtlString id;
         NetMd5Codec::encode(contact, id);
         Tuple* tuple = presenceEvent->getTuple(id);
            
         UtlString status;
         tuple->getStatus(status);
            
         if (status.compareTo(STATUS_CLOSED) == 0)
         {
            notifier->setStatus(contactUrl, StateChangeNotifier::AWAY);
         }
         else
         {     
            notifier->setStatus(contactUrl, StateChangeNotifier::PRESENT);
         }
      }
   }
   mLock.release();
}

bool SipPresenceMonitor::setStatus(const Url& aor, const Status value)
{
   if (OsSysLog::willLog(FAC_SIP, PRI_DEBUG))
   {
      UtlString aorString;
      aor.toString(aorString);
      OsSysLog::add(FAC_SIP, PRI_DEBUG,
                    "SipPresenceMonitor::setStatus aor = '%s', value = %d %s",
                    aorString.data(), value,
                    (value == StateChangeNotifier::PRESENT ? "PRESENT" :
                     value == StateChangeNotifier::AWAY ? "AWAY" :
                     "UNKNOWN"));
   }

   bool result = false;
   
   UtlString contact;
   aor.getUserId(contact);
   contact += mHostAndPort;
   // Make the contact be a proper URI by prepending "sip:".
   contact.prepend("sip:");
   
   // Create a presence event package and store it in the publisher
   SipPresenceEvent* sipPresenceEvent = new SipPresenceEvent(contact);
      
   UtlString id;
   NetMd5Codec::encode(contact, id);
   
   Tuple* tuple = new Tuple(id.data());
   
   if (value == StateChangeNotifier::PRESENT)
   {
      tuple->setStatus(STATUS_OPEN);
      tuple->setContact(contact, 1.0);
   }
   else if (value == StateChangeNotifier::AWAY)
   {
      tuple->setStatus(STATUS_CLOSED);
      tuple->setContact(contact, 1.0);
   }

   sipPresenceEvent->insertTuple(tuple); 
   
   // Add the SipPresenceEvent object to the subscribe list
   result = addPresenceEvent(contact, sipPresenceEvent);
   
   return result;
}


void SipPresenceMonitor::getState(const Url& aor, UtlString& status)
{
   UtlString contact;
   aor.getUserId(contact);
   contact += mHostAndPort;
   // Make the contact be a proper URI by prepending "sip:".
   contact.prepend("sip:");

   UtlContainable* foundValue;
   foundValue = mPresenceEventList.findValue(&contact);
   
   if (foundValue)
   {
      SipPresenceEvent* presenceEvent = dynamic_cast <SipPresenceEvent *> (foundValue);
      UtlString id;
      NetMd5Codec::encode(contact, id);
      presenceEvent->getTuple(id)->getStatus(status);
      OsSysLog::add(FAC_SIP, PRI_ERR, "SipPresenceMonitor::getState contact %s state = %s",
                    contact.data(), status.data());
   }
   else
   {
      OsSysLog::add(FAC_SIP, PRI_ERR, "SipPresenceMonitor::getState contact %s does not exist",
                    contact.data());
                    
      status = STATUS_CLOSED;
   }   
}
