//
// Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
// $$
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES
// APPLICATION INCLUDES

#include "Appearance.h"
#include "AppearanceAgent.h"
#include "AppearanceGroup.h"
#include <os/OsSysLog.h>
#include <utl/UtlHashMapIterator.h>

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STATIC VARIABLE INITIALIZATIONS

const UtlContainableType Appearance::TYPE = "Appearance";


/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ CREATORS ================================== */

// Constructor
Appearance::Appearance( AppearanceAgent* appAgent,
                        AppearanceGroup* appGroup,
                                 UtlString& uri) :
//   ResourceSubscriptionReceiver(),
   mAppearanceAgent(appAgent),
   mAppearanceGroup(appGroup),
   mUri(uri),
   mbShortTimeout(false)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance:: this = %p, mUri = '%s'",
                 this, mUri.data());

   // Start the subscription for dialog events.
   UtlBoolean ret;
   UtlString uriNameAddr = "<" + mAppearanceGroup->getUser() + ">";
   ret = getAppearanceAgent()->getSubscribeClient().
      addSubscription(mUri.data(), // resourceId and ReqURI
                      SLA_EVENT_TYPE,
                      DIALOG_EVENT_CONTENT_TYPE,
                      uriNameAddr.data(),  // FromURI
                      uriNameAddr.data(),  // ToURI
                      getAppearanceAgent()->getServerContactURI(),
                      getAppearanceAgent()->getResubscribeInterval(),
                      &getAppearanceGroupSet(),
                      AppearanceGroupSet::subscriptionEventCallbackAsync,
                      AppearanceGroupSet::notifyEventCallbackAsync,
                      mSubscriptionEarlyDialogHandle);
   if (ret)
   {
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "Appearance:: addSubscription for '%s' succeeded",
                    mUri.data());
      // Add this Appearance to mSubscribeMap.
      getAppearanceGroupSet().addSubscribeMapping(&mSubscriptionEarlyDialogHandle,
                                                this);
   }
   else
   {
      OsSysLog::add(FAC_SAA, PRI_WARNING,
                    "Appearance:: addSubscription for '%s' failed",
                    mUri.data());
   }
}

// Destructor
Appearance::~Appearance()
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance::~ mUri = '%s'",
                 mUri.data());

   // Delete this Appearance from mSubscribeMap.
   getAppearanceGroupSet().deleteSubscribeMapping(&mSubscriptionEarlyDialogHandle);

   // Terminate the master subscription.
   UtlBoolean ret;
   ret = getAppearanceAgent()->getSubscribeClient().
      endSubscription(mSubscriptionEarlyDialogHandle.data());
   // endSubscription will cause a new SUBSCRIBE to be sent with Expires=0,
   // which will in turn cause an OK and an incoming NOTIFY.
   // Stick around a bit to allow these messages to be handled somewhat gracefully
   // (i.e. acknowledged).
   OsTask::delay(100);
   OsSysLog::add(FAC_SAA,
                 ret ? PRI_DEBUG : PRI_WARNING,
                 "Appearance::~ endSubscription %s mUri = '%s', mSubscriptionEarlyDialogHandle = '%s'",
                 ret ? "succeeded" : "failed",
                 mUri.data(),
                 mSubscriptionEarlyDialogHandle.data());
}

/* ============================ MANIPULATORS ============================== */

void Appearance::subscriptionEventCallback(
   const UtlString* earlyDialogHandle,
   const UtlString* dialogHandle,
   SipSubscribeClient::SubscriptionState newState,
   const UtlString* subscriptionState)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance::subscriptionEventCallback uri = '%s', newState = %d, earlyDialogHandle = '%s', dialogHandle = '%s', subscriptionState = '%s'",
                 mUri.data(), newState, mSubscriptionEarlyDialogHandle.data(),
                 dialogHandle->data(), subscriptionState->data());

   switch (newState)
   {
   case SipSubscribeClient::SUBSCRIPTION_INITIATED:
      break;
   case SipSubscribeClient::SUBSCRIPTION_SETUP:
   {
      // Put the subscription into pending state, as we have no
      // content for it yet.
   }
   break;
   case SipSubscribeClient::SUBSCRIPTION_TERMINATED:
   {
      bool bContentChanged = terminateDialogs(false); // terminate only non-held dialogs
      if ( bContentChanged)
      {
         SipDialogEvent* lPartialContent = new SipDialogEvent("partial", mUri.data());
         getDialogs(lPartialContent);
         getAppearanceGroup()->publish(true, true, lPartialContent);
         delete lPartialContent;
      }
   }
   break;
   }
}


void Appearance::getDialogs(SipDialogEvent *content)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance::getDialogs mUri = '%s': adding %zu dialogs",
                 mUri.data(), mDialogs.entries());
   UtlHashMapIterator itor(mDialogs);
   UtlString* handle;
   while ( (handle = dynamic_cast <UtlString*> (itor())) )
   {
      Dialog* pDialog = dynamic_cast <Dialog*> (itor.value());
      content->insertDialog(new Dialog(*pDialog));
   }
}


bool Appearance::terminateDialogs(bool terminateHeldDialogs)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance::terminateDialogs mUri = '%s': terminating %zu dialogs",
                 mUri.data(), mDialogs.entries());
   UtlHashMapIterator itor(mDialogs);
   UtlString* handle;
   bool ret = false;  // nothing changed
   while ( (handle = dynamic_cast <UtlString*> (itor())) )
   {
      Dialog* pDialog = dynamic_cast <Dialog*> (itor.value());
      UtlString rendering;
      UtlString dialogState= STATE_TERMINATED;
      UtlString event;
      UtlString code;
      pDialog->getLocalParameter("+sip.rendering", rendering);
      pDialog->getState(dialogState, event, code);
      // unless told otherwise, do not terminate held dialogs:
      // they can still be picked up by another set
      if ( terminateHeldDialogs ||
            !((dialogState == STATE_CONFIRMED) && (rendering == "no"))
         )
      {
         OsSysLog::add(FAC_SAA, PRI_DEBUG,
                       "Appearance::terminateDialogs dialog '%s'",
                       handle->data());
         pDialog->setState(STATE_TERMINATED, event, code);
         ret = true;
      }
   }
   return ret;
}


bool Appearance::updateState(SipDialogEvent *notifyDialogs, bool& bFullContentChanged)
{
   // mDialogs contains list of dialogs being actively managed at this set
   // i.e. trying, early, confirmed FROM this contact.
   bool ret = false;
   bFullContentChanged = false;

   if (notifyDialogs)
   {
      UtlString state;
      notifyDialogs->getState(state);
      // partial updates are always sent to all members of the group.
      // full updates are only sent if they contain new dialogs.
      if (state == STATE_PARTIAL)
      {
         ret = true;
      }
      UtlSListIterator* itor = notifyDialogs->getDialogIterator();
      Dialog* pNewDialog;
      while ( (pNewDialog= dynamic_cast <Dialog*> ((*itor)())))
      {
         UtlString appearanceId;
         UtlString rendering;
         UtlString dialogState= STATE_TERMINATED;
         UtlString event;
         UtlString code;
         pNewDialog->getLocalParameter("x-line-id", appearanceId);
         pNewDialog->getLocalParameter("+sip.rendering", rendering);
         pNewDialog->getState(dialogState, event, code);
         OsSysLog::add(FAC_SAA, PRI_DEBUG,
               "Appearance::updateState received %s update for: '%s', appearance '%s', state '%s', rendering '%s'",
               state.data(), mUri.data(), appearanceId.data(), dialogState.data(), rendering.data());

         // is this an update for an existing subscription, or new data?
         UtlString dialogId;
         UtlString uniqueDialogId;
         pNewDialog->getDialogId(uniqueDialogId);
         Dialog *pOldDialog = dynamic_cast <Dialog*> (mDialogs.findValue(&uniqueDialogId));
         if (pOldDialog)
         {
            // this is one of our identifiers already
            // is this dialog still being managed by us?
            // hang on to ones we put on hold...
            if ( dialogState == STATE_TERMINATED )
            {
               mDialogs.destroy(&uniqueDialogId);
               OsSysLog::add(FAC_SAA, PRI_DEBUG,
                     "Appearance::updateState removed dialog: '%s', %zu now in list",
                     uniqueDialogId.data(), mDialogs.entries());
               ret = true;
               bFullContentChanged = true;
            }
            else
            {
               UtlString oldRendering;
               UtlString oldDialogState = STATE_TERMINATED;
               pOldDialog->getLocalParameter("+sip.rendering", oldRendering);
               pOldDialog->getState(oldDialogState, event, code);
               if ( oldDialogState != dialogState || oldRendering != rendering)
               {
                  // replace the old dialog with this new one
                  mDialogs.destroy(&uniqueDialogId);
                  mDialogs.insertKeyAndValue(new UtlString(uniqueDialogId), new Dialog(*pNewDialog));
                  OsSysLog::add(FAC_SAA, PRI_DEBUG,
                        "Appearance::updateState updated dialog: '%s', %zu in list",
                        uniqueDialogId.data(), mDialogs.entries());
                  ret = true;
                  bFullContentChanged = true;
               }
            }
         }
         else
         {
            if ( dialogState == STATE_TERMINATED )
            {
               OsSysLog::add(FAC_SAA, PRI_DEBUG,
                     "Appearance::updateState ignoring terminated dialog: '%s', %zu in list",
                     uniqueDialogId.data(), mDialogs.entries());
            }
            else
            {
               // this is a new dialog, so add it to our list
               mDialogs.insertKeyAndValue(new UtlString(uniqueDialogId), new Dialog(*pNewDialog));
               OsSysLog::add(FAC_SAA, PRI_DEBUG,
                     "Appearance::updateState added dialog: '%s', %zu now in list",
                     uniqueDialogId.data(), mDialogs.entries());
               bFullContentChanged = true;
               ret = true;
            }
         }
      }
      delete itor;
      if (state == STATE_FULL)
      {
         // check to see that we don't have any dialogs hanging around which aren't in their list
         UtlHashMapIterator itor(mDialogs);
         UtlString* handle;
         UtlString uniqueDialogId;
         while ( (handle = dynamic_cast <UtlString*> (itor())) )
         {
            Dialog* pDialog = dynamic_cast <Dialog*> (itor.value());
            pDialog->getDialogId(uniqueDialogId);
            if (!notifyDialogs->getDialogByDialogId(uniqueDialogId))
            {
               OsSysLog::add(FAC_SAA, PRI_DEBUG,
                     "Appearance::updateState removed unreferenced dialog: '%s', %zu now in list",
                     uniqueDialogId.data(), mDialogs.entries());
               mDialogs.destroy(&uniqueDialogId);
               bFullContentChanged = true;
            }
         }
      }
   }
   return ret;
}


bool Appearance::appearanceIsBusy()
{
   // we are busy if we are currently managing any non-held dialogs
   bool ret = false;
   UtlHashMapIterator itor(mDialogs);
   UtlString* handle;
   while ( !ret && (handle = dynamic_cast <UtlString*> (itor())) )
   {
      Dialog* pDialog = dynamic_cast <Dialog*> (itor.value());
      UtlString dialogState = STATE_TERMINATED;
      UtlString event;
      UtlString code;
      UtlString rendering;
      pDialog->getState(dialogState, event, code);
      pDialog->getLocalParameter("+sip.rendering", rendering);
      if ( (dialogState != STATE_TERMINATED) &&
           !((dialogState == STATE_CONFIRMED) && (rendering == "no"))
         )
      {
         ret = true;
      }
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
                    "Appearance::appearanceIsBusy mUri = '%s', state %s(%s) returns %d",
                    mUri.data(), dialogState.data(), rendering.data(), ret);
   }
   return ret;
}

bool Appearance::appearanceIdIsSeized(const UtlString& appearanceId)
{
   OsSysLog::add(FAC_SAA, PRI_DEBUG,
                 "Appearance::appearanceIdIsSeized mUri = '%s', appearance = '%s'",
                 mUri.data(), appearanceId.data());
   bool ret = false;
   UtlHashMapIterator itor(mDialogs);
   UtlString* handle;
   while ( !ret && (handle = dynamic_cast <UtlString*> (itor())) )
   {
      Dialog* pDialog = dynamic_cast <Dialog*> (itor.value());
      UtlString dialogState= STATE_TERMINATED;
      UtlString event;
      UtlString code;
      UtlString rendering;
      pDialog->getState(dialogState, event, code);
      pDialog->getLocalParameter("+sip.rendering", rendering);
      UtlString myAppearanceId;
      pDialog->getLocalParameter("x-line-id", myAppearanceId);
      if ( (myAppearanceId == appearanceId) //&&
         //   (dialogState != STATE_TERMINATED) &&
         //  ( !(dialogState == STATE_CONFIRMED) && (rendering == "no") )
           // not even sure I need any other conditions for TRYING contention...
         )
      {
         OsSysLog::add(FAC_SAA, PRI_DEBUG,
                       "Appearance::appearanceIdIsSeized mUri = '%s', appearance = '%s' "
                       "is in state '%s': seized",
                       mUri.data(), appearanceId.data(), dialogState.data());
         ret = true;
      }
   }
   return ret;
}

void Appearance::setResubscribeInterval(bool bShortTimeout)
{
   if (bShortTimeout != mbShortTimeout)
   {
      int subscriptionPeriodSeconds;
      if (bShortTimeout)
      {
         subscriptionPeriodSeconds = getAppearanceAgent()->getSeizedResubscribeInterval();
      }
      else
      {
         subscriptionPeriodSeconds = getAppearanceAgent()->getResubscribeInterval();
      }
      OsSysLog::add(FAC_SAA, PRI_DEBUG,
            "Appearance::setResubscribeInterval changing resubscribe interval for '%s' "
                    "(earlyDialogHandle %s) to %d",
            mUri.data(), mSubscriptionEarlyDialogHandle.data(), subscriptionPeriodSeconds);

      UtlBoolean ret = false;
      ret = getAppearanceAgent()->getSubscribeClient().
         changeSubscriptionTime(mSubscriptionEarlyDialogHandle.data(), subscriptionPeriodSeconds);

      if (ret)
      {
         OsSysLog::add(FAC_SAA, PRI_DEBUG,
               "Appearance::setResubscribeInterval changeSubscriptionTimer for '%s' succeeded",
               mUri.data());
         mbShortTimeout = bShortTimeout;
      }
      else
      {
         OsSysLog::add(FAC_SAA, PRI_WARNING,
               "Appearance::setResubscribeInterval changeSubscriptionTimer for '%s' failed",
               mUri.data());
      }
   }
}


/* ============================ ACCESSORS ================================= */

/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ INQUIRY =================================== */

// Dump the object's internal state.
void Appearance::dumpState()
{
   // indented 6 and 8
   OsSysLog::add(FAC_SAA, PRI_INFO,
                 "\t      Appearance %p mUri = '%s', mSubscriptionEarlyDialogHandle = '%s'",
                 this, mUri.data(), mSubscriptionEarlyDialogHandle.data());

   Dialog* pDialog;
   UtlString b;
   ssize_t l;
   UtlHashMapIterator itor(mDialogs);
   UtlString* handle;
   while ( (handle = dynamic_cast <UtlString*> (itor())) )
   {
      pDialog = dynamic_cast <Dialog*> (itor.value());
      pDialog->getBytes(b, l);
      OsSysLog::add(FAC_SAA, PRI_INFO,
                    "\t        dialog %p '%s'",
                    pDialog, b.data());
   }
}

/**
 * Get the ContainableType for a UtlContainable-derived class.
 */
UtlContainableType Appearance::getContainableType() const
{
   return Appearance::TYPE;
}

/* //////////////////////////// PROTECTED ///////////////////////////////// */

/* //////////////////////////// PRIVATE /////////////////////////////////// */


/* ============================ FUNCTIONS ================================= */
