// 
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////


// INCLUDES
#include "os/OsFS.h"
#include "os/OsSysLog.h"
#include "utl/UtlHashBagIterator.h"
#include "xmlparser/tinyxml.h"

#include "Process.h"
#include "ProcessManager.h"

// DEFINES
// CONSTANTS
const char* PROCESS_DEFINITION_NAME_PATTERN = ".*-process.xml";

// STATICS
OsBSem          ProcessManager::sSingletonLock(OsBSem::Q_PRIORITY, OsBSem::FULL);
ProcessManager* ProcessManager::spSingleton;

// FORWARD DECLARATIONS

/// Singleton accessor
ProcessManager* ProcessManager::getInstance()
{
   OsLock mutex(sSingletonLock);

   if (!spSingleton)
   {
      spSingleton = new ProcessManager();
   }
   return spSingleton;
}


/// constructor
ProcessManager::ProcessManager() :
   mProcessTableLock(OsBSem::Q_PRIORITY, OsBSem::FULL)
{
};

/// Save a process definition.
void ProcessManager::save(Process* process)
{
   // called from within Process::createFromDefinition
   OsLock mutex(mProcessTableLock);
         
   OsSysLog::add(FAC_SUPERVISOR, PRI_NOTICE, "ProcessManager::save "
                 " Process '%s'", process->data());
   mProcesses.insert(process);
}



/// Locate a Process object by name.
Process* ProcessManager::findProcess(const UtlString& processName)
{
   OsSysLog::add(FAC_SUPERVISOR, PRI_DEBUG,"ProcessManager::findProcess "
                 "searching for '%s'", processName.data()
                 );

   OsLock tableMutex(mProcessTableLock);

   return dynamic_cast<Process*>(mProcesses.find(&processName));
}

/// Find process definitions and instantiate Process objects for each.
void ProcessManager::instantiateProcesses(const OsPath& processDefinitionDirectory)
{
   OsFileIterator definitions(processDefinitionDirectory);
   OsPath    processDefinitionFile;
   OsStatus  iteratorStatus;
   
   OsSysLog::add(FAC_SUPERVISOR, PRI_DEBUG,"ProcessManager::instantiateProcesses searching %s",
                 processDefinitionDirectory.data()
                 );

   for ( iteratorStatus = definitions.findFirst(processDefinitionFile,
                                                PROCESS_DEFINITION_NAME_PATTERN,
                                                OsFileIterator::FILES);
         OS_SUCCESS == iteratorStatus;
         iteratorStatus = definitions.findNext(processDefinitionFile)
        )
   {
      OsPath processDefinitionPath( processDefinitionDirectory
                                   +OsPath::separator
                                   +processDefinitionFile
                                   );
      OsSysLog::add(FAC_SUPERVISOR, PRI_DEBUG,"ProcessManager::instantiateProcesses reading %s",
                    processDefinitionPath.data()
                    );

      Process::createFromDefinition(processDefinitionPath);
   }
}

// Fill in a map of process names and states (as UtlStrings)
void ProcessManager::getAllProcessStates(UtlHashMap& processStates //< key->name, value->state string
                         )
{
   processStates.destroyAll();
   Process* process;

   OsLock mutex(mProcessTableLock);
   UtlHashBagIterator processes(mProcesses);
   while ((process = dynamic_cast<Process*>(processes())))
   {
      processStates.insertKeyAndValue(new UtlString(process->data()),
                                      new UtlString(Process::state(process->getState()))
                                      );
   }
}

/// destructor
ProcessManager::~ProcessManager()
{
   OsLock tableMutex(mProcessTableLock);

   OsSysLog::add(FAC_SUPERVISOR, PRI_NOTICE, "ProcessManager::~ "
                 "delete %zu Process objects", mProcesses.entries());

   mProcesses.destroyAll();
};
