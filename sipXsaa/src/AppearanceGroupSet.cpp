//
// Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
// $$
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES
// APPLICATION INCLUDES

#include "AppearanceGroupSet.h"
#include "AppearanceGroup.h"
#include "Appearance.h"
#include "ResourceNotifyReceiver.h"
#include "ResourceSubscriptionReceiver.h"
#include "ResourceListMsg.h"
#include <os/OsSysLog.h>
#include <os/OsLock.h>
#include <os/OsEventMsg.h>
#include <utl/UtlSListIterator.h>
#include <net/SipDialogEvent.h>

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS

// Resubscription period.
#define RESUBSCRIBE_PERIOD 3600

// STATIC VARIABLE INITIALIZATIONS
const UtlContainableType AppearanceGroupSet::TYPE = "AppearanceGroupSet";


/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ CREATORS ================================== */

// Constructor
AppearanceGroupSet::AppearanceGroupSet(AppearanceAgent* appearanceAgent) :
   mAppearanceAgent(appearanceAgent),
   mSemaphore(OsBSem::Q_PRIORITY, OsBSem::FULL),
   mVersion(0)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet:: this = %p",
                 this);
}

// Destructor
AppearanceGroupSet::~AppearanceGroupSet()
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::~ this = %p",
                 this);
}


/* ============================ MANIPULATORS ============================== */

// Create and add an Appearance Group.
void AppearanceGroupSet::addAppearanceGroup(const char* user)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::addAppearanceGroup this = %p, user = '%s'",
                 this, user);

   // Serialize access to the AppearanceGroupSet.
   OsLock lock(mSemaphore);

   // Check to see if there is already a group with this name.
   if (!findAppearanceGroup(user))
   {
      // Create the appearance group.
      AppearanceGroup* appearanceGroup = new AppearanceGroup(this, user/*, userCons*/);

      // Add the appearance group to the set.
      mAppearanceGroups.append(appearanceGroup);

      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::addAppearanceGroup added AppearanceGroup '%s', mVersion = %d",
                    user, mVersion);
   }
   else
   {
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::addAppearanceGroup AppearanceGroup '%s' already exists",
                    user);
   }
}

// Remove an Appearance Group.
void AppearanceGroupSet::removeAppearanceGroup(const char* user)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::removeAppearanceGroup this = %p, user = '%s'",
                 this, user);

   // Serialize access to the AppearanceGroupSet.
   OsLock lock(mSemaphore);

   // Check to see if there is a group with this name.
   AppearanceGroup* ag;
   if (!(ag = findAppearanceGroup(user)))
   {
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::removeAppearanceGroup  AppearanceGroup '%s' does not exist",
                    user);
   }
   else
   {
      mAppearanceGroups.removeReference(ag);
      delete ag;
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::removeAppearanceGroup removed AppearanceGroup '%s'",
                    user);
   }
}

// Delete all Appearance Groups.
void AppearanceGroupSet::deleteAllAppearanceGroups()
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::deleteAllAppearanceGroups this = %p",
                 this);

   // Gradually remove elements from the AppearanceGroups and delete them.
   AppearanceGroup* ag;
   int changeDelay = getAppearanceAgent()->getChangeDelay();
   do
   {
      // Serialize access to the AppearanceGroupSet.
      OsLock lock(mSemaphore);

      // Get pointer to the first AppearanceGroup.
      ag = dynamic_cast <AppearanceGroup*> (mAppearanceGroups.first());

      if (ag)
      {
         mAppearanceGroups.removeReference(ag);
         delete ag;
      }

      // Delay to allow the consequent processing to catch up.
      OsTask::delay(changeDelay);
   } while (ag);
}

// Get a list of all Appearance Groups.
void AppearanceGroupSet::getAllAppearanceGroups(UtlSList& list)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::getAllAppearanceGroups this = %p",
                 this);

   // Serialize access to the AppearanceGroupSet.
   OsLock lock(mSemaphore);

   // Iterate through the Appearance Groups.
   UtlSListIterator appearanceGroupItor(mAppearanceGroups);
   AppearanceGroup* appearanceGroup;
   while ((appearanceGroup = dynamic_cast <AppearanceGroup*> (appearanceGroupItor())))
   {
      list.append(new UtlString(appearanceGroup->getUser()));
   }
}

// Callback routine for subscription state events.
// Called as a callback routine.
void AppearanceGroupSet::subscriptionEventCallbackAsync(
   SipSubscribeClient::SubscriptionState newState,
   const char* earlyDialogHandle,
   const char* dialogHandle,
   void* applicationData,
   int responseCode,
   const char* responseText,
   long expiration,
   const SipMessage* subscribeResponse
   )
{
   // earlyDialogHandle may be NULL for some termination callbacks.
   if (!earlyDialogHandle)
   {
      earlyDialogHandle = "";
   }
   // dialogHandle may be NULL for some termination callbacks.
   if (!dialogHandle)
   {
      dialogHandle = "";
   }
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::subscriptionEventCallbackAsync newState = %d, applicationData = %p, earlyDialogHandle = '%s', dialogHandle = '%s'",
                 newState, applicationData, earlyDialogHandle, dialogHandle);

   // The AppearanceGroupSet concerned is applicationData.
   AppearanceGroupSet* appearanceGroupSet = (AppearanceGroupSet*) applicationData;

   // Determine the subscription state.
   // Currently, this is only "active" or "terminated", which is not
   // very good.  But the real subscription state is carried in the
   // NOTIFY requests. :TODO: Handle subscription set correctly.
   const char* subscription_state;
   if (subscribeResponse)
   {
      int expires;
      subscribeResponse->getExpiresField(&expires);
      subscription_state = expires == 0 ? "terminated" : "active";
   }
   else
   {
      subscription_state = "active";
   }

   // Send a message to the AppearanceGroupTask.
   SubscriptionCallbackMsg msg(earlyDialogHandle, dialogHandle,
                               newState, subscription_state);
   appearanceGroupSet->getAppearanceAgent()->getAppearanceAgentTask().
      postMessage(msg);
}

// Callback routine for subscription state events.
// Called by AppearanceGroupTask.
void AppearanceGroupSet::subscriptionEventCallbackSync(
   const UtlString* earlyDialogHandle,
   const UtlString* dialogHandle,
   SipSubscribeClient::SubscriptionState newState,
   const UtlString* subscriptionState
   )
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::subscriptionEventCallbackSync earlyDialogHandle = '%s', dialogHandle = '%s', newState = %d, subscriptionState = '%s'",
                 earlyDialogHandle->data(), dialogHandle->data(), newState,
                 subscriptionState->data());

   // Serialize access to the appearance group set.
   OsLock lock(mSemaphore);

   // Look up the ResourceSubscriptionReceiver to notify based on the
   // earlyDialogHandle.
   /* To call the handler, we dynamic_cast the object to
    * (ResourceSubscriptionReceiver*).  Whether this is strictly
    * conformant C++ I'm not sure, since UtlContainable and
    * ResourceSubscriptionReceiver are not base/derived classes of
    * each other.  But it seems to work in GCC as long as the dynamic
    * type of the object is a subclass of both UtlContainable and
    * ResourceSubscriptionReceiver.
    */
   ResourceSubscriptionReceiver* receiver =
      dynamic_cast <ResourceSubscriptionReceiver*>
         (mSubscribeMap.findValue(earlyDialogHandle));

   if (receiver)
   {
      receiver->subscriptionEventCallback(earlyDialogHandle,
                                          dialogHandle,
                                          newState,
                                          subscriptionState);
   }
   else
   {
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::subscriptionEventCallbackSync this = %p, no ResourceSubscriptionReceiver found for earlyDialogHandle '%s'",
                    this, earlyDialogHandle->data());
   }
}

// Callback routine for NOTIFY events.
// Called as a callback routine.
void AppearanceGroupSet::notifyEventCallbackAsync(const char* earlyDialogHandle,
                                               const char* dialogHandle,
                                               void* applicationData,
                                               const SipMessage* notifyRequest)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::notifyEventCallbackAsync applicationData = %p, earlyDialogHandle = '%s', dialogHandle = '%s'",
                 applicationData, earlyDialogHandle, dialogHandle);

   // The AppearanceGroupSet concerned is applicationData.
   AppearanceGroupSet* appearanceGroupSet = (AppearanceGroupSet*) applicationData;

   // Get the NOTIFY content.
   const char* b;
   ssize_t l;
   const HttpBody* body = notifyRequest->getBody();
   if (body)
   {
      body->getBytes(&b, &l);
   }
   else
   {
      b = NULL;
      l = 0;
   }

   // Send a message to the AppearanceGroupTask.
   NotifyCallbackMsg msg(dialogHandle, b, l);
   appearanceGroupSet->getAppearanceAgent()->getAppearanceAgentTask().
      postMessage(msg);
}

// Callback routine for NOTIFY events.
// Called by AppearanceGroupTask.
void AppearanceGroupSet::notifyEventCallbackSync(const UtlString* dialogHandle,
                                              const UtlString* content)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::notifyEventCallbackSync dialogHandle = '%s'",
                 dialogHandle->data());

   // Serialize access to the appearance group set.
   OsLock lock(mSemaphore);

   // Look up the ResourceNotifyReceiver to notify based on the dialogHandle.
   /* To call the handler, we dynamic_cast the object to
    * (ResourceNotifyReceiver*).  Whether this is strictly
    * conformant C++ I'm not sure, since UtlContainanble and
    * ResourceNotifyReceiver are not base/derived classes of
    * each other.  But it seems to work in GCC as long as the dynamic
    * type of the object is a subclass of both UtlContainable and
    * ResourceNotifyReceiver.
    */
   ResourceNotifyReceiver* receiver =
      dynamic_cast <ResourceNotifyReceiver*>
         (mNotifyMap.findValue(dialogHandle));

   if (receiver)
   {
      receiver->notifyEventCallback(dialogHandle, content);
   }
   else
   {
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "AppearanceGroupSet::notifyEventCallbackSync this = %p, no ResourceNotifyReceiver found for dialogHandle '%s'",
                    this, dialogHandle->data());
   }
}

/** Add a mapping for an early dialog handle to its handler for
 *  subscription events.
 */
void AppearanceGroupSet::addSubscribeMapping(UtlString* earlyDialogHandle,
                                          UtlContainable* handler)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::addSubscribeMapping this = %p, earlyDialogHandle = '%s', handler = %p",
                 this, earlyDialogHandle->data(), handler);

   mSubscribeMap.insertKeyAndValue(earlyDialogHandle, handler);
}

/** Delete a mapping for an early dialog handle.
 */
void AppearanceGroupSet::deleteSubscribeMapping(UtlString* earlyDialogHandle)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::deleteSubscribeMapping this = %p, earlyDialogHandle = '%s'",
                 this, earlyDialogHandle->data());

   mSubscribeMap.remove(earlyDialogHandle);
}

/** Add a mapping for a dialog handle to its handler for
 *  NOTIFY events.
 */
void AppearanceGroupSet::addNotifyMapping(UtlString* dialogHandle,
                                       UtlContainable* handler)
{
   /* The machinery surrounding dialog handles is broken in that it
    * does not keep straight which tag is local and which is remote,
    * and the dialogHandle for notifyEventCallback has the tags
    * reversed relative to the tags in the dialogHandle for
    * subscriptionEventCallback.  So we reverse the tags in dialogHandle
    * before inserting it into mNotifyMap, to match what
    * notifyEventCallback will receive.  Yuck.  Ideally, we would fix
    * the problem (XSL-146), but there are many places in the code
    * where it is sloppy about tracking whether a message is incoming
    * or outgoing when constructing a dialogHandle, and this is
    * circumvented by making the lookup of dialogs by dialogHandle
    * insensitive to reversing the tags.  (See SipDialog::isSameDialog.)
    */
   /* Correction:  Sometimes the NOTIFY tags are reversed, and
    * sometimes they aren't.  So we have to file both handles in
    * mNotifyMap.  Yuck.
    */
   mNotifyMap.insertKeyAndValue(dialogHandle, handler);

   UtlString* swappedDialogHandleP = new UtlString;
   swapTags(*dialogHandle, *swappedDialogHandleP);

   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::addNotifyMapping this = %p, dialogHandle = '%s', swappedDialogHandle = '%s', handler = %p",
                 this, dialogHandle->data(), swappedDialogHandleP->data(),
                 handler);

   // Note that we have allocated *swappedDialogHandleP.  Our caller
   // owns *dialogHandle and will deallocate it after calling
   // deleteNotifyMapping, but deleteNotify must remember to deallocate
   // *swappedDialogHandleP, the key object in mNotifyMap.  Yuck.
   mNotifyMap.insertKeyAndValue(swappedDialogHandleP, handler);
}

/** Delete a mapping for a dialog handle.
 */
void AppearanceGroupSet::deleteNotifyMapping(const UtlString* dialogHandle)
{
   // See comment in addNotifyMapping for why we do this.
   UtlString swappedDialogHandle;

   swapTags(*dialogHandle, swappedDialogHandle);

   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "AppearanceGroupSet::deleteNotifyMapping this = %p, dialogHandle = '%s', swappedDialogHandle = '%s'",
                 this, dialogHandle->data(), swappedDialogHandle.data());

   // Delete the un-swapped mapping.
   mNotifyMap.remove(dialogHandle);

   // Have to get a pointer to the key object, as our caller won't
   // free it.  Otherwise, we could just do "mNotifyMap.remove(dialogHandle)".
   UtlContainable* value;
   UtlContainable* keyString = mNotifyMap.removeKeyAndValue(&swappedDialogHandle, value);
   if (keyString)
   {
      // Delete the key object.
      delete keyString;
   }
}


/* ============================ ACCESSORS ================================= */

/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ INQUIRY =================================== */

/**
 * Get the ContainableType for a UtlContainable-derived class.
 */
UtlContainableType AppearanceGroupSet::getContainableType() const
{
   return AppearanceGroupSet::TYPE;
}

// Dump the object's internal state.
void AppearanceGroupSet::dumpState()
{
   // indented 2
   OsSysLog::add(FAC_SAA, PRI_INFO,
                 "\t  AppearanceGroupSet %p", this);
   UtlSListIterator appearanceGroupItor(mAppearanceGroups);
   AppearanceGroup* appearanceGroup;
   while ((appearanceGroup = dynamic_cast <AppearanceGroup*> (appearanceGroupItor())))
   {
      appearanceGroup->dumpState();
   }
}

/* //////////////////////////// PROTECTED ///////////////////////////////// */

// Search for an Appearance Group with a given uri.
AppearanceGroup* AppearanceGroupSet::findAppearanceGroup(const char* user)
{
   AppearanceGroup* ret = 0;

   UtlSListIterator appearanceGroupItor(mAppearanceGroups);
   AppearanceGroup* appearanceGroup;
   while (!ret &&
          (appearanceGroup = dynamic_cast <AppearanceGroup*> (appearanceGroupItor())))
   {
      if (appearanceGroup->getUser().compareTo(user) == 0)
      {
         ret = appearanceGroup;
      }
   }

   return ret;
}

/* //////////////////////////// PRIVATE /////////////////////////////////// */


/* ============================ FUNCTIONS ================================= */

// Swap the tags in a dialog handle.
void AppearanceGroupSet::swapTags(const UtlString& dialogHandle,
                               UtlString& swappedDialogHandle)
{
   // Find the commas in the dialogHandle.
   ssize_t index1 = dialogHandle.index(',');
   ssize_t index2 = dialogHandle.index(',', index1+1);

   // Copy the call-Id and the first comma.
   swappedDialogHandle.remove(0);
   swappedDialogHandle.append(dialogHandle,
                              index1+1);

   // Copy the second tag.
   swappedDialogHandle.append(dialogHandle,
                              index2+1,
                              dialogHandle.length() - (index2+1));

   // Copy the first comma and the first tag.
   swappedDialogHandle.append(dialogHandle,
                              index1,
                              index2-index1);
}
