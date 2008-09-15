//
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//
// $$
//////////////////////////////////////////////////////////////////////////////


// SYSTEM INCLUDES
#include <climits>
// APPLICATION INCLUDES
#include "xmlparser/ExtractContent.h"
#include "xmlparser/XmlErrorMsg.h"
#include "os/OsDateTime.h"
#include "os/OsFS.h"
#include "os/OsLock.h"
#include "os/OsSysLog.h"
#include "utl/UtlSList.h"
#include "utl/UtlSListIterator.h"
#include "utl/UtlHashMap.h"
#include "utl/UtlHashMapIterator.h"
#include "utl/XmlContent.h"
#include "sipXecsService/SipXecsService.h"
#include "AlarmServer.h"
#include "AlarmUtils.h"
#include "EmailNotifier.h"
#include "LogNotifier.h"

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
const UtlString cAlarmServer::alarmIdKey = "alarmId";

// STATIC VARIABLE INITIALIZATIONS
cAlarmServer*   cAlarmServer::spAlarmServer = 0;
OsMutex         cAlarmServer::sLockMutex (OsMutex::Q_FIFO);

#define DEBUG

/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ CREATORS ================================== */
cAlarmServer::cAlarmServer() :
   mLanguage(""),
   mAlarmCount(0)
{
   OsSysLog::setLoggingPriorityForFacility(FAC_ALARM, PRI_DEBUG);
   for (int i=0; i<cAlarmData::eActionMax; i++)
   {
      mpNotifiers[i] = 0;
   }
   mAlarmMap.removeAll();
}

cAlarmServer::~cAlarmServer()
{
   // Critical Section here
   OsLock lock( sLockMutex );
   
   // reset the static instance pointer to NULL
   spAlarmServer = NULL;
}

/* ============================ ACCESSORS ================================= */
cAlarmServer *cAlarmServer::getInstance()
{
   // Critical Section here
   OsLock lock( sLockMutex );

   // See if this is the first time through for this process
   if ( spAlarmServer == NULL )
   {   // Create the singleton class for clients to use
      spAlarmServer = new cAlarmServer();
   }

   return spAlarmServer;
}

void cAlarmServer::cleanup()
{
   unloadAlarms();
   for (int i=0; i<cAlarmData::eActionMax; i++)
   {
      if (mpNotifiers[i])
      {
         delete mpNotifiers[i];
         mpNotifiers[i] = 0;
      }
   }
}

OsSysLogPriority sevToSyslogPri(const char* sev)
{
   OsSysLogPriority logging_level= PRI_WARNING;

   if (sev)
   {
      if (strcmp(sev,"debug") == 0)
          logging_level = PRI_DEBUG;
      else if (strcmp(sev,"info") == 0)
          logging_level = PRI_INFO;
      else if (strcmp(sev,"notice") == 0)
          logging_level = PRI_NOTICE;
      else if (strcmp(sev,"warning") == 0)
          logging_level = PRI_WARNING;
      else if (strcmp(sev,"error") == 0)
          logging_level = PRI_ERR;
      else if (strcmp(sev,"crit") == 0)
          logging_level = PRI_CRIT;
      else if (strcmp(sev,"alert") == 0)
          logging_level = PRI_ALERT;
      else if (strcmp(sev,"emerg") == 0)
          logging_level = PRI_EMERG;
      else
      {
         OsSysLog::add(FAC_ALARM, PRI_ERR,
                       "Incomprehensible logging level string '%s'!",
                       sev);
      }
   }
   return logging_level;
}

bool cAlarmServer::loadAlarmData(TiXmlElement* element, cAlarmData* data)
{

   UtlString codeStr;
   UtlString idStr;
   UtlString compStr("");
   UtlString sevStr("minor");
   UtlString resStr("");
   bool actEmail = true;
   bool actLog = true;
   bool actTrap = false;
   int  filtMax = INT_MAX;
   int  filtMin = 0;
   if (element && data)
   {
      idStr = element->Attribute("id");
      if (idStr.isNull())
      {
         OsSysLog::add(FAC_ALARM, PRI_ERR,"code=%s: alarm ID is required", codeStr.data());
         return false;
      }
      
      TiXmlElement* codeElement = element->FirstChildElement("code");
      if ( !codeElement )
      {
         OsSysLog::add(FAC_ALARM, PRI_ERR,
               "id=%s: alarm code is required", element->Attribute("id"));
         return false;
      }
      textContentShallow(codeStr, codeElement);

      codeElement = element->FirstChildElement("severity");
      if ( codeElement )
      {
         textContentShallow(sevStr, codeElement);
      }
      else
      {
         OsSysLog::add(FAC_ALARM, PRI_WARNING,
               "id=%s: no severity; assuming %s", idStr.data(), sevStr.data());
      }

      codeElement = element->FirstChildElement("component");
      if ( codeElement )
      {
         textContentShallow(compStr, codeElement);
      }
      else
      {
         OsSysLog::add(FAC_ALARM, PRI_WARNING,"id=%s: no component; set to null", idStr.data());
      }

      codeElement = element->FirstChildElement("action");
      actLog   = getBoolAttribute(codeElement, "log", true);
      actEmail = getBoolAttribute(codeElement, "email", true);
      actTrap  = getBoolAttribute(codeElement, "trap");
      codeElement = element->FirstChildElement("filter");
      filtMax  = getIntAttribute(codeElement, "max_reports", INT_MAX);
      filtMin  = getIntAttribute(codeElement, "min_threshold");
   }
   else
   {
      return false;
   }

   data->setAlarmId(idStr);
   data->setCode(codeStr);
   data->setSeverity(sevToSyslogPri(sevStr));
   data->setComponent(compStr);
   data->setDescription(idStr); // default fallback description is internal id
   data->setResolution(resStr);
   data->actions[cAlarmData::eActionLog] = actLog;
   data->actions[cAlarmData::eActionEmail] = actEmail;
   data->actions[cAlarmData::eActionTrap] = actTrap;
   data->max_report = filtMax;
   data->min_threshold = filtMin;
   data->resetCount();

   return true;

}

bool cAlarmServer::loadAlarmStrings(const UtlString& stringsFile)
{
   // load file in English for fallback
   bool loadResult = loadAlarmStringsFile(stringsFile);
   
   // load localized version if available
   if (!mLanguage.isNull() && mLanguage.compareTo("en"))
   {
      UtlString localStringsFile = stringsFile;
      ssize_t pos = localStringsFile.index(".xml");
      if (pos != UTL_NOT_FOUND)
      {
         // append language to string file name
         UtlString langSuffix = mLanguage;
         langSuffix.insert(0, "_");
         localStringsFile = localStringsFile.insert(pos, langSuffix);
         loadResult = loadAlarmStringsFile(localStringsFile);
      }
      else
      {
         OsSysLog::add(FAC_ALARM, PRI_NOTICE, 
               "stringsFile %s is not .xml, not loading local language", stringsFile.data());
         loadResult = false;
      }   
   }

   return loadResult;
}

bool cAlarmServer::loadAlarmStringsFile(const UtlString& stringsFile)
{
   OsSysLog::add(FAC_ALARM, PRI_DEBUG, " load alarm strings file %s", stringsFile.data());

   TiXmlDocument doc(stringsFile);
   TiXmlHandle docHandle( &doc );
   if (!doc.LoadFile())
   {
      UtlString errorMsg;
      XmlErrorMsg( doc, errorMsg );
      OsSysLog::add(FAC_ALARM, PRI_ERR, "failed to load alarm strings file: %s", errorMsg.data());
      return false;
   }

   TiXmlHandle docH( &doc );
   TiXmlHandle alarmServerHandle = docH.FirstChildElement("alarm_server");
      
   TiXmlElement* settingsElement = alarmServerHandle.FirstChildElement("settings").Element();
   if (settingsElement)
   {
      //load alarm action strings
      TiXmlElement* alarmActionsElement = settingsElement->FirstChildElement("actions");
      if (alarmActionsElement)
      {
         TiXmlElement* element = alarmActionsElement->FirstChildElement("log");
         if (mpNotifiers[cAlarmData::eActionLog])
         {
            mpNotifiers[cAlarmData::eActionLog]->initStrings(element);
         }

         element = alarmActionsElement->FirstChildElement("email");
         if (mpNotifiers[cAlarmData::eActionEmail])
         {
            mpNotifiers[cAlarmData::eActionEmail]->initStrings(element);
         }

         /* not implemented yet
          element = alarmActionsElement->FirstChildElement("trap");
          if (mpNotifiers[cAlarmData::eActionTrap])
          {
          mpNotifiers[cAlarmData::eActionTrap]->initStrings(element);
          } 
          */
      }
   }
   else
   {
      OsSysLog::add(FAC_ALARM, PRI_DEBUG,
                    "no <settings> element in alarm config file '%s'",
                    stringsFile.data());
   }

   //load alarm description strings
   TiXmlElement*  alarmDefElement = alarmServerHandle.
                     FirstChildElement("definitions").Element();
   if (alarmDefElement)
   {
      TiXmlElement* element = alarmDefElement->FirstChildElement();
      UtlString idStr;
      UtlString descStr;
      UtlString resStr;

      for (; element; element=element->NextSiblingElement() )
      {
         idStr = element->Attribute("id");
         if (idStr.isNull())
         {
            OsSysLog::add(FAC_ALARM, PRI_ERR,"Parsing alarm strings file %s: alarm ID is required", stringsFile.data());
            continue;
         }
         
         cAlarmData* alarmData = lookupAlarm(idStr);
         if (!alarmData)
         {
            OsSysLog::add(FAC_ALARM, PRI_ERR,"unknown alarm ID %s", idStr.data());
            continue;
         }

         TiXmlElement* codeElement = element->FirstChildElement("description");
         if (codeElement)
         {
            textContentShallow(descStr, codeElement);
            if (!descStr.isNull())
            {
               UtlString tempStr;
               XmlUnEscape(tempStr, descStr);
               alarmData->setDescription(tempStr);
            }
         }
         
         codeElement = element->FirstChildElement("resolution");
         if (codeElement)
         {
            textContentShallow(resStr, codeElement);
            if (!resStr.isNull())
            {
               UtlString tempStr;
               XmlUnEscape(tempStr, resStr);
               alarmData->setResolution(tempStr);
            }
         }

      }
   }

   return true;
}

cAlarmData* cAlarmServer::lookupAlarm(const UtlString& id)
{
   return dynamic_cast<cAlarmData*>(mAlarmMap.findValue(&id));
}


bool cAlarmServer::loadAlarmConfig(const UtlString& alarmFile)
{
   // load global alarm config from alarm-config.xml
   OsSysLog::add(FAC_ALARM, PRI_DEBUG, "load alarm config file %s", alarmFile.data());

   TiXmlDocument doc(alarmFile);
   TiXmlHandle docHandle( &doc );
   if (!doc.LoadFile())
   {
      UtlString errorMsg;
      XmlErrorMsg( doc, errorMsg );
      OsSysLog::add(FAC_ALARM, PRI_ERR, "failed to load alarm config file %s", errorMsg.data());
      return false;
   }

   TiXmlHandle docH( &doc );
   TiXmlHandle alarmServerHandle = docH.FirstChildElement("alarm_server");
   
   TiXmlElement* settingsElement = alarmServerHandle.FirstChildElement("settings").Element();
   if (!settingsElement)
   {
      OsSysLog::add(FAC_ALARM, PRI_ERR,
                    "no <settings> element in alarm config file '%s'",
                    alarmFile.data());
      return false;
   }

   TiXmlElement* langElement = settingsElement->FirstChildElement("language");
   if (langElement)
   {
      textContentShallow(mLanguage, langElement);
      OsSysLog::add(FAC_ALARM, PRI_INFO, "language for alarm notifications: %s", mLanguage.data());
   }

   //load alarm action settings
   TiXmlElement* alarmActionsElement = settingsElement->FirstChildElement("actions");
   if (alarmActionsElement)
   {
      TiXmlElement* element = alarmActionsElement->FirstChildElement("log");
      if (getBoolAttribute(element, "enabled"))
      {
         LogNotifier* pLogNotifier = new LogNotifier();
         if (mpNotifiers[cAlarmData::eActionLog])
         {
            delete mpNotifiers[cAlarmData::eActionLog];
         }
         mpNotifiers[cAlarmData::eActionLog] = pLogNotifier;
         if (pLogNotifier)
         {
            pLogNotifier->init(element);
            gbActions[cAlarmData::eActionLog] = true;
         }
      }
      
      element = alarmActionsElement->FirstChildElement("email");
      if (getBoolAttribute(element, "enabled"))
      {
         EmailNotifier* pEmailNotifier = new EmailNotifier();
         if (mpNotifiers[cAlarmData::eActionEmail])
         {
            delete mpNotifiers[cAlarmData::eActionEmail];
         }
         mpNotifiers[cAlarmData::eActionEmail] = pEmailNotifier;
         if (pEmailNotifier)
         {
            pEmailNotifier->init(element);
            gbActions[cAlarmData::eActionEmail] = true;
         }
      }      
      
      /* not implemented yet
      element = alarmActionsElement->FirstChildElement("trap");
      if (getBoolAttribute(element, "enabled"))
      {
         TrapNotifier* pTrapNotifier = new TrapNotifier();
         if (mpNotifiers[cAlarmData::eActionTrap])
         {
            delete mpNotifiers[cAlarmData::eActionTrap];
         }
         mpNotifiers[cAlarmData::eActionTrap] = pTrapNotifier;
         if (pTrapNotifier)
         {
            pTrapNotifier->init(element);
            gbActions[cAlarmData::eActionTrap] = true;
         }
      }  
      */    
   }
   return true;
}

bool cAlarmServer::loadAlarmDefinitions(const UtlString& alarmFile)
{
   OsSysLog::add(FAC_ALARM, PRI_DEBUG, " load alarm def file %s", alarmFile.data());

   TiXmlDocument doc(alarmFile);
   TiXmlHandle docHandle( &doc );
   if (!doc.LoadFile())
   {
      UtlString errorMsg;
      XmlErrorMsg( doc, errorMsg );
      OsSysLog::add(FAC_ALARM, PRI_ERR, "failed to load alarm file: %s", errorMsg.data());
      return false;
   }

   TiXmlHandle docH( &doc );    
         
   //load alarm definitions
   TiXmlElement* alarmDefElement = docH.FirstChildElement("alarm_server").
   FirstChildElement("definitions").Element();
   if (alarmDefElement)
   {
      TiXmlElement* element = alarmDefElement->FirstChildElement();
      cAlarmData* pAlarmData;
      for (; element; element=element->NextSiblingElement() )
      {
         pAlarmData = new cAlarmData();
         if (loadAlarmData(element, pAlarmData))
         {
            UtlString* idStr = new UtlString(pAlarmData->getId());
            if (!mAlarmMap.insertKeyAndValue(idStr, pAlarmData))
            {
               OsSysLog::add(FAC_ALARM, PRI_ERR, "%s is already defined", pAlarmData->getId().data());
               delete pAlarmData;
            }
         }
         else
         {
            OsSysLog::add(FAC_ALARM, PRI_ERR, "%s is incorrectly defined",
                  (char *)element->ToText());
            delete pAlarmData;
         }
      }
   }
   return true;
}


bool cAlarmServer::loadAlarms()
{
   // load global alarm config from alarm-config.xml
   UtlString strAlarmFilename = SipXecsService::Path(SipXecsService::ConfigurationDirType,
         "alarm-config.xml");
   loadAlarmConfig(strAlarmFilename);
   
   // load specific alarm definitions from ${confdir}/alarms/*.xml
   UtlString alarmDefDir = SipXecsService::Path(SipXecsService::ConfigurationDirType);
   alarmDefDir.append("/alarms/");
   OsSysLog::add(FAC_ALARM, PRI_INFO, "looking for alarm def files in %s", alarmDefDir.data());
   OsFileIterator files(alarmDefDir);
   OsPath entry;
   
   // for each file in the ${confdir}/alarms, load alarm definitions
   OsStatus status;
   int numFound;
   for (status=files.findFirst(entry, ".*\\.xml$", OsFileIterator::FILES), numFound=0;
        status == OS_SUCCESS;
        status = files.findNext(entry) )
   {
      loadAlarmDefinitions(alarmDefDir + entry);
   }

   // load alarm strings and local language version from ${datadir}/alarms/*.xml
   UtlString alarmStringsDir = SipXecsService::Path(SipXecsService::DataDirType);
   alarmStringsDir.append("/alarms/");
   OsSysLog::add(FAC_ALARM, PRI_INFO, "looking for alarm string files in %s",
                 alarmStringsDir.data());
   OsFileIterator stringFiles(alarmStringsDir);
   
   // for each "base" file (*-strings.xml) in the ${datadir}/alarms, load alarm strings
   for (status=stringFiles.findFirst(entry, ".*-strings\\.xml$", OsFileIterator::FILES), numFound=0;
        status == OS_SUCCESS;
        status = stringFiles.findNext(entry) )
   {
      loadAlarmStrings(alarmStringsDir + entry);
   }


#ifdef DEBUG
   UtlHashMapIterator alarmIter(mAlarmMap);
   UtlString* alarmKey;
   int count=0;
   while ((alarmKey = dynamic_cast<UtlString*>(alarmIter())))
   {
      cAlarmData* alarm = dynamic_cast<cAlarmData*>(alarmIter.value());
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, 
            "alarm[%d]: %s %s: %s, Log:%d, Email:%d", 
            count, alarmKey->data(), alarm->getCode().data(),
            OsSysLog::sPriorityNames[alarm->getSeverity()], 
            alarm->actions[cAlarmData::eActionLog], alarm->actions[cAlarmData::eActionEmail]);
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, 
            "           Description:%s", alarm->getDescription().data());
      OsSysLog::add(FAC_ALARM, PRI_DEBUG, 
            "           Resolution:%s", alarm->getResolution().data());
      count++;
   }
#endif
   
   return true;
}

void cAlarmServer::unloadAlarms()
{
   mAlarmMap.destroyAll();
}


bool cAlarmServer::handleAlarm(UtlString& callingHost, UtlString& alarmId, UtlSList& alarmParams)
{
   cAlarmData* alarmData;
   OsTime now, alarmTime;
   OsDateTime::getCurTime(alarmTime);
   OsDateTime::getCurTimeSinceBoot(now);

   alarmData = lookupAlarm(alarmId);
   if (alarmData)
   {
      alarmData->setTime(now);
      alarmData->incrCount();

      if (alarmData->applyThresholds())
      {
         // alarm has been filtered out due to thresholds
         OsSysLog::add(FAC_ALARM, PRI_INFO, 
               "alarm %s not logged due to thresholds", alarmId.data());
         return true;
      }

      //:TODO: keep counts at each severity level
      mAlarmCount++;
      
      UtlString alarmMsg;
      assembleMsg(alarmData->getDescription(), alarmParams, alarmMsg);

      for (int i=0; i<cAlarmData::eActionMax; i++)
      {
         if (gbActions[i] && alarmData->getAction((cAlarmData::eAlarmActions)i) && mpNotifiers[i])
         {
            mpNotifiers[i]->handleAlarm(alarmTime, callingHost, alarmData, alarmMsg);
         }
      }
      return true;
   }
   else
   {
      OsSysLog::add(FAC_ALARM, PRI_ERR, "lookup of alarm %s failed", alarmId.data());
      return false;
   }
}

int cAlarmServer::getAlarmCount()
{
   //:TODO: turn into array of counts across priority
   return mAlarmCount;
}

bool cAlarmServer::reloadAlarms()
{
   unloadAlarms();
   return loadAlarms();
}

bool cAlarmServer::init()
{
   for (int i=0 ; i<cAlarmData::eActionMax; i++)
   {
      gbActions[i] = false;
   }
   
   mLanguage="";
   mAlarmCount=0;
   
   return loadAlarms();
}


