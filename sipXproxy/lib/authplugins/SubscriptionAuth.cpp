//
// Copyright (C) 2008 Nortel Networks, certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES
#include "os/OsSysLog.h"
#include "os/OsConfigDb.h"

// APPLICATION INCLUDES
#include "net/Url.h"
#include "net/SipMessage.h"
#include "net/NameValueTokenizer.h"
#include "SipRouter.h"
#include "SubscriptionAuth.h"
#include "utl/UtlRegex.h"

// DEFINES
// CONSTANTS
// CONSTANTS
const char* SubscriptionAuth::EventsRequiringAuthenticationKey = "PACKAGES_REQUIRING_AUTHENTICATION";
const char* SubscriptionAuth::DestinationNotRequiringAuthenticationKey = "DESTINATIONS_NOT_REQUIRING_AUTHENTICATION";

// TYPEDEFS
// FORWARD DECLARATIONS

/// Factory used by PluginHooks to dynamically link the plugin instance
extern "C" AuthPlugin* getAuthPlugin(const UtlString& pluginName)
{
   return new SubscriptionAuth(pluginName);
}

/// constructor
SubscriptionAuth::SubscriptionAuth(const UtlString& pluginName ///< the name for this instance
   )
   : AuthPlugin(pluginName),
     mpSipRouter(NULL)
{
   OsSysLog::add(FAC_SIP,PRI_INFO,"SubscriptionAuth plugin instantiated '%s'",
                 mInstanceName.data());
};

SubscriptionAuth::~SubscriptionAuth()
{
   mEventPackagesRequiringAuthentication.destroyAll();
   mDestinationNotRequiringAuthentication.destroyAll();
}

void
SubscriptionAuth::announceAssociatedSipRouter( SipRouter* sipRouter )
{
   mpSipRouter = sipRouter;
}

/// Read (or re-read) the authorization rules.
void
SubscriptionAuth::readConfig( OsConfigDb& configDb /**< a subhash of the individual configuration
                                                    * parameters for this instance of this plugin. */
                             )
{
   /*
    * @note
    * The parent service may call the readConfig method at any time to
    * indicate that the configuration may have changed.  The plugin
    * should reinitialize itself based on the configuration that exists when
    * this is called.  The fact that it is a subhash means that whatever prefix
    * is used to identify the plugin (see PluginHooks) has been removed (see the
    * examples in PluginHooks::readConfig).
    */
   OsSysLog::add(FAC_SIP, PRI_DEBUG, "SubscriptionAuth[%s]::readConfig",
                 mInstanceName.data()
                 );

   mEventPackagesRequiringAuthentication.destroyAll();
   mDestinationNotRequiringAuthentication.destroyAll();

   UtlString eventPackagesRequiringAuthentication;
   if (configDb.get(EventsRequiringAuthenticationKey, 
                    eventPackagesRequiringAuthentication) && 
        !eventPackagesRequiringAuthentication.isNull())
   {
      OsSysLog::add( FAC_SIP, PRI_INFO
                    ,"SubscriptionAuth[%s]::readConfig "
                    "  %s = '%s'"
                    ,mInstanceName.data(), EventsRequiringAuthenticationKey
                    ,eventPackagesRequiringAuthentication.data()
                    );
      
      int eventPackageIndex = 0;
      UtlString eventPackageName;
      while(NameValueTokenizer::getSubField(eventPackagesRequiringAuthentication.data(), 
                                            eventPackageIndex,
                                            ", \t", &eventPackageName))
      {
         mEventPackagesRequiringAuthentication.insert( new UtlString( eventPackageName ) );
         eventPackageIndex++;
      }
   }
   else
   {
      OsSysLog::add( FAC_SIP, PRI_NOTICE
                    ,"SubscriptionAuth[%s]::readConfig "
                    "  %s not found - no subscription will be challenged by this plug-in"
                    ,mInstanceName.data(), EventsRequiringAuthenticationKey
                    );
   }

   UtlString destinationNotRequiringAuthentication;
   if (configDb.get(DestinationNotRequiringAuthenticationKey, 
                    destinationNotRequiringAuthentication) &&
       !destinationNotRequiringAuthentication.isNull())
   {
      OsSysLog::add( FAC_SIP, PRI_INFO
                    ,"SubscriptionAuth[%s]::readConfig "
                    "  %s = '%s'"
                    ,mInstanceName.data(), DestinationNotRequiringAuthenticationKey
                    ,destinationNotRequiringAuthentication.data()
                    );
      
      int destinationIndex = 0;
      UtlString destiantionName;
      while(NameValueTokenizer::getSubField(destinationNotRequiringAuthentication.data(), 
                                            destinationIndex,
                                            ", \t", &destiantionName))
      {
         RegEx* destinationRegEx;
         try
         {
            destinationIndex++;
            destinationRegEx = new RegEx(destiantionName.data());
            mDestinationNotRequiringAuthentication.insert(destinationRegEx);
         }
         catch(const char* compileError)
         {
            OsSysLog::add(FAC_SIP, PRI_ERR
                          ,"SubscriptionAuth[%s]::readConfig Invalid recognizer expression '%s' for '%s': %s"
                          ,mInstanceName.data()
                          ,destiantionName.data()
                          ,DestinationNotRequiringAuthenticationKey
                          ,compileError
                          );
         }
      }
   }
}

AuthPlugin::AuthResult
SubscriptionAuth::authorizeAndModify(const UtlString& id,    /**< The authenticated identity of the
                                                              *   request originator, if any (the null
                                                              *   string if not).
                                                              *   This is in the form of a SIP uri
                                                              *   identity value as used in the
                                                              *   credentials database (user@domain)
                                                              *   without the scheme or any parameters.
                                                              */
                                    const Url&  requestUri, ///< parsed target Uri
                                    RouteState& routeState, ///< the state for this request.
                                    const UtlString& method,///< the request method
                                    AuthResult  priorResult,///< results from earlier plugins.
                                    SipMessage& request,    ///< see AuthPlugin wrt modifying
                                    bool bSpiralingRequest, ///< request spiraling indication
                                    UtlString&  reason      ///< rejection reason
                                    )
{
   AuthResult result = CONTINUE;
   UtlString eventField;
   UtlString destination;
   requestUri.getUserId(destination);

   if (CONTINUE == priorResult &&
       id.isNull() &&
       method.compareTo(SIP_SUBSCRIBE_METHOD) == 0 &&
       request.getEventField(eventField) &&
       mEventPackagesRequiringAuthentication.contains( &eventField ) &&
       destinationRequiresAuthentication(destination))
   {
      // we do not have an authenticated ID for the request - challenge it.
      // get the call-id to use in logging
      UtlString callId;
      request.getCallIdField(&callId);

      OsSysLog::add(FAC_AUTH, PRI_INFO, "SubscriptionAuth[%s]::authorizeAndModify "
                    "challenging subscription for dialog event package '%s' (call id = '%s')",
                    mInstanceName.data(), eventField.data(), callId.data()
                    );
      result = DENY;
      reason = "Authentication Required to Subscribe to " + eventField;
   }
   return result;
}

UtlBoolean
SubscriptionAuth::destinationRequiresAuthentication(const UtlString& destination)
{
   UtlBoolean requiresAuth = TRUE;
   UtlSListIterator nextDestinationRegEx(mDestinationNotRequiringAuthentication);

   RegEx* destinationRegEx;
   
   while((destinationRegEx = dynamic_cast<RegEx*>(nextDestinationRegEx())))
   {
      if(destinationRegEx->Search(destination.data()))
      {
         requiresAuth = FALSE;
      }
   }

   return requiresAuth;
}
