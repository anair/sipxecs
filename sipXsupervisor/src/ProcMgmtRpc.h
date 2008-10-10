// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////
#ifndef _PROCMGMTRPC_H_
#define _PROCMGMTRPC_H_

// SYSTEM INCLUDES
// APPLICATION INCLUDES
#include "net/XmlRpcMethod.h"

// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS
// FORWARD DECLARATIONS
class WatchDog;

/**
 The base class for all SipxProcess Management XML-RPC Methods.
 */
class ProcMgmtRpcMethod : public XmlRpcMethod
{
public:
   
   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor
   virtual ~ProcMgmtRpcMethod() {}
   
   /// Get the name of the XML-RPC method.
   virtual const char* name() = 0;

   /// Register this method with the XmlRpcDispatch object so it can be called.
   static void registerSelf(WatchDog & watchdog);

   typedef enum
   {
      UnconfiguredPeer = 300, ///< caller is not a configured peer of this server
      InvalidParameter,       ///< missing parameter, extra parameter(s), or invalid type
   } FaultCode;

protected:

   /// The maximum amount of time to block waiting for a single process to undergo a state change.
   static int SINGLE_BLOCK_MAX;

   /// The maximum amount of time to block waiting for a list of processes to undergo a state change.
   static int LIST_BLOCK_MAX;

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;
   
   /// The name of the XML-RPC 'callingHostname' parameter.
   static const char* PARAM_NAME_CALLING_HOST;

   /// The name of the XML-RPC 'alias' parameter.
   static const char* PARAM_NAME_ALIAS;

   /// The name of the XML-RPC 'blockForStateChange' parameter.
   static const char* PARAM_NAME_BLOCK;

   /// The name of the XML-RPC 'service name' parameter.
   static const char* PARAM_NAME_SERVICE;

   /// The name of the XML-RPC 'service version' parameter.
   static const char* PARAM_NAME_SERVICE_VERSION;

   /// constructor 
   ProcMgmtRpcMethod();

   /// Common method for registering with the XML-RPC dispatcher.
   static void registerMethod(const char*       methodName,
                              XmlRpcMethod::Get getMethod,
                              WatchDog&         watchdog
                              );

   /// The execute method called by XmlRpcDispatch.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        ) = 0;

   /// Common method to do caller peer validation.
   bool validCaller(const HttpRequestContext& requestContext, ///< request context
                    const UtlString&          peerName,       ///< name of the calling host
                    XmlRpcResponse&           response,       ///< response to put fault in
                    const WatchDog&           watchdog,       ///< the watchdog
                    const char*               callingMethod   ///< calling xml rpc method name
                    );
   /**<
    * Ensures that: 
    *  - the peerName is one of the configured allowed hosts; and
    *  - the HttpRequestContext indicates that the connection is indeed from that host.
    * 
    * If the caller is not a valid, then the XMLRPC 'response' is populated with an
    * appropriate message.
    * 
    * \return True is the caller is a valid (allowed) peer, false otherwise.
    */

   /// Handle missing parameters for the execute method.
   void handleMissingExecuteParam(const char* methodName,  ///< name of the called XmlRpc method
                                  const char* paramName,   ///< name of problematic parameter
                                  XmlRpcResponse& response,///< response (fault is set on this)
                                  ExecutionStatus& status  ///< set to FAILED
                                  );
   
   /// Handle extra parameters for the execute method.
   void handleExtraExecuteParam(const char* methodName,  ///< name of the called XmlRpc method
                                XmlRpcResponse& response,///< response (fault is set on this)
                                ExecutionStatus& status  ///< set to FAILED
                                );

   /// Sets the user requested state of a single process to the specified state, and blocks for the result.
   bool executeSetUserRequestState(const HttpRequestContext& requestContext, ///< request context
                                   UtlSList& params,                         ///< request param list
                                   void* userData,                           ///< user data
                                   XmlRpcResponse& response,                 ///< request response
                                   ExecutionStatus& status,                  ///< XML-RPC method execution status
                                   const int request_state                   ///< the state to set
                                   );
   /**<
     Returns true on success, false otherwise.
    */
   
private:
   /// no copy constructor
   ProcMgmtRpcMethod(const ProcMgmtRpcMethod& nocopy);

   /// no assignment operator
   ProcMgmtRpcMethod& operator=(const ProcMgmtRpcMethod& noassignment);
};

/**
 Returns the current state of all processes being monitored by the supervisor.

 \par
 <b>Method Name: ProcMgmtRpc.getStateAll</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
 </table>
 
 \par
 <b>Return Value:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string struct</td>
       <td>The current status of each process, indexed by process alias.  (The alias 
           is the "name" attribute of the "sipXecs-process" element in the process's 
           SIPX_SHAREDIR/process.d/___.process.xml configuration file.)
           \par
           Possible states are:
           <code>
           Undefined
           Disabled
           ConfigurationMismatch
           ResourceRequired
           Starting
           Running
           Stopping
           ShuttingDown
           ShutDown
           </code>
           </td>
    </tr>
 </table>
 */
class ProcMgmtRpcGetStateAll : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcGetStateAll() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcGetStateAll();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};

/**
 Attempts to start each of the specified processes.

 \par
 <b>Method Name: ProcMgmtRpc.start</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
    <tr>
       <td>array</td>
       <td>alias</td>
       <td>List of aliases (strings) of the processes whose state is to be changed.  (The alias 
           is the "name" attribute of the "sipXecs-process" element in the process's 
           SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
    <tr>
       <td>boolean</td>
       <td>blockForStateChange</td>
       <td><b>Deprecated and ignored.</b>  Whether or not to block for the state change to occur.</td>
    </tr>
 </table>
 
 \par
 <b>Return Value:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>boolean struct</td>
       <td>Whether or not each process was instructed to start.
           Indexed by process alias.
           (The alias is the "name" attribute of the "sipXecs-process" element in the
           process' SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
 </table>
 */
class ProcMgmtRpcStart : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcStart() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcStart();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};

/**
 Attempts to stop each of the specified processes.

 \par
 <b>Method Name: ProcMgmtRpc.stop</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
    <tr>
       <td>array</td>
       <td>alias</td>
       <td>List of aliases of the processes.  (The alias is the "name" attribute of the
           "sipXecs-process" element in the process's 
           SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
    <tr>
       <td>boolean</td>
       <td>blockForStateChange</td>
       <td><b>Deprecated and ignored.</b>  Whether or not to block for the state change to occur.</td>
    </tr>
 </table>
 
 \par
 <b>Return Value:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>boolean struct</td>
       <td>Whether or not each process was instructed to stop.
           Indexed by process alias.
           (The alias is the "name" attribute of the "sipXecs-process" element in the
           process's SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
 </table>

 */
class ProcMgmtRpcStop : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcStop() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcStop();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};

/**
 Attempts to restart the specified processes.  For each process, returns true if found.

 \par
 <b>Method Name: ProcMgmtRpc.restart</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
    <tr>
       <td>array</td>
       <td>alias</td>
       <td>List of process aliases to restart.  (The alias is the "name" attribute of the
           "sipXecs-process" element in the process's 
           SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
    <tr>
       <td>boolean</td>
       <td>blockForStateChange</td>
       <td>Whether or not to block for the state change to occur.</td>
    </tr>
 </table>
 
 \par
 <b>Return Value:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>boolean struct</td>
       <td>Whether or not each process was found and instructed to restart.
           Indexed by process alias.  (The alias is the "name" attribute of the
           "sipXecs-process" element in the process's 
           SIPX_SHAREDIR/process.d/___.process.xml configuration file.)</td>
    </tr>
 </table>
 */
class ProcMgmtRpcRestart : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcRestart() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcRestart();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};


/**
 Returns the configuration version of the process specified by its name.  (The 
 name is the "name" attribute of the "sipXecs-process" element in the
 process's SIPX_CONFDIR/process.d/___.process.xml configuration file.)

 \par
 <b>Method Name: ProcMgmtRpc.getConfigVersion</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
    <tr>
       <td>string</td>
       <td>name</td>
       <td>The service name of the process whose configuration version is to be returned.</td>
    </tr>
 </table>
 
 \par
 <b>Return Value:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>The configuration version of the process with the specified service name.</td>
    </tr>
 </table>
 */
class ProcMgmtRpcGetConfigVersion : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcGetConfigVersion() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcGetConfigVersion();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};


/**
 Sets the configuration version of the process specified by its name.  (The 
 name is the "name" attribute of the "sipXecs-process" element in  
 the process's SIPX_SHAREDIR/process.d/___.process.xml configuration file.)

 \par
 <b>Method Name: ProcMgmtRpc.setConfigVersion</b>

 \par
 <b>Input:</b>
 <table border="1">
    <tr>
       <td><b>Data type</b></td>
       <td><b>Name</b></td>
       <td><b>Description</b></td>
    </tr>
    <tr>
       <td>string</td>
       <td>callingHostname</td>
       <td>The FQDN of the calling host to be checked as an SSL trusted peer <b>and</b> 
           against an explicit list of hosts allowed to make requests.</td>
    </tr>
    <tr>
       <td>string</td>
       <td>name</td>
       <td>The service name of the process whose configuration version is to be written.</td>
    </tr>
    <tr>
       <td>string</td>
       <td>version</td>
       <td>The configuration version for the service to be written to the service's 
           configuration file.</td>
    </tr>
 </table>
 */
class ProcMgmtRpcSetConfigVersion : public ProcMgmtRpcMethod
{
public:

   /// The XmlRpcMethod::Get registered with the dispatcher for this XML-RPC Method.  
   static XmlRpcMethod* get();

   /// Destructor.
   virtual ~ProcMgmtRpcSetConfigVersion() {};

   /// Get the name of the XML-RPC method.
   virtual const char* name();

   /// Register this method handler with the XML-RPC dispatcher.
   static void registerSelf(WatchDog & watchdog);

protected:

   /// The name of the XML-RPC method.
   static const char* METHOD_NAME;

   /// Constructor.
   ProcMgmtRpcSetConfigVersion();

   /// The execution of this XML-RPC Method.
   virtual bool execute(const HttpRequestContext& requestContext, ///< request context
                        UtlSList& params,                         ///< request param list
                        void* userData,                           ///< user data
                        XmlRpcResponse& response,                 ///< request response
                        ExecutionStatus& status                   ///< XML-RPC method execution status
                        );
};


#endif // _PROCMGMTRPC_H_
