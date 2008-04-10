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
#include "os/OsQueuedEvent.h"
#include "os/OsEventMsg.h"
#include "os/OsSocket.h"
#include "os/OsTimer.h"
#include "WatchDog.h"
#include "os/OsDateTime.h"
#include "net/XmlRpcDispatch.h"
#include "ProcMgmtRpc.h"
#include "ImdbRpc.h"
#include "FileRpc.h"
#include "utl/UtlBool.h"

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
   const int USER_PROCESS_EVENT = 1; //signifies message is user status check
// STATIC VARIABLE INITIALIZATIONS

/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ CREATORS ================================== */

// Constructor
WatchDog::WatchDog(int nWatchInterval, 	MonitoredProcess **processList,int processCount,
                   const int port, UtlSList& allowedPeers) :
mLock(OsBSem::Q_PRIORITY, OsBSem::FULL),
mpWatchDogTimer(NULL),
mXmlRpcPort(port),
mpXmlRpcDispatch(NULL)
{
   // Create the watchdog event/timer
   mpWatchDogEvent = new OsQueuedEvent(*getMessageQueue(), 0) ;
   mpWatchDogTimer = new OsTimer(*mpWatchDogEvent) ;         
 
   // Create the watchdog event/timer
   mpWatchDogUserChangeEvent = new OsQueuedEvent(*getMessageQueue(), 0) ;
   mpWatchDogUserChangeEvent->setUserData(USER_PROCESS_EVENT); //set subtype to signify user action
   mpWatchDogUserChangeTimer = new OsTimer(*mpWatchDogUserChangeEvent) ;         

   // Finally, set the timers
   mpWatchDogTimer->periodicEvery(OsTime(5, 0),OsTime(nWatchInterval, 0));
   mpWatchDogUserChangeTimer->periodicEvery(OsTime(5, 0),OsTime(5, 0));

   //save off some important things
   mpProcessList = processList;
   mnProcessCount = processCount;
   
   // Take ownership of the UtlString object memory.
   UtlContainable* peer;
   while((peer = allowedPeers.get()))
   {
      mAllowedPeers.insert(peer);
   }
   
   // Make sure the localhost is among the allowed peers.
   UtlString myName;
   OsSocket::getHostName(&myName);
   if (!mAllowedPeers.contains(&myName))
   {
      mAllowedPeers.insert(new UtlString(myName));
   }
}

// Destructor
WatchDog::~WatchDog()
{
   delete mpWatchDogEvent;
   delete mpWatchDogTimer;

   delete mpWatchDogUserChangeEvent;
   delete mpWatchDogUserChangeTimer;

   if ( mpXmlRpcDispatch )
   {
      delete mpXmlRpcDispatch;
      mpXmlRpcDispatch = NULL;
   }

   mAllowedPeers.destroyAll();
}

/* ============================ MANIPULATORS ============================== */

// Assignment operator
WatchDog& 
WatchDog::operator=(const WatchDog& rhs)
{
   if (this == &rhs)            // handle the assignment to self case
      return *this;

   return *this;
}
   
UtlBoolean WatchDog::handleMessage(OsMsg &rMsg)
{
   int         eventData;
   int         userData;
   int         loop;

   UtlBoolean   returnValue = TRUE;
   OsEventMsg* pEventMsg;

#ifdef DEBUG
   UtlString dateString;
   OsDateTime dt;
   OsDateTime::getCurTime(dt);
   dt.getHttpTimeString(dateString);  
#endif /* DEBUG */

   if (rMsg.getMsgType() == OsMsg::OS_EVENT)
   {
      //for now I'll leave these here... although im not using them now
       // I may in the near future
      pEventMsg = (OsEventMsg*) &rMsg;
      pEventMsg->getEventData(eventData);
      pEventMsg->getUserData(userData);

      // Lock out other threads.
      OsLock mutex(mLock);   

      if (userData == USER_PROCESS_EVENT)
      {
          //here we need to loop through all the process objects and call
          //their check method
          for (loop = 0;loop < mnProcessCount;loop++)
          {
             mpProcessList[loop]->ApplyUserRequestedState();
          }
#ifdef DEBUG
          osPrintf("Last state change check occurred at:         %s\n",dateString.data());
#endif /* DEBUG */
      }
      else
      {
          //here we need to loop through all the process objects and request reports to be sent
          //yes, I could put the report call in the loop below with check, but I'd rather 
          //have all processes flush and report before adding any new ones.
          for (loop = 0;loop < mnProcessCount;loop++)
          {
             mpProcessList[loop]->sendReports();
          }

          //here we need to loop through all the process objects and call
          //their check method
          for (loop = 0;loop < mnProcessCount;loop++)
          {
             mpProcessList[loop]->check();
          }

#ifdef DEBUG
          osPrintf("Last check occurred at:         %s\n",dateString.data());
#endif /* DEBUG */
      }
   }

   return(returnValue);
}

int WatchDog::run(void* pArg)
{
   startRpcServer();

   int taskResult = OsServerTask::run(pArg);

   return taskResult;
}

void WatchDog::startRpcServer()
{
   // Begin operation of the XML-RPC service.
   mpXmlRpcDispatch = new XmlRpcDispatch(mXmlRpcPort, true /* use https */);

   // Register the XML-RPC methods.
   ProcMgmtRpcGetStateAll::registerSelf(*this);
   ProcMgmtRpcStart::registerSelf(*this);
   ProcMgmtRpcStartAll::registerSelf(*this);
   ProcMgmtRpcStop::registerSelf(*this);
   ProcMgmtRpcStopAll::registerSelf(*this);
   ProcMgmtRpcRestart::registerSelf(*this);
   ProcMgmtRpcRestartAll::registerSelf(*this);
   ProcMgmtRpcGetAliasByPID::registerSelf(*this);

   ImdbRpcReplaceTable::registerSelf(*this);
   ImdbRpcRetrieveTable::registerSelf(*this);
   ImdbRpcAddTableRecords::registerSelf(*this);
   ImdbRpcDeleteTableRecords::registerSelf(*this);

   FileRpcReplaceFile::registerSelf(*this);

   
}

/* ============================ ACCESSORS ================================= */

XmlRpcDispatch* WatchDog::getXmlRpcDispatch()
{
   return mpXmlRpcDispatch;
}

int WatchDog::getProcessState(const UtlString& alias)
{
   OsProcessMgr* processMgr = OsProcessMgr::getInstance();

   // Lock out other threads.
   OsLock mutex(mLock);   

   return processMgr->getAliasState(alias);
}

void WatchDog::getProcessStateAll(UtlHashMap& process_states)
{
   OsProcessMgr* processMgr = OsProcessMgr::getInstance();

   // Lock out other threads.
   OsLock mutex(mLock);   

   for (int x=0; x < mnProcessCount; x++)
   {
      // UtlHashMap stores raw pointers to the key and value objects, instead 
      // of making copies.  Therefore, individual UtlString objects are  
      // explicitly new'd here, and the caller is responsible for deleting.
      UtlString* pAlias = new UtlString(mpProcessList[x]->getAlias());
      UtlString* pState = new UtlString();
      *pState = getProcessStatusString(processMgr->getAliasState(*pAlias));
      process_states.insertKeyAndValue(pAlias, pState);
   }
}

void WatchDog::setProcessUserRequestStateAll(const int state, UtlHashMap& process_results)
{
   UtlString* pAlias;
   bool result;

   // Lock out other threads.
   OsLock mutex(mLock);   

   for (int x=0; x < mnProcessCount; x++)
   {
      // UtlHashMap stores raw pointers to the key and value objects, instead of 
      // making copies.  Therefore, individual UtlString/UtlBoolean objects are 
      // explicitly new'd here, and the caller is responsible for deleting.
      pAlias = new UtlString(mpProcessList[x]->getAlias());
      result = setProcessUserRequestStateNoLock(*pAlias, state);
      process_results.insertKeyAndValue(pAlias, new UtlBool(result));
   }
}

bool WatchDog::setProcessUserRequestState(const UtlString& alias, const int state)
{
   // Lock out other threads.
   OsLock mutex(mLock);   

   return setProcessUserRequestStateNoLock(alias, state);
}

bool WatchDog::setProcessUserRequestStateNoLock(const UtlString& alias, const int state)
{
   bool result = false;

   // Can the specified process can undergo the specified state change?
   if (canProcessStateChange(alias, state))
   {
      // Yes, so attempt it.
      result = OsProcessMgr::getInstance()->setUserRequestState(alias, state);
   }

   return result;
}

/* ============================ INQUIRY =================================== */

void WatchDog::getAllPids(UtlHashMap& pids)
{
   OsProcess process;
   OsProcessMgr* processMgr = OsProcessMgr::getInstance();
   UtlString* pAlias;

   // Lock out other threads.
   OsLock mutex(mLock);   

   for (int x=0; x < mnProcessCount; x++)
   {
      // UtlHashMap stores raw pointers to the key and value objects, instead of 
      // making copies.  Therefore, individual UtlString/UtlBoolean objects are 
      // explicitly new'd here, and the caller is responsible for deleting.
      pAlias = new UtlString(mpProcessList[x]->getAlias());
      if (pAlias)
      {
         OsStatus rc = processMgr->getProcessByAlias(*pAlias, process);
         if (OS_SUCCESS == rc)
         {
            pids.insertKeyAndValue(pAlias, new UtlInt(process.getPID()));   
         }
         else
         {
            delete pAlias;
         }
      }
   }
}

UtlString WatchDog::getAliasByPid(const PID pid)
{
   UtlString alias;
   
   // Lock out other threads.
   OsLock mutex(mLock);   
   
   OsStatus rc = OsProcessMgr::getInstance()->getAliasByPID(pid, alias);
   if (OS_SUCCESS != rc)
   {
      OsSysLog::add(FAC_WATCHDOG,PRI_INFO,"getAliasByPID(%d) failed, OsStatus = %d", pid, rc);
   }
   
   return alias;
}

PID WatchDog::getPidByAlias(const UtlString &alias)
{
   PID result = 0;
   
   // Lock out other threads.
   OsLock mutex(mLock);   
   
   OsProcess process;
   OsStatus rc = OsProcessMgr::getInstance()->getProcessByAlias(alias, process);
   if (OS_SUCCESS == rc)
   {
      result = process.getPID();
   }
   else
   {
      OsSysLog::add(FAC_WATCHDOG,PRI_INFO,"getProcessByAlias(%s) failed, OsStatus = %d", alias.data(), rc);
   }
   
   return result;
}

bool WatchDog::isAllowedPeer(const UtlString& peer) const
{
   return mAllowedPeers.contains(&peer);
}

bool WatchDog::isValidAlias(const UtlString& alias)
{
   bool alias_found = false;

   for (int x = 0; x < mnProcessCount; x++)
   {
      if (mpProcessList[x]->getAlias() == alias)
      {
         alias_found = true;
         x = mnProcessCount;
      }
   }

   if (!alias_found)
   {
      OsSysLog::add(FAC_WATCHDOG,PRI_INFO,"isValidAlias, no match found: '%s'",alias.data());
   }

   return alias_found;
}


/* //////////////////////////// PROTECTED ///////////////////////////////// */

/* //////////////////////////// PRIVATE /////////////////////////////////// */

/* ============================ TESTING =================================== */

/* ============================ FUNCTIONS ================================= */

