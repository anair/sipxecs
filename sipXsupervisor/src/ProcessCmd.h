// 
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////
#ifndef _PROCESSCMD_H_
#define _PROCESSCMD_H_

// SYSTEM INCLUDES

// APPLICATION INCLUDES
#include "os/OsFS.h"
#include "utl/UtlString.h"
#include "utl/UtlSList.h"

// DEFINES
// CONSTANTS
// TYPEDEFS
// FORWARD DECLARATIONS
class TiXmlElement;

/// Represents a command as defined by the Command type in sipXecs-process.xsd
/**
 * The process definition parser calls the parseCommandDefinition method of this class
 * to interpret each command element for the process, which returns NULL if the element
 * is not valid.
 *
 * To execute the command, the execute method is called (it does not return any status).
 */
class ProcessCmd
{
  public:

   /// initializes by parsing a Command type element from sipXecs-process schema
   static ProcessCmd* parseCommandDefinition(const TiXmlDocument& processDefinitionDoc,
                                             ///< the process definition document
                                             const TiXmlElement* commandElement /**< any 'Command'
                                                                                 *   type element
                                                                                 */
                                             );
   ///< @returns NULL if the element contents are invalid.

   /// Execute the command.
   void execute();
   
   /// destructor
   virtual ~ProcessCmd();

  protected:

   ProcessCmd(const UtlString& execute,
              const UtlString& workingDirectory,
              const UtlString& user
              );
  private:

   OsPath      mWorkingDirectory; ///< Directory from which all process commands are exec'd
   UtlString   mUser;             ///< User that commands must be run as.
   OsPath      mExecutable;       ///< Fully qualified path to the command to be executed.
   UtlSList    mParameters;       ///< UtlStrings to be passed as arguments
   
   // @cond INCLUDENOCOPY
   /// There is no copy constructor.
   ProcessCmd(const ProcessCmd& nocopyconstructor);

   /// There is no assignment operator.
   ProcessCmd& operator=(const ProcessCmd& noassignmentoperator);
   // @endcond     

   friend class ProcessDefinitionParserTest;
};

#endif // _PROCESSCMD_H_
