// 
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
// $$
//////////////////////////////////////////////////////////////////////////////

#include <sipxunit/TestUtilities.h>

#include "../AlarmData.h"
#include "../AlarmServer.h"
#include "alarm/Alarm.h"
#include "testlib/FileTestContext.h"
#include "utl/UtlSListIterator.h"

#define DELAY 500  // wait this long before checking whether alarms have been logged

typedef struct
{
   const char* id;
   const char* code;
   const char* component;
   const OsSysLogPriority severity;
   const char* description;
   const char* resolution;
   const bool actions[cAlarmData::eActionMax];
   const int max_report;
   const int min_threshold;
} alarmRowData;

alarmRowData expectedResult[] =
{
   {
      "TEST_LOG",
      "SCF00011",
      "alarmTest",
      PRI_ALERT,
      "This is a test of the log function. Single parameter should be here: {0}, and that's all that is required",
      "This also tests parameter insertion, and single quote.",
      { true, true, false },
      3,
      0
   },
   {
      "NO_LOG",
      "SCF33321",
      "backup",
      PRI_CRIT,
      "This should not be logged",
      "Set log=true in actions element",
      { false, true, false },
      3,
      0
   },
   {
      "MISSING_DESCRIPTION",
      "SCF33321",
      "backup",
      PRI_CRIT,
      "MISSING_DESCRIPTION",
      "Add description. Default should be id.",
      { true, true, false },
      INT_MAX,
      0
   },
   {
      "DUPLICATE_ID",
      "SCF00021",
      "backup",
      PRI_NOTICE,
      "Strings from the second instance of the duplicate id over-write the first by design. "
        "However the rest of the data comes from the first instance.",
      "The duplicate id is flagged in sipXsupervisor.log",
      { true, true, false },
      INT_MAX,
      0
   },
   {
     "DUPLICATE_CODE_1",
     "SCF00031",
     "backup",
     PRI_NOTICE,
     "This is the first instance of the duplicate code.",
     "",
     { true, true, false },
     INT_MAX,
     0
  },    {
     "DUPLICATE_CODE_2",
     "SCF00031",
     "backup",
     PRI_CRIT,
     "This is the second instance of the duplicate code.",
     "It should cause a test failure, but how?",
     { true, true, false },
     INT_MAX,
     0
  }, 
  {
      "UNKNOWN_SEVERITY",
      "SCF33321",
      "backup",
      PRI_WARNING,
      "Unknown severity.",
      "Severity should be one of 'debug info notice warning error crit alert emerg'",
      { true, true, false },
      INT_MAX,
      0
   },
   {
      "PARAMETER_SUBSTITUTION",
      "SCF23089",
      "sipXsupervisor",
      PRI_WARNING,
      "Parameter {1}, then parameter {0}",
      "Check for bugs in assembleMsg!",
      { true, true, false },
      INT_MAX,
      0
   },
   {
      "SPECIAL_CHARACTERS",
      "SCF39289",
      "sipXsupervisor",
      PRI_DEBUG,
      "Print special characters such as <, >, and & properly.",
      "Text must be escaped in the strings file, and unescaped before sending notifications.",
      { true, true, false },
      INT_MAX,
      0
   },
   {
      "DIFF_CONFIG_XML",
      "AAA00001",
      "alarmTest",
      PRI_ERR,
      "The config data for this alarm is in a separate file.",
      "The alarm server should load it and handle it normally.",
      { true, true, false },
      INT_MAX,
      0
   }
};
         
class AlarmServerTest : public CppUnit::TestCase
{
   CPPUNIT_TEST_SUITE(AlarmServerTest);
   CPPUNIT_TEST(testLoadAlarms);
   CPPUNIT_TEST(testHandleAlarm);
   CPPUNIT_TEST(testReloadAlarms);
   CPPUNIT_TEST(testParameterSubstitution);
   CPPUNIT_TEST_SUITE_END();

public:
   
   FileTestContext* mAlarmTestContext;
   FileTestContext* mAlarmTestContext2;
   FileTestContext* mAlarmTestContext3;
   FileTestContext* mAlarmTestContext4;
   UtlString mLogFile;
   UtlString mAlarmFile;
   
   void setUp()
   {   
      OsSysLog::initialize(0, "alarm");
      mLogFile = "alarmTest.log";
      mAlarmFile = "sipXalarms.log";
      OsSysLog::setOutputFile(0, mLogFile);
      OsSysLog::setLoggingPriority(PRI_DEBUG);  
      
      OsSysLog::add(FAC_WATCHDOG, PRI_DEBUG, "AlarmServerTest::setUp");
      
      // set up parallel directory structure to match actual
      mAlarmTestContext = new FileTestContext(TEST_DATA_DIR "/alarm-server",
         TEST_WORK_DIR "/alarms-server-config");
      mAlarmTestContext2 = new FileTestContext(TEST_DATA_DIR "/alarm-server",
         TEST_WORK_DIR "/alarms-server-config/alarms");
      mAlarmTestContext3 = new FileTestContext(TEST_DATA_DIR "/alarm-server",
         TEST_WORK_DIR "/alarms-server-share");
      mAlarmTestContext4 = new FileTestContext(TEST_DATA_DIR "/alarm-server",
         TEST_WORK_DIR "/alarms-server-share/alarms");
      
      // copy test files into parallel structure
      mAlarmTestContext->inputFile("alarm-config.xml");
      mAlarmTestContext2->inputFile("test-alarms-config.xml");
      mAlarmTestContext2->inputFile("test-alarms-config2.xml");
      mAlarmTestContext3->inputFile("alarm-strings.xml");
      mAlarmTestContext4->inputFile("test-alarms-strings.xml");
      
      // tell SipXecsService to use the parallel structure
      mAlarmTestContext->setSipxDir(SipXecsService::ConfigurationDirType);
      mAlarmTestContext3->setSipxDir(SipXecsService::DataDirType);
      
      // now load the test files
      cAlarmServer::getInstance()->init();
   }   
   
   void tearDown()
   {   
      OsSysLog::add(FAC_WATCHDOG, PRI_DEBUG, "AlarmServerTest::tearDown");
      cAlarmServer::getInstance()->cleanup();
      delete mAlarmTestContext;
      delete mAlarmTestContext2;
      delete mAlarmTestContext3;
      delete mAlarmTestContext4;
      OsSysLog::flush();
      fflush(stdout);
   }   
   

   /// read the last message from a log file
   void tail(UtlString& fileName, UtlString& lastLine)
   {
      lastLine = "";
      OsFile file(fileName);
      if (file.open(OsFile::READ_ONLY) == OS_SUCCESS)
      {
         size_t len=0;
         file.getLength(len);
         if (len > 200)
         {
            len = 200;
         }
         if (file.setPosition(len*-1, OsFile::END) == OS_SUCCESS)
         {
            UtlString tempStr;
            while (file.readLine(tempStr) == OS_SUCCESS)
            {
               lastLine = tempStr;
            }
         }
         file.close();
      }
   }

   
   void testLoadAlarms()
   {
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "AlarmServerTest::testLoadAlarms");

      cAlarmData* alarmData;
      for (size_t i=0; i<sizeof(expectedResult)/sizeof(alarmRowData); i++)
      {
         char msg[1000];
         UtlString tempStr(expectedResult[i].id);
         alarmData = cAlarmServer::getInstance()->lookupAlarm(tempStr);
         sprintf(msg, "in definition of alarm %s", expectedResult[i].id);
         CPPUNIT_ASSERT_MESSAGE(msg, alarmData!=0);
         ASSERT_STR_EQUAL_MESSAGE(msg, expectedResult[i].code, alarmData->getCode().data());
         CPPUNIT_ASSERT_EQUAL_MESSAGE(msg, (int)expectedResult[i].severity, (int)alarmData->getSeverity());
         ASSERT_STR_EQUAL_MESSAGE(msg, expectedResult[i].description, alarmData->getDescription().data());
         ASSERT_STR_EQUAL_MESSAGE(msg, expectedResult[i].resolution, alarmData->getResolution().data());
         for (int j=0; j<cAlarmData::eActionMax; j++)
         {
            sprintf(msg, "alarm %s, action %d", expectedResult[i].id, j);
            CPPUNIT_ASSERT_EQUAL_MESSAGE(msg, (int)expectedResult[i].actions[(int)j], (int)alarmData->getAction((cAlarmData::eAlarmActions)j));
         }
         //CPPUNIT_ASSERT_EQUAL(expectedResult[i].min_threshold, alarmData.getMinThreshold());
      }

   }
   void testHandleAlarm()
   {
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "AlarmServerTest::testHandleAlarm");
      
      UtlString localhost("localhost");
      UtlString alarmId("NO_LOG");
      UtlString alarmParam("testing");
      UtlSList alarmParams;
      alarmParams.append(&alarmParam);
      UtlString oldLastString;
      tail(mAlarmFile, oldLastString);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "oldLastString %s", oldLastString.data());
      bool rc=cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      OsTask::delay(500);
      CPPUNIT_ASSERT_MESSAGE("handleAlarm('NO_LOG') failed", rc==true);
      UtlString newLastString;
      tail(mAlarmFile, newLastString);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "newLastString %s", newLastString.data());
      CPPUNIT_ASSERT_MESSAGE("alarm with 'NO_LOG' was logged", !oldLastString.compareTo(newLastString));
      
      alarmId = "TEST_LOG";
      alarmParam = "single parameter";
      alarmParams.removeAll();
      alarmParams.append(&alarmParam);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "Test TEST_LOG");
      cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      OsTask::delay(DELAY);
      UtlString actualString;
      UtlString expectedString = "This is a test of the log function. Single parameter should be here: single parameter, and that's all that is required";
      tail(mAlarmFile, actualString);
      char msg[1000];
      sprintf(msg, "incorrect message was logged: actualString '%s'  expected '%s'", actualString.data(), expectedString.data());
      CPPUNIT_ASSERT_MESSAGE(msg, actualString.contains(expectedString));
      
      // test that non-existant alarm returns false
      alarmId = "NONEXISTANT_ID";
      rc=cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      CPPUNIT_ASSERT_MESSAGE("handleAlarm('NONEXISTANT_ID') did not fail, and should have", rc!=true);
      
      // test that alarm with min_threshold is only logged after n attempts
      alarmId = "MIN_THRESHOLD";
      alarmParam = "one";
      alarmParams.removeAll();
      alarmParams.append(&alarmParam);
      tail(mAlarmFile, oldLastString);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "oldLastString %s", oldLastString.data());
      cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      OsTask::delay(DELAY);
      tail(mAlarmFile, newLastString);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, "newLastString %s", newLastString.data());
      CPPUNIT_ASSERT_MESSAGE("first instance of alarm with 'min_threshold' was logged", !oldLastString.compareTo(newLastString));
      alarmParam = "two";
      alarmParams.append(&alarmParam);
      cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      OsTask::delay(DELAY);
      tail(mAlarmFile, actualString);
      expectedString = "This should only be logged the second time";
      sprintf(msg, "incorrect message was logged: actualString '%s'  expected '%s'", actualString.data(), expectedString.data());
      CPPUNIT_ASSERT_MESSAGE(msg, actualString.contains(expectedString));

   }
   
   void testReloadAlarms()
   {
      OsSysLog::add(FAC_WATCHDOG, PRI_DEBUG, "AlarmServerTest::testReloadAlarms");
      bool rc=cAlarmServer::getInstance()->reloadAlarms();
      CPPUNIT_ASSERT_EQUAL((int)true, (int)rc);
   }
   
   void testParameterSubstitution()
   {
      OsSysLog::add(FAC_WATCHDOG, PRI_DEBUG, "AlarmServerTest::testParameterSubstitution");
      UtlString localhost("localhost");
      UtlString alarmId("PARAMETER_SUBSTITUTION");
      UtlSList alarmParams;
      UtlString alarmParam("1");
      alarmParams.append(&alarmParam);
      UtlString alarmParam2 = "2";
      alarmParams.append(&alarmParam2);
      cAlarmServer::getInstance()->handleAlarm(localhost, alarmId, alarmParams);
      OsTask::delay(DELAY);
      UtlString actualString;
      UtlString expectedString = "Parameter 2, then parameter 1";
      tail(mAlarmFile, actualString);
      char msg[1000];
      sprintf(msg, "incorrect message was logged: actualString '%s'  expected '%s'", actualString.data(), expectedString.data());
      CPPUNIT_ASSERT_MESSAGE(msg, actualString.contains(expectedString));
   }
   
};

CPPUNIT_TEST_SUITE_REGISTRATION(AlarmServerTest);
