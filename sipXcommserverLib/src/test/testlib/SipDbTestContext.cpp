// 
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
// $$
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES
#include <memory>
#include <stdlib.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/TestCase.h>

// APPLICATION INCLUDES
#include "os/OsFS.h"
#include "sipdb/SIPDBManager.h"
#include "sipXecsService/SipXecsService.h"
#include "sipxunit/TestUtilities.h"
#include "testlib/FileTestContext.h"
#include "testlib/SipDbTestContext.h"

// DEFINES
// CONSTANTS
// TYPEDEFS
// FORWARD DECLARATIONS


/// constructor
SipDbTestContext::SipDbTestContext( const char* testInputDir
                                   ,const char* testWorkingDir
                                   )
   : FileTestContext(testInputDir, testWorkingDir)
{
   setFastDbEnvironment();
};

void SipDbTestContext::setFastDbEnvironment()
{
   // Locate the registration DB in a test directory so that
   // we don't collide with the production DB.
   UtlString msg("failed to set environment to '");
   msg.append(mTestWorkingDir);
   msg.append("'");
   int status = 0;
   status += setenv(SipXecsService::DatabaseDirType, mTestWorkingDir, 1);
   status += setenv(SipXecsService::TmpDirType,      mTestWorkingDir, 1);

   CPPUNIT_ASSERT_EQUAL_MESSAGE(msg.data(), 0, status);
}

/// destructor
SipDbTestContext::~SipDbTestContext()
{
   delete SIPDBManager::getInstance();
};
