// 
// Copyright (C) 2009 Nortel., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
// $$
//////////////////////////////////////////////////////////////////////////////
#ifndef _CALLDESTINATION_H_
#define _CALLDESTINATION_H_

// SYSTEM INCLUDES
// APPLICATION INCLUDES
#include "AuthPlugin.h"

// DEFINES
// CONSTANTS
// TYPEDEFS
// FORWARD DECLARATIONS
class CallDestinationTest;

extern "C" AuthPlugin* getAuthPlugin(const UtlString& name);

/**
 * The purpose of this auth plugin is to remove the call destination information  
 * from an INVITE request header and copy/append it to the Record-Route.
 * 
 */
class CallDestination : public AuthPlugin
{
  public:

   /// destructor
   virtual ~CallDestination();

   virtual
      AuthResult authorizeAndModify(const UtlString& id,    /**< The authenticated identity of the
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
                                    SipMessage& request,    ///< see AuthPlugin regarding modifying
                                    bool bSpiralingRequest, ///< request spiraling indication 
                                    UtlString&  reason      ///< rejection reason
                                    );
   /**<
    *   The CallDestination plug-in always returns AuthResult::CONTINUE
    */

   /// Read (or re-read) the authorization rules.
   virtual void readConfig( OsConfigDb& configDb /**< a subhash of the individual configuration
                                                  * parameters for this instance of this plugin. */
                           );
   /**<
    * @note
    * The parent service may call the readConfig method at any time to
    * indicate that the configuration may have changed.  The plugin
    * should reinitialize itself based on the configuration that exists when
    * this is called.  The fact that it is a subhash means that whatever prefix
    * is used to identify the plugin (see PluginHooks) has been removed (see the
    * examples in PluginHooks::readConfig).
    */

   virtual void announceAssociatedSipRouter( SipRouter* sipRouter );
   
  protected:
  private:
   friend class CallDestinationTest;
   friend AuthPlugin* getAuthPlugin(const UtlString& name);

   /// Moves sipXecs-CallDest from the request header to Record-Route. 
   bool moveCallDestToRecordRoute( SipMessage& request );
   
   /// Constructor - private so that only the factory can call it.
   CallDestination(const UtlString& instanceName ///< the configured name for this plugin instance
                    );

   SipRouter* mpSipRouter; ///< stores pointer to owning SipRouter.

// @cond INCLUDENOCOPY
   
   /// There is no copy constructor.
   CallDestination(const CallDestination& nocopyconstructor);

   /// There is no assignment operator.
   CallDestination& operator=(const CallDestination& noassignmentoperator);
// @endcond INCLUDENOCOPY
};

#endif // _CALLDESTINATION_H_
