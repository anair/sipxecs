// 
// Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//////////////////////////////////////////////////////////////////////////////
#ifndef _FILERESOURCE_H_
#define _FILERESOURCE_H_

// SYSTEM INCLUDES

// APPLICATION INCLUDES
#include "utl/UtlHashBag.h"
#include "SipxResource.h"

// DEFINES
// CONSTANTS
// TYPEDEFS
// FORWARD DECLARATIONS

/// Represents a File as a SipxResource
/**
 * Created as a side effect of creating a Process when parsing a 'sipXecs-process' element
 *   (in the 'findProcessResource' method).  @see the Process class documentation.
 *
 * To locate a particular FileResource, call FileResourceManager::find.
 *
 * At present, this class is used for both 'file' and 'osconfigdb' resource types; they
 * are in fact both files, but in the future we may take advantage of other ways of manipulating
 * an OsConfigDb (see ConfigRPC), so we make the distinction in the definition.
 * If we make that change, the osconfigdb element parsing will be moved to a SipxResource
 * subclass of its own.
 */
class FileResource : public SipxResource
{
  public:

// ================================================================
/** @name           Creation
 *
 */
///@{
   /// Public name of the resource element parsed by this parser.
   static const char* FileResourceTypeName;       ///< name of 'file' resource element
   static const char* OsconfigdbResourceTypeName; ///< name of 'osconfigdb' resource element

   /// Factory method that parses a 'file' or 'osconfigdb' resource description element.
   static
      bool parse(const TiXmlDocument& processDefinitionDoc, ///< process definition document
                 TiXmlElement* resourceElement, ///< the child element of 'resources'.
                 Process* currentProcess        ///< Process whose resources are being read.
                 );
   /**<
    * This is called by SipxResource::parse with any 'file' or 'osconfigdb' child of
    * the 'resources' element in a process definition.  
    *
    * @returns false if the element was in any way invalid.
    */

   /// Log files are resources too - this creates a log file resource
   static
      FileResource* logFileResource(const UtlString& logFilePath, Process* currentProcess);
   ///< a log file resource is read-only and not required
   
///@}   
// ================================================================
/** @name           Status Operations
 *
 */
///@{

   /// get a description of the FileResource (for use in logging)
   virtual void appendDescription(UtlString&  description /**< returned description */);

   /// Whether or not the FileResource is ready for use by a Process.
   virtual bool isReadyToStart();

///@}

// ================================================================
/** @name           Container Support Operations
 *
 */
///@{

   /// Determine whether or not the values in a containable are comparable.
   virtual UtlContainableType getContainableType() const;
   /**<
    * This returns a unique type for UtlString
    */

   static const UtlContainableType TYPE;    ///< Class type used for runtime checking 

///@}

  protected:
   
   /// constructor
   FileResource(const char* uniqueId, Process* currentProcess);

   /// destructor
   virtual ~FileResource();

  private:

   // @cond INCLUDENOCOPY
   /// There is no copy constructor.
   FileResource(const FileResource& nocopyconstructor);

   /// There is no assignment operator.
   FileResource& operator=(const FileResource& noassignmentoperator);
   // @endcond     
};

#endif // _FILERESOURCE_H_
