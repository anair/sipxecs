//
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
//////////////////////////////////////////////////////////////////////////////

// SYSTEM INCLUDES

// APPLICATION INCLUDES
#include "os/OsFS.h"
#include "os/OsSysLog.h"
#include "xmlparser/tinyxml.h"
#include "xmlparser/XmlErrorMsg.h"
#include "xmlparser/ExtractContent.h"

#include "ImdbResourceManager.h"
#include "ImdbResource.h"

// DEFINES
// CONSTANTS
const UtlContainableType ImdbResource::TYPE = "ImdbResource";

const char* ImdbResource::ImdbResourceTypeName = "imdb";

// TYPEDEFS
// FORWARD DECLARATIONS

// Factory method that parses a 'imdb' resource description element.
bool ImdbResource::parse(const TiXmlDocument& imdbDefinitionDoc, ///< imdb definition document
                         TiXmlElement* resourceElement, // 'imdb' element
                         Process* currentProcess        // whose resources are being read.
                         )
{
   /*
    * This is called by SipxResource::parse with any 'imdb' child of
    * the 'resources' element in a imdb definition.
    *
    * @returns NULL if the element was in any way invalid.
    */
   UtlString errorMsg;
   bool resourceIsValid;

   UtlString tableName;
   resourceIsValid = textContent(tableName, resourceElement);
   if (resourceIsValid)
   {
      if (!tableName.isNull())
      {
         ImdbResourceManager* imdbResourceMgr = ImdbResourceManager::getInstance();

         ImdbResource* imdbResource;
         if (!(imdbResource = imdbResourceMgr->find(tableName)))
         {
            imdbResource = new ImdbResource(tableName);
         }

         for ( const TiXmlAttribute* attribute = resourceElement->FirstAttribute();
               resourceIsValid && attribute;
               attribute = attribute->Next()
              )
         {
            if (!(resourceIsValid =
                  imdbResource->SipxResource::parseAttribute(imdbDefinitionDoc, attribute)))
            {
               OsSysLog::add(FAC_WATCHDOG, PRI_ERR, "ImdbResource::parse "
                             "invalid attribute '%s'",
                             attribute->Name());
            }
         }

         if ( imdbResource->mFirstDefinition ) // existing resources are in the manager already
         {
            if (resourceIsValid)
            {
               imdbResource->mFirstDefinition = false;
               imdbResourceMgr->save(imdbResource);
            }
            else
            {
               delete imdbResource;
            }
         }
      }
      else
      {
         resourceIsValid = false;
         XmlErrorMsg(imdbDefinitionDoc, errorMsg);
         OsSysLog::add(FAC_WATCHDOG, PRI_ERR, "ImdbResource::parse "
                       "imdb element is empty %s",
                       errorMsg.data());
      }
   }
   else
   {
      XmlErrorMsg(imdbDefinitionDoc, errorMsg);
      OsSysLog::add(FAC_WATCHDOG, PRI_ERR, "ImdbResource::parse "
                    "invalid content in imdb element %s",
                    errorMsg.data());
   }

   return resourceIsValid;
}


// get a description of the ImdbResource (for use in logging)
void ImdbResource::appendDescription(UtlString&  description /**< returned description */)
{
   description.append("imdb '");
   description.append(data());
   description.append("'");
}


// Whether or not the ImdbResource is ready for use by a Imdb.
bool ImdbResource::isReadyToStart()
{
   return false; // @TODO
}

// Determine whether or not the values in a containable are comparable.
UtlContainableType ImdbResource::getContainableType() const
{
   return TYPE;
}

/// constructor
ImdbResource::ImdbResource(const char* uniqueId) :
   SipxResource(uniqueId)
{
}

/// destructor
ImdbResource::~ImdbResource()
{
}



