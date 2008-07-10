//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
// 
//
// $$
////////////////////////////////////////////////////////////////////////
//////


// SYSTEM INCLUDES
#include <stdio.h>
#if defined(_WIN32)
#   include <io.h>
#elif defined(_VXWORKS)
#   include <unistd.h>
#   include <dirent.h>
#elif defined(__pingtel_on_posix__)
#   include <unistd.h>
#   include <stdlib.h>
#   define O_BINARY 0 // There is no notion of a "not binary" file under POSIX,
                      // so we just set O_BINARY used below to no bits in the mask.
#else
#   error Unsupported target platform.
#endif
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "os/OsDefs.h"
#include "os/OsSysLog.h"

// APPLICATION INCLUDES
#include <os/OsServerSocket.h>
#include <os/OsConnectionSocket.h>
#include <os/OsConfigDb.h>
#include <utl/UtlVoidPtr.h>
#include <net/HttpMessage.h>
#include <net/HttpServer.h>
#include <net/HttpService.h>
#include <net/HttpBody.h>
#include <net/MimeBodyPart.h>
#include <net/HttpRequestContext.h>
#include <net/NetAttributeTokenizer.h>
#include <net/NetMd5Codec.h>
#include <utl/UtlSListIterator.h>

// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
#ifdef _VXWORKS
#   define O_BINARY 0
#   define S_IREAD 0
#   define S_IWRITE 0
#endif

// STATIC VARIABLE INITIALIZATIONS

#ifdef TEST_UPLOAD_FILE_DEBUG
void incrementalCheckSum(unsigned int* checkSum, const char* buffer, ssize_t bufferLength)
{
    ssize_t integerIndex = 0;
    while(integerIndex < bufferLength)
    {
        (*checkSum) += (*buffer);
        buffer++;
        integerIndex++;
    }
}
#endif
/* //////////////////////////// PUBLIC //////////////////////////////////// */

/* ============================ CREATORS ================================== */

// Constructor
HttpServer::HttpServer(OsServerSocket *pSocket, OsConfigDb* userPasswordDb,
                       const char* realm, OsConfigDb* validIpAddressDB,
                       bool bPersistentConnection) :
   OsTask("HttpServer-%d"),
   httpStatus(OS_TASK_NOT_STARTED),
   mpServerSocket(pSocket),
   mpUserPasswordDigestDb(NULL),
   mpUserPasswordBasicDb(userPasswordDb),
   mpValidIpAddressDB(validIpAddressDB),
   mpNonceDb(new OsConfigDb),
   mRealm(realm),
   mAllowMappedFiles(true), // :TODO: should be false, but allow now for backward compatibility
   mbPersistentConnection(bPersistentConnection),
   mHttpConnections(0),
   mpHttpConnectionList(new UtlSList)
{
   if(mpValidIpAddressDB)
   {
      loadValidIpAddrList();
   }

   if (!mpNonceDb)
   {
      OsSysLog::add( FAC_SIP, PRI_ERR, "HttpServer failed to allocate mpNonceDb");
   }
   
   if (!mpHttpConnectionList)
   {
      mbPersistentConnection = false;
      OsSysLog::add( FAC_SIP, PRI_ERR, "HttpServer failed to allocate mpHttpConnectionList");
   }
   else
   {
      OsSysLog::add(FAC_SIP, PRI_INFO, "HttpServer: Using persistent connections" );
   }
   
}

void HttpServer::loadValidIpAddrList()
{
   //maybe there should be no reference to OsConfig DB here and the hashTable should be passed
   // as paramenter to HtppServer. If time permits I will make the change. - SDUA
   UtlString strKey;
   int i = 1;
   while(true)
   {
      char szTempBuf[32] ;
      sprintf(szTempBuf, "%d", i) ;
      UtlString ipAddress;

      if( mpValidIpAddressDB->get(szTempBuf, ipAddress) && !ipAddress.isNull())
      {
         UtlString *pMatchIpAddress = new UtlString(ipAddress);
         mValidIpAddrList.insert(pMatchIpAddress);
         i++;
         continue;
      }
      else
      {
         //no more ip addresses
         break;
      }
   }
}

// Copy constructor
HttpServer::HttpServer(const HttpServer& rHttpServer)
{
}

// Destructor
HttpServer::~HttpServer()
{
    if(mpServerSocket)
    {
        // Close the server socket to unblock the listener
        mpServerSocket->close();
    }

    // Wait until run exits before clobbering members
    waitUntilShutDown();

    if(mpServerSocket)
    {
       delete mpServerSocket;
       mpServerSocket = NULL;
    }
    
    if(mpUserPasswordDigestDb)
    {
       delete mpUserPasswordDigestDb;
       mpUserPasswordDigestDb = NULL;
    }

    if(mpUserPasswordBasicDb)
    {
       delete mpUserPasswordBasicDb;
       mpUserPasswordBasicDb = NULL;
    }
    
    if(mpValidIpAddressDB)
    {
       delete mpValidIpAddressDB;
       mpValidIpAddressDB = NULL;
       mValidIpAddrList.destroyAll();
    }

    if(mpNonceDb)
    {
       delete mpNonceDb;
       mpNonceDb = NULL;
    }

    // Delete all of the processor mappings
    mRequestProcessorMethods.destroyAll();
    
    // Delete remaining HttpConnections
    if (mpHttpConnectionList)
    {
        mpHttpConnectionList->destroyAll();
        delete mpHttpConnectionList;
        mpHttpConnectionList = NULL;
    }
}

/* ============================ MANIPULATORS ============================== */

// Assignment operator
HttpServer&
HttpServer::operator=(const HttpServer& rhs)
{
   if (this == &rhs)            // handle the assignment to self case
      return *this;

   return *this;
}

OsStatus HttpServer::getStatus()
{
        return httpStatus;
}

UtlBoolean HttpServer::isSocketOk() const
{
    UtlBoolean bOk = FALSE;
    
    if (mpServerSocket)
    {
        bOk = mpServerSocket->isOk();
    }
        
    return bOk;    
}

int HttpServer::run(void* runArg)
{
    OsConnectionSocket* requestSocket = NULL;

    if (!mpServerSocket->isOk())
    {
        OsSysLog::add( FAC_SIP, PRI_ERR, "HttpServer: port not ok" );
        httpStatus = OS_PORT_IN_USE;
    }

    while(!isShuttingDown() && mpServerSocket->isOk())
    {
        requestSocket = mpServerSocket->accept();
        
        if(requestSocket)
        {
            if (mbPersistentConnection)
            {
                // Take this opportunity to check for any old HttpConnections that can be deleted
                int items = mpHttpConnectionList->entries();
                if (items != 0)
                {
                    int deleted = 0;
                    
                    UtlSListIterator iterator(*mpHttpConnectionList);
                    HttpConnection* connection;
                    while ((connection = dynamic_cast<HttpConnection*>(iterator())))
                    {
                        if (connection->toBeDeleted())
                        {
                           OsSysLog::add(FAC_SIP, PRI_DEBUG,
                                         "HttpServer: destroying connection %p",
                                         connection);
                           mpHttpConnectionList->destroy(connection);                            
                           ++deleted;
                            
                           if (mHttpConnections > 0)
                           {
                              --mHttpConnections;
                           }
                        }
                    }
                    items = mpHttpConnectionList->entries();
                    OsSysLog::add(FAC_SIP, PRI_DEBUG, 
                                  "HttpServer: "
                                  "destroyed %d inactive HttpConnections, %d remaining", 
                                  deleted, items);                    
                }
                // Create new persistent connection             
                if (mHttpConnections < MAX_PERSISTENT_HTTP_CONNECTIONS)
                {
                    ++mHttpConnections;
                    HttpConnection* newConnection = new HttpConnection(requestSocket, this);
                    mpHttpConnectionList->append(newConnection);
                    OsSysLog::add(FAC_SIP, PRI_INFO,
                                  "HttpServer::run starting persistent connection %d (%p)", 
                                  mHttpConnections, newConnection);                    
                    newConnection->start();
                }
                else
                {
                   OsSysLog::add(FAC_SIP, PRI_WARNING,
                                  "HttpServer::run out of persistent connections - sending 503");
                    HttpMessage request;
                    HttpMessage response;
                    // Read the http request from the socket
                    request.read(requestSocket);
                    
                    // Send out-of-resources message
                    response.setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
                                                        HTTP_OUT_OF_RESOURCES_CODE,
                                                        HTTP_OUT_OF_RESOURCES_TEXT);
                    response.write(requestSocket);
                    requestSocket->close();
                    delete requestSocket;
                    requestSocket = NULL;                                                         
                }
            }
            else
            {
                HttpMessage request;
                // Read a http request from the socket
                request.read(requestSocket);

                UtlString remoteIp;
                requestSocket->getRemoteHostIp(&remoteIp);

                HttpMessage* response = NULL;

                // If request from Valid IP Address
                if( processRequestIpAddr(remoteIp, request, response))
                {
                   // If the request is authorized
                   processRequest(request, response, requestSocket);
                }

                if(response)
                {
                    response->write(requestSocket);
                    delete response;
                    response = NULL;
                }

                requestSocket->close();
                delete requestSocket;
                requestSocket = NULL;
            }
        }
        else
        {
           httpStatus = OS_PORT_IN_USE;
        }
    } // while (!isShuttingDown && mpServerSocket->isOk()) 

    if ( !isShuttingDown() )
    {
       OsSysLog::add( FAC_SIP, PRI_ERR, "HttpServer: exit due to port failure" );
    }
    
    httpStatus = OS_TASK_NOT_STARTED;

    return(TRUE);
}


UtlBoolean HttpServer::isRequestAuthorized(const HttpMessage& request,
                                HttpMessage*& response,
                                UtlString& userId)
{
    UtlBoolean allowRequest = FALSE;
    if( mpUserPasswordDigestDb == NULL && mpUserPasswordBasicDb == NULL)
    {
       allowRequest = TRUE;
       return allowRequest;
    }

    // If digest authentication is enabled
    else if(mpUserPasswordDigestDb && !mpUserPasswordDigestDb->isEmpty())
    {
        UtlString user;
        UtlString nonce;
        UtlString nonceKey;
        char nonceSecret[20];
        
        request.getDigestAuthorizationData(&user, NULL,
                                                  NULL, &nonceKey);
        UtlString userPasswordDigest;

        mpUserPasswordDigestDb->get(user.data(), userPasswordDigest);

        // Get the nonce for the user/URI pair
        if(!user.isNull())
        {
            mpNonceDb->get(nonceKey.data(), nonce);

            // Remove the nonce from the database, so that it cannot be re-used
            mpNonceDb->remove(nonceKey.data());
        }

        // If the user is setup for a password
        if(!userPasswordDigest.isNull() &&
            request.verifyMd5Authorization(userPasswordDigest.data(),
                                     nonce))
        {
            allowRequest = TRUE;
            userId = user;
        }

        // Generate the authorization error response
        if(!allowRequest)
        {
            // Create the nonce
            request.getRequestUri(&nonceKey);
            nonceKey.insert(0, ':');
            sprintf(nonceSecret, "%d", rand());
            nonceKey.insert(0, nonceSecret);

            UtlString nonceSeed;
            sprintf(nonceSecret, "%d",rand());
            nonceSeed = nonceKey;
            nonceSeed.append(nonceSecret);
            NetMd5Codec::encode(nonceSeed.data(), nonce);

            // Add it to the database for when the authorized request comes in
            mpNonceDb->set(nonceKey.data(), nonce.data());

            // Create the response
            UtlString hostIp;
            OsSocket::getHostIp(&hostIp);
            response = new HttpMessage();
            response->setRequestUnauthorized(&request,
                                HTTP_DIGEST_AUTHENTICATION,
                                mRealm.data(),
                                nonce.data(),
                                nonceKey.data(), // authenticationOpaque
                                hostIp.data()); // domain

                        const char* text = "Unauthorized request";
            HttpBody* body = new HttpBody(text, -1, CONTENT_TYPE_TEXT_PLAIN);
            response->setBody(body);
            response->setContentType(CONTENT_TYPE_TEXT_PLAIN);
            response->setContentLength(strlen(text));
                                nonceSeed.remove(0);
                                hostIp.remove(0);
        }
        user.remove(0);
        nonce.remove(0);
        nonceKey.remove(0);
        userPasswordDigest.remove(0);
    }
    else if(mpUserPasswordBasicDb && !mpUserPasswordBasicDb->isEmpty())
    {
        UtlString user;
        UtlString msgPassword;
        UtlString dbPassword;

        request.getBasicAuthorizationData(&user, &msgPassword);

        OsStatus userFound = mpUserPasswordBasicDb->get(user.data(),
            dbPassword);


        // Passwords stored as digest in user-config.
        // Must convert passwords to encrypted ones also
        UtlString digestPassword ;
        UtlString realm(PASSWORD_SECRET) ;
        HttpMessage::buildMd5UserPasswordDigest((const char*) user.data(),
                                                (const char*) realm.data(),
                                                (const char*) msgPassword.data(),
                                                digestPassword) ;


        // The user does not exist or passwords do not match
        if(userFound != OS_SUCCESS ||
            user.isNull() ||
            dbPassword.compareTo(digestPassword) != 0)
        {
            allowRequest = FALSE;

            // Generate a unauthorized response
            UtlString hostIp;
            OsSocket::getHostIp(&hostIp);
            response = new HttpMessage();
            response->setRequestUnauthorized(&request,
                                HTTP_BASIC_AUTHENTICATION,
                                mRealm.data(),
                                NULL, // nonce
                                NULL, // authenticationOpaque
                                hostIp.data()); // domain

                        const char *text = "Unauthorized request";
            HttpBody* body = new HttpBody(text, -1, CONTENT_TYPE_TEXT_PLAIN);
            response->setBody(body);
            response->setContentType(CONTENT_TYPE_TEXT_PLAIN);
            response->setContentLength(strlen(text));

                                hostIp.remove(0);
        }

        // The password matches
        else
        {
            allowRequest = TRUE;
            userId = user;
        }
        user.remove(0);
        msgPassword.remove(0);
        dbPassword.remove(0);
        digestPassword.remove(0);
        realm.remove(0);
    }
    else
    {
       // AJS - Disabled unauthorized access for 0.8.0
       allowRequest = FALSE;
    }

    return(allowRequest);
}

UtlBoolean HttpServer::processRequestIpAddr(const UtlString& remoteIp,
                                      const HttpMessage& request,
                                      HttpMessage*& response)
{
   UtlBoolean isValidIp = FALSE;
   UtlString remoteAddress(remoteIp);
   UtlString matchIp(remoteAddress);

   if(mValidIpAddrList.isEmpty() || mValidIpAddrList.find(&matchIp))
   {
      isValidIp = TRUE;
   }
   else
   {
      response = new HttpMessage();
      response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
      HTTP_FORBIDDEN_CODE, HTTP_FORBIDDEN_TEXT);

      OsSysLog::add(FAC_SIP, PRI_INFO, "HTTP Request from IP address: %s disallowed",
                    remoteAddress.data());

   }
   return  isValidIp ;
}

void HttpServer::processRequest(const HttpMessage& request,
                                HttpMessage*& response,
                                const OsConnectionSocket* connection
                                )
{
    UtlString method;
    response = NULL;
    UtlString userId;

    if(isRequestAuthorized(request, response, userId))
    {
        request.getRequestMethod(&method);
        method.toUpper();
        UtlString uri;
        request.getRequestUri(&uri);
        UtlString uriFileName(uri);
        ssize_t fileNameEnd = -1;
        if(method.compareTo(HTTP_GET_METHOD) == 0)
        {
            fileNameEnd = uriFileName.first('?');
            if(fileNameEnd > 0)
            {
               uriFileName.remove(fileNameEnd);
            }
        }

        UtlString mappedUriFileName;
        ssize_t badCharsIndex = uriFileName.index("..");
        if(badCharsIndex < 0)
        {
           badCharsIndex = uriFileName.index("//");
        }
        if(badCharsIndex >= 0)
        {
            OsSysLog::add(FAC_SIP, PRI_ERR, "HttpServer::processRequest "
                          "Disallowing URI: \"%s\"", uriFileName.data());

            // Disallow relative path names going up for security reasons
            mappedUriFileName.append("/");
        }
        else
        {
            // Map the file name
            mapUri(mUriMaps, uriFileName.data(), mappedUriFileName);
        }

        OsSysLog::add(FAC_SIP, PRI_DEBUG, "HTTP '%s' '%s' mapped to: '%s'",
                      method.data(), uriFileName.data(), mappedUriFileName.data());

        // Build the request context
        HttpRequestContext requestContext(method.data(),
                                          uri.data(),
                                          mappedUriFileName.data(),
                                          NULL,
                                          !userId.isNull() ? userId.data() : NULL,
                                          connection
                                          );

        if(method.compareTo(HTTP_POST_METHOD) == 0)
        {
            //Need to get the CGI/form variables from the body.
            const HttpBody* body = request.getBody();
            if(body  && !body->isMultipart())
            {
                requestContext.extractPostCgiVariables(*body);
            }
        }

        if(   method.compareTo(HTTP_GET_METHOD) == 0
           || method.compareTo(HTTP_POST_METHOD) == 0
           )
        {
            // If there is a request processor for this URI
            RequestProcessor* requestProcessorPtr;

            if(findRequestProcessor(uriFileName.data(), requestProcessorPtr))
            {
                requestProcessorPtr(requestContext, request, response);
            }
            else
            {
                // Check to see whether there is a service for this URI
                HttpService* pService = NULL;
                if (findHttpService(uriFileName.data(), pService))
                {
                    pService->processRequest(requestContext, request, response);
                }
                else if (mAllowMappedFiles)
                {
                    // Use the default request processor
                    processFileRequest(requestContext, request, response);
                }
                else
                {
                   // This is not allowed, but there is no reason to tell them that
                   processFileNotFound(requestContext, request, response);
                }
            }
        }
        else if(method.compareTo(HTTP_PUT_METHOD) == 0)
        {
            processPutRequest(requestContext, request, response);
        }
        else
        {
           processNotSupportedRequest(requestContext, request, response);
        }
    }
}

/// set permission for access to mapped file names
void HttpServer::allowFileAccess(bool fileAccess ///< true => allow access, false => disallow access
                                 )
{
   mAllowMappedFiles = fileAccess;
}

void HttpServer::processFileRequest(const HttpRequestContext& requestContext,
                                    const HttpMessage& request,
                                    HttpMessage*& response)
{
    UtlString uri;
    request.getRequestUri(&uri);
    UtlString uriFileName;
    UtlString method;

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_MAPPED_FILE,
        uriFileName);
    request.getRequestMethod(&method);

    if(!uriFileName.isNull())
    {
        OsSysLog::add(FAC_SIP, PRI_DEBUG, "HttpServer: Trying to open: \"%s\"",
                      uriFileName.data());

        int fileDesc = open(uriFileName.data(), O_BINARY | O_RDONLY, 0);
        if(fileDesc < 0)
        {
            OsSysLog::add(FAC_SIP, PRI_ERR, "HttpServer::processFileRequest"
                          " failed to open '%s' Errno: %d",
                          uriFileName.data(), errno);
        }
        struct stat fileStatInfo;
        if(fileDesc >= 0 && !fstat(fileDesc, &fileStatInfo))
        {
            int fileDescToGet = -1;
            const char* contentType = CONTENT_TYPE_TEXT_PLAIN;

            // If the URI is directory
            if(fileStatInfo.st_mode & S_IFDIR)
            {
                contentType = CONTENT_TYPE_TEXT_HTML;
                // check for index files
                UtlString indexFileName(uriFileName.data());
                if(indexFileName.data()[indexFileName.length() - 1] != '/')
                            indexFileName.append('/');
                indexFileName.append("index.html");
                //HttpMessage::convertToPlatformPath(indexFileName.data(),
                //    indexFileName);

                fileDescToGet = open(indexFileName.data(), O_RDONLY, 0);
                if(fileDescToGet < 0)
                {
                    // Try index.htm
                    indexFileName.remove(indexFileName.length() - 1);

                    fileDescToGet = open(indexFileName.data(), O_RDONLY | O_BINARY, 0);
                }

                // No index files, construct an html list of files
                if(fileDescToGet < 0)
                {
                                        UtlString indexText ;
                                        constructFileList(indexText, uri, uriFileName) ;

                    HttpBody* body = new HttpBody(indexText.data(),
                    indexText.length(), contentType);
                    response = new HttpMessage();
                    response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
                        HTTP_OK_CODE, HTTP_OK_TEXT);
                    response->setBody(body);
                    response->setContentType(contentType);
                    response->setContentLength(indexText.length());
                                                  indexText.remove(0);
                }
                                        indexFileName.remove(0);
            }
            else if(fileStatInfo.st_mode & S_IFREG)
            {
                fileDescToGet = fileDesc;
                // This stuff really ought to be configurable and
                // contained in a list
                const char* fileNamePtr = uriFileName.data();
                ssize_t extensionIndex = uriFileName.last('.');
                if(extensionIndex >= 0)
                {
                    fileNamePtr += extensionIndex + 1;

                    if(strcmp(fileNamePtr, "htm") == 0 ||
                        strcmp(fileNamePtr, "html") == 0)
                    {
                        contentType = CONTENT_TYPE_TEXT_HTML;
                    }
                    else if(strcmp(fileNamePtr, "aif") == 0)
                    {
                        contentType = "application/pingtel";
                    }
                    else if(strcmp(fileNamePtr, "raw") == 0)
                    {
                        contentType = "audio/raw";
                    }
                    else if(strcmp(fileNamePtr, "gif") == 0)
                    {
                        contentType = "image/gif";
                    }
                    else if(strcmp(fileNamePtr, "jar") == 0)
                    {
                        contentType = "application/octet-stream";
                    }
                    else if(strcmp(fileNamePtr, "jpg") == 0 ||
                        strcmp(fileNamePtr, "jpeg") == 0)
                    {
                        contentType = "image/jpeg";
                    }
                    else if(strcmp(fileNamePtr, "wav") == 0)
                    {
                        contentType = "image/wav";
                    }
                                        else if(strcmp(fileNamePtr, "js") == 0)
                                        {
                                                contentType = "application/x-javascript" ;
                                        }
                }
            }

            // If the URI is a file get it
            if(fileDescToGet >= 0)
            {
                char* buffer = new char[HTTP_DEFAULT_SOCKET_BUFFER_SIZE + 1];
                int numBytesRead;
                UtlString fileContents;
                while((numBytesRead = read(fileDescToGet,
                    buffer, HTTP_DEFAULT_SOCKET_BUFFER_SIZE)) > 0)
                {
                    fileContents.append(buffer, numBytesRead);
                }
                HttpBody* body = new HttpBody(fileContents.data(),
                    fileContents.length(), contentType);
                response = new HttpMessage();
                response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
                    HTTP_OK_CODE, HTTP_OK_TEXT);
                response->setBody(body);
                response->setContentType(contentType);
                response->setContentLength(fileContents.length());

               if(fileDescToGet != fileDesc) close(fileDescToGet);

               if(buffer) delete buffer;
               buffer = NULL;
               fileContents.remove(0);
            }

            close(fileDesc);
        }
        // File not found
        else
        {
            processFileNotFound(requestContext, request, response);
        }
    }
}

void HttpServer::constructFileList(UtlString & indexText, UtlString uri, UtlString uriFileName)
{
#ifdef _VXWORKS /* [ */
        char tmpDateBuf[80];
#endif /* _VXWORKS ] */

    indexText.append("<BODY>\n");
    indexText.append("<H3>Contents of ");
    // Use the public uri not the mapped one
    indexText.append(uri.data());
    indexText.append("</H3>\n<TABLE BORDER=0>\n<TR><TD ALIGN=LEFT>\nName\n</TD><TD ALIGN=RIGHT>Size (Bytes)\n</TD><TD ALIGN=RIGHT>Date\n</TD></TR>\n");

#ifdef _VXWORKS /* [ */
    char sizeBuffer[80];
    DIR* uriDir = opendir((char*) uriFileName.data());
    struct dirent* uriDirEntry = NULL;
    struct tm* timeStruct = NULL;


    if(uriFileName.data()[uriFileName.length() - 1] != '/')
            uriFileName.append('/');
    while((uriDirEntry = readdir(uriDir)) != NULL)
    {
        indexText.append("<TR><TD ALIGN=LEFT>\n<A HREF=\"");
        // Use the public uri not the mapped one
        indexText.append(uri.data());
        if(uri.data()[uri.length() - 1] != '/')
            indexText.append('/');
        indexText.append(uriDirEntry->d_name);
        indexText.append("\">");
        indexText.append(uriDirEntry->d_name);
        indexText.append("</A>\n</TD><TD ALIGN=RIGHT>\n");
        UtlString dirEntryFileName(uriFileName);
        dirEntryFileName.append(uriDirEntry->d_name);

                struct stat dirEntryStat;
        stat((char*)dirEntryFileName.data(), &dirEntryStat);

        if(dirEntryStat.st_mode & S_IFDIR)

        {
            indexText.append("directory");
        }
        else
        {
            sprintf(sizeBuffer, "%d", dirEntryStat.st_size);
            indexText.append(sizeBuffer);
                }

                indexText.append("\n</TD><TD ALIGN=RIGHT WIDTH=\"150\">\n");
                timeStruct = localtime(&dirEntryStat.st_ctime);
        sprintf(tmpDateBuf, "  %02d/%02d/%d  %02d:%02d",
                timeStruct->tm_mon+1,timeStruct->tm_mday,1900+timeStruct->tm_year,
                timeStruct->tm_hour,timeStruct->tm_min);

                indexText.append(tmpDateBuf);

        indexText.append("\n</TD></TR>\n");

                dirEntryFileName.remove(0);
    }

    closedir(uriDir);
#endif /* _VXWORKS ] */

#ifdef _WIN32 /* [ */

    char sizeBuffer[80];
        BOOL bFoundFile =  FALSE;
        WIN32_FIND_DATA fileInfo;
        SYSTEMTIME sysTime;
        UtlString searchString =  uriFileName;
        searchString.append("\\*.*");

        HANDLE hFile = FindFirstFile(
                                (char*) searchString.data(),               // file name
                                &fileInfo);  // data buffer

        if (hFile != INVALID_HANDLE_VALUE)
                bFoundFile = TRUE;

    if(uriFileName.data()[uriFileName.length() - 1] != '/')
            uriFileName.append('/');

    while(bFoundFile)
    {
        indexText.append("<TR><TD ALIGN=LEFT>\n<A HREF=\"");
        // Use the public uri not the mapped one
        indexText.append(uri.data());
        if(uri.data()[uri.length() - 1] != '/')
            indexText.append('/');

                indexText.append(fileInfo.cFileName);
        indexText.append("\">");
        indexText.append(fileInfo.cFileName);
        indexText.append("</A>\n</TD><TD ALIGN=RIGHT>\n");

        UtlString dirEntryFileName(uriFileName);
        dirEntryFileName.append(fileInfo.cFileName);

                DWORD fileAttrs = GetFileAttributes((char *)dirEntryFileName.data());


        if(fileAttrs & FILE_ATTRIBUTE_DIRECTORY)

        {
            indexText.append("directory");
        }
        else
        {
            sprintf(sizeBuffer, "%lu", fileInfo.nFileSizeLow);

            indexText.append(sizeBuffer);
                }

                FILETIME ft = fileInfo.ftCreationTime ;
                indexText.append("\n</TD><TD ALIGN=RIGHT WIDTH=\"150\">\n");

                FileTimeToSystemTime(&ft,&sysTime);
                char tmpDateBuf[80];
        sprintf(tmpDateBuf, "  %02d/%02d/%d  %02d:%02d",
                sysTime.wMonth,sysTime.wDay,sysTime.wYear,
                sysTime.wHour,sysTime.wMinute);
                strcat(sizeBuffer,tmpDateBuf);

        indexText.append(tmpDateBuf);


        indexText.append("\n</TD></TR>\n");

                dirEntryFileName.remove(0);

                bFoundFile = FindNextFile(hFile,&fileInfo);
    }


#endif /* _WIN32 ] */

    indexText.append("</TABLE>\n</BODY>\n");



}

void HttpServer::processPutRequest(const HttpRequestContext& requestContext,
                                   const HttpMessage& request,
                                   HttpMessage*& response)
{
    processNotSupportedRequest(requestContext, request, response);
}


void HttpServer::processPostFile(const HttpRequestContext& requestContext,
                                 const HttpMessage& request,
                                 HttpMessage*& response)
{
        UtlString status;
        doPostFile(requestContext, request, response, status);
}

int HttpServer::doPostFile(const HttpRequestContext& requestContext,
                                                   const HttpMessage& request,
                                                   HttpMessage*& response,
                                                   UtlString& status)
{
        int ret = 0;    // ret == 1: success, ret == 0: failed
        status.remove(0);
    response = NULL;
    const HttpBody* body = request.getBody();
    UtlString htmlMessage("<HTML>\n<BODY>\n");

#ifdef TEST_UPLOAD_FILE_DEBUG
    const char* saveBytes;
    ssize_t saveLen;
    body->getBytes(&saveBytes, &saveLen);
    int bodyDumpFileDesc = open("/flash0/postbodyDump.aif", O_WRONLY | O_CREAT | O_TRUNC | O_BINARY,
                    S_IREAD | S_IWRITE);
    ::write(bodyDumpFileDesc, saveBytes, saveLen);
    close(bodyDumpFileDesc);
#endif

    if(body)
    {
        if(body->isMultipart())
        {
            const MimeBodyPart* firstPartFileBody = //firstPartMessage.getBody();
                body->getMultipart(0);
            const char* fileData;
            ssize_t fileDataLength;
            if(firstPartFileBody)
            {
#ifdef TEST_UPLOAD_FILE_DEBUG
                unsigned int bufferCheckSum = 0;
                unsigned int fileCheckSum = 0;
#endif

                firstPartFileBody->getBytes(&fileData, &fileDataLength);

                if (fileDataLength > 0)
                {
                   while (fileDataLength &&
                          (*fileData == ' ' ||
                           *fileData == '\r' ||
                           *fileData == '\n'))
                   {
                      fileData++;
                      fileDataLength--;
                   }
                }


                if (fileDataLength > 0)
                {

                                        UtlString fieldValue;
                                        firstPartFileBody->getPartHeaderValue(HTTP_CONTENT_DISPOSITION_FIELD,
                                                fieldValue);

                                        if(!fieldValue.isNull())
                                        {
                                                NetAttributeTokenizer tokenizer(fieldValue.data());
                                                UtlString tokenName;
                                                UtlString tokenValue;
                                                while(tokenizer.getNextAttribute(tokenName, tokenValue))
                                                {
                                                        tokenName.toUpper();
                     if(tokenName.compareTo("NAME") == 0)
                                                        {
                                                                // The value is the file name use to save the body as

                                                                if(fileDataLength > 0)
                                                                {
                                                                        int fileDesc = open(tokenValue.data(),
                                                                                O_WRONLY | O_CREAT | O_TRUNC | O_BINARY,
                                                                                S_IREAD | S_IWRITE);

                                                                        if(fileDesc >= 0)
                                                                        {
                                                                                ssize_t bytesWritten = write(fileDesc,
                                                                                        (char*)fileData, fileDataLength);
#ifdef TEST_UPLOAD_FILE_DEBUG
                                                                                unsigned int postWriteCheckSum = 0;
                                                                                incrementalCheckSum(&postWriteCheckSum, fileData, fileDataLength);
                                                                                osPrintf("Post write check sum: %d\n", postWriteCheckSum);
#endif

                                                                                close(fileDesc);
                                                                                if(bytesWritten == fileDataLength)
                                                                                {
#ifdef TEST_UPLOAD_FILE_DEBUG
                                                                                        // Open the file again to do the checksum
                                                                                        unsigned int checkSumFileDesc = open(tokenValue.data(), O_BINARY | O_RDONLY, 0);
                                                                                        char checkSumBuffer[2001];
                                                                                        int checkSumBytesRead;
                                                                                        int totalBytesRead = 0;
                                                                                        do
                                                                                        {
                                                                                                checkSumBytesRead = read(checkSumFileDesc, checkSumBuffer, 2000);
                                                                                                totalBytesRead += checkSumBytesRead;
                                                                                                if(checkSumBytesRead > 0) incrementalCheckSum(&fileCheckSum, checkSumBuffer, checkSumBytesRead);

                                                                                        }
                                                                                        while(checkSumBytesRead > 0);
                                                                                        close(checkSumFileDesc);
                                                                                        unsigned int postReadCheckSum = 0;
                                                                                        incrementalCheckSum(&postReadCheckSum, fileData, fileDataLength);
                                                                                        osPrintf("Post read check sum: %d\n", postReadCheckSum);

                                                                                        osPrintf("HttpServer::processPostFile bufferCheckSum: %d fileCheckSum: %d, buffer size: %d, file size: %d\n",
                                                                                                bufferCheckSum, fileCheckSum, fileDataLength, totalBytesRead);
#endif

                                                                                        htmlMessage.append("<H3>Upload Successful</H3>\n");
                                                                                        char buffer[20];
                                                                                        sprintf(buffer, "%zd", bytesWritten);
                                                                                        status = UtlString(buffer);
                                                                                        status.append(" bytes saved as file: ");
                                                                                        status.append(tokenValue.data());
                                                                                        htmlMessage.append(status);
                                                                                        ret = 1;
                                                                                }

                                                                                // Something happened, it did not write the whole file
                                                                                else
                                                                                {
                                                                                        htmlMessage.append("<H3>Upload Failed</H3>\n");
                                                                                        htmlMessage.append("Insufficient file space\n");
                                                                                        char buffer[100];
                                                                                        sprintf(buffer, "<BR>Bytes available: %zd\n<BR>Bytes needed: %zd for file: ",
                                                                                                bytesWritten, fileDataLength);
                                                                                        htmlMessage.append(buffer);
                                                                                        htmlMessage.append(tokenValue.data());
#ifdef _VXWORKS
                                                                                        ::remove(tokenValue.data());
                                                                                        htmlMessage.append("\n<BR>File not saved\n");
#endif

                                                                                        htmlMessage.append("\
<p><b>Troubleshooting</b>\
<p>Before you call Pingtel for customer support, please print this page.  \
You can also follow these troubleshooting suggestions:\
<ul>\
<li>\
Check the size of each file stored on your phone. Click <a href=/cgi/filelist.cgi>here</a> \
to see a list; please print this list.</li>\
\
<br>If the size of any of these files is greater than 5K, a file may have \
been loaded incorrectly.\
<li>\
Verify that your device and user configuration files contain configuration \
parameters:\
</li>\
\
<ul>\
<li>\
Check your <a href=/pinger-config>pinger-config</a> file.\
</li>\
\
<li>\
Check your <a href=/user-config>user-config</a> file.\
</li>\
</ul>\
\
<li>\
If one of these files incorrectly contains some other type of data:</li>\
\
<ul>\
<li>\
Reload your pinger-config file <a href=/cgi/config.cgi>here</a>.</li>\
\
<li>\
Reload your user-config file <a href=/cgi/config.cgi>here</a>.</li>\
</ul>\
\
<li>\
If you have loaded audio files onto your phone, check the file size.</li>\
\
<ul>\
<li>\
An audio file should be no greater than 100K.</li>\
\
<li>\
If the file is larger, replace it with a smaller file <a href=/cgi/config.cgi>here</a>.\
</li>\
</ul>\
</ul>\
If you need further assistance, please contact Pingtel at <a href=mailto:custsup@pingtel.com>custsupp@pingtel.com</a>.");

                                                                                }
                                                                        }
                                                                        else
                                                                        {
                                                                                htmlMessage.append("<H3>Upload Failed</H3>\n");
                                                                                htmlMessage.append("Unable to open file \"");
                                                                                htmlMessage.append(tokenValue);
                                                                                htmlMessage.append("\" for write\n");

                           }
                                                                }
                                                                else
                                                                {
                                                                        htmlMessage.append("<H3>Upload Failed</H3>\n");
                                                                        htmlMessage.append("Zero length file");
                        }

                                                                break;
                                                        }
                                                }
                                                if(tokenName.isNull())
                                                {
                                                        htmlMessage.append("<H3>Upload Failed</H3>\n");
                                                        htmlMessage.append("No file name given to save content\n");
                  }
                                                tokenName.remove(0);
                                                tokenValue.remove(0);
                                        }
                                        else
                                        {
                                                htmlMessage.append("<H3>Upload Failed</H3>\n");
                                                htmlMessage.append("Content-Disposition field not found\n");
               }
                                        fieldValue.remove(0);
                                }
                                else
                                {
                                        htmlMessage.append("<H3>Upload Failed</H3>\n");
                                        htmlMessage.append("First part file body contains zero byte data. Please check the filename and try again.\n");
            }
                        }
            else
            {
                                UtlString strMessage = "Possible out of memory condition. Restart and try again.\n";
                htmlMessage.append("<H3>Upload Failed</H3>\n");
                        htmlMessage.append("First part has NO file body.\n");
                htmlMessage.append(strMessage.data());
            }
        }
        else
        {
            htmlMessage.append("<H3>Upload Failed</H3>\n");
            htmlMessage.append("Single part body\n");
        }
    }
    else
    {
                UtlString strMessage = "NO file body. Possible out of memory condition. Restart and try again.\n";
        htmlMessage.append("<H3>Upload Failed</H3>\n");
        htmlMessage.append("NO file body.\n");
        htmlMessage.append(strMessage.data());
    }

    htmlMessage.append("\n<BR><BR>\n<A HREF=\"/\">Home</A>\n</BODY>\n</HTML>\n");

    createHtmlResponse(HTTP_OK_CODE, HTTP_OK_TEXT, htmlMessage.data(),
                       response);


        htmlMessage.remove(0);
        return ret;
}


void HttpServer::processNotSupportedRequest(const HttpRequestContext& requestContext,
                                            const HttpMessage& request,
                                            HttpMessage*& response)
{
    // Method not supported
    response = new HttpMessage();
    response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
        HTTP_UNSUPPORTED_METHOD_CODE,
        HTTP_UNSUPPORTED_METHOD_TEXT);
    const char* text = "<HTML><BODY>Not Implemented</BODY></HTML>\n";
    HttpBody* body = new HttpBody(text, -1,
        CONTENT_TYPE_TEXT_HTML);
    response->setBody(body);
    response->setContentType(CONTENT_TYPE_TEXT_HTML);
            response->setContentLength(strlen(text));
}

void HttpServer::processFileNotFound(const HttpRequestContext& requestContext,
                                     const HttpMessage& request,
                                     HttpMessage*& response)
{
    response = new HttpMessage();
    response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
        HTTP_FILE_NOT_FOUND_CODE,
        HTTP_FILE_NOT_FOUND_TEXT);
    const char* text = "<HTML><BODY>File Not Found</BODY></HTML>\n";
    HttpBody* body = new HttpBody(text, -1 ,
        CONTENT_TYPE_TEXT_HTML);
    response->setBody(body);
    response->setContentType(CONTENT_TYPE_TEXT_HTML);
    response->setContentLength(strlen(text));
}

void HttpServer::processUserNotAuthorized(const HttpRequestContext& requestContext,
                                     const HttpMessage& request,
                                     HttpMessage*& response,
                                     const char* text)
{

    UtlString hostIp;
    OsSocket::getHostIp(&hostIp);

        response = new HttpMessage();
    response->setRequestUnauthorized(&request,
                        HTTP_BASIC_AUTHENTICATION,
                        NULL, // realm
                        NULL, // nonce
                        NULL, // authenticationOpaque
                        hostIp.data()); // domain
        //char* text = "<HTML><BODY>User Not Authorized</BODY></HTML>\n";

        if (text == 0) {
                text = "<HTML><BODY>User Not Authorized</BODY></HTML>\n";
        }

    HttpBody* body = new HttpBody(text, -1 ,
        CONTENT_TYPE_TEXT_HTML);
    response->setBody(body);
    response->setContentType(CONTENT_TYPE_TEXT_HTML);
    response->setContentLength(strlen(text));
        hostIp.remove(0);
}

void HttpServer::testCgiRequest(const HttpRequestContext& requestContext,
                                const HttpMessage& request,
                                HttpMessage*& response)
{
    UtlString url;
    UtlString value;
    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_RAW_URL,
        url);
    UtlString cgiDump("<HTML>\n<TITLE>\n");
    cgiDump.append(url);
    cgiDump.append(" dump\n</TITLE>\n<BODY>\n<H3>Environment Variables\n</H3>\n");
    cgiDump.append("<TABLE BORDER=1>\n<TR>\n<TH ALIGN=LEFT>Name</TH>\n<TH ALIGN=LEFT>Value</TH>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_RAW_URL,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_RAW_URL</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_UNMAPPED_FILE,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_UNMAPPED_FILE</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_MAPPED_FILE,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_MAPPED_FILE</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_QUERY_STRING,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_QUERY_STRING</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_SERVER_NAME,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_SERVER_NAME</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_REQUEST_METHOD,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_REQUEST_METHOD</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n");

    requestContext.getEnvironmentVariable(HttpRequestContext::HTTP_ENV_USER,
        value);
    cgiDump.append("<TR>\n<TD ALIGN=LEFT>HTTP_ENV_USER</TD>\n<TD ALIGN=LEFT>");
    cgiDump.append(value);
    cgiDump.append("</TD>\n</TR>\n</TABLE>\n");


    cgiDump.append("<H3>CGI/Form Variables\n</H3>\n");
    cgiDump.append("<TABLE BORDER=1>\n<TR>\n<TH>Name</TH>\n<TH>Value</TH>\n</TR>\n");

    int index = 0;
    UtlString name;
    while(requestContext.getCgiVariable(index, name, value))
    {
      cgiDump.append("<TR>\n<TD  ALIGN=LEFT>");
        cgiDump.append(name);
        cgiDump.append("</TD>\n<TD ALIGN=LEFT>");
        cgiDump.append(value);
        cgiDump.append("</TD>\n</TR>\n");
        index++;
    }
    cgiDump.append("</TABLE>\n");

    createHtmlResponse(HTTP_OK_CODE, HTTP_OK_TEXT, cgiDump.data(), response);

    url.remove(0);
    value.remove(0);
    cgiDump.remove(0);
    name.remove(0);
}

void HttpServer::createHtmlResponse(int responseCode, const char* responseCodeText,
                   const char* htmlBodyText, HttpMessage*& response)
{
    response = new HttpMessage();
    response->setResponseFirstHeaderLine(HTTP_PROTOCOL_VERSION,
        responseCode,
        responseCodeText);
    HttpBody* body = new HttpBody(htmlBodyText, -1,
        CONTENT_TYPE_TEXT_HTML);
    response->setBody(body);
    response->setContentType(CONTENT_TYPE_TEXT_HTML);
    response->setContentLength(strlen(htmlBodyText));
}

void HttpServer::addUriMap(const char* fromUri, const char* toUri)
{
   OsSysLog::add(FAC_SIP, PRI_DEBUG, "HttpServer::addUriMap '%s' to '%s'",
                 fromUri, toUri);
   
    mUriMaps.set(fromUri, toUri);
}

void HttpServer::addRequestProcessor(const char* fileUrl,
                                     RequestProcessor* requestProcessor
                                     )
{
   OsSysLog::add(FAC_SIP, PRI_DEBUG, "HttpServer::addRequestProcessor '%s' to %p",
                 fileUrl, requestProcessor);

   addUriMap( fileUrl, fileUrl );
   
   UtlString* name = new UtlString(fileUrl);
   UtlVoidPtr* value = new UtlVoidPtr((void*)requestProcessor);
   mRequestProcessorMethods.insertKeyAndValue(name, value);
}

void HttpServer::addHttpService(const char* fileUrl, HttpService* service)
{
   OsSysLog::add(FAC_SIP, PRI_DEBUG, "HttpServer::addHttpService '%s' to %p",
                 fileUrl, service);

   UtlString* name = new UtlString(fileUrl);
    UtlVoidPtr* value = new UtlVoidPtr(service);
    mHttpServices.insertKeyAndValue(name, value);
}

/* ============================ ACCESSORS ================================= */

void HttpServer::setPasswordDigest(const char* user, const char* password,
                                   UtlString& userPasswordDigest)
{
    userPasswordDigest.remove(0);
    if(user && *user && mpUserPasswordDigestDb)
    {

        HttpMessage::buildMd5UserPasswordDigest(user, mRealm, password,
            userPasswordDigest);
        mpUserPasswordDigestDb->set(user, userPasswordDigest.data());

        }

    else if(user && *user && password && mpUserPasswordBasicDb)
    {
                // Basic Digest uses encrypted password in user-config
        HttpMessage::buildMd5UserPasswordDigest(user, mRealm, password,
            userPasswordDigest);
        mpUserPasswordBasicDb->set(user, userPasswordDigest.data());
        userPasswordDigest = password;

    }
}

/**
 * added by Pradeep:
 * This takes an already encrypted password and sets it as the new encrypted password.
 */
void HttpServer::setPasswordDigest(const char* user, const char* passwordDigest)
{

        if(user && *user && mpUserPasswordDigestDb)
    {
                mpUserPasswordDigestDb->set(user, passwordDigest);
        }
        else if(user && *user && passwordDigest && mpUserPasswordBasicDb)
    {
                mpUserPasswordBasicDb->set(user, passwordDigest);
    }


 }


void HttpServer::getDigest(const char* user, const char* password,
                                   UtlString& userPasswordDigest)
{
  HttpMessage::buildMd5UserPasswordDigest(user, mRealm, password,
            userPasswordDigest);
}

void HttpServer::removeUser(const char* user, const char* password)
{
        UtlString strUser(user) ;
    if(user && *user && mpUserPasswordDigestDb)
    {
        mpUserPasswordDigestDb->remove(strUser);
    }

    else if(user && *user && password && mpUserPasswordBasicDb)
    {

                  mpUserPasswordBasicDb->remove(strUser);
    }
        strUser.remove(0);
}

/* ============================ INQUIRY =================================== */

/* //////////////////////////// PROTECTED ///////////////////////////////// */

UtlBoolean HttpServer::findRequestProcessor(const char* fileUri,
                                            RequestProcessor* &requestProcessor
                                            )
{
    UtlString uriCollectable(fileUri);
    UtlVoidPtr* processorCollectable;

    requestProcessor = NULL;
    processorCollectable =
        (UtlVoidPtr*) mRequestProcessorMethods.findValue(&uriCollectable);
    if(processorCollectable)
    {
        requestProcessor = (RequestProcessor*)processorCollectable->getValue();
    }

    return(requestProcessor != NULL);
}

UtlBoolean HttpServer::findHttpService(const char* fileUri, HttpService*& pService)
{
    UtlString uriCollectable(fileUri);
    UtlInt* processorCollectable;

    processorCollectable =
        (UtlInt*) mHttpServices.findValue(&uriCollectable);
    if(processorCollectable)
    {
        pService = (HttpService *) processorCollectable->getValue();
    }

    return(pService != NULL);
}

UtlBoolean HttpServer::mapUri(OsConfigDb& uriMaps, const char* uri, UtlString& mappedUri)
{
    UtlBoolean mapFound = FALSE;

    if(uri)
    {
        UtlString originalUri(uri);
        UtlString mapFromUri(uri);
        UtlString mapToUri;
        ssize_t dirSeparatorIndex;

        OsSysLog::add(FAC_SIP, PRI_DEBUG, "HttpServer::mapUri looking for \"%s\"",
                      mapFromUri.data());

        do
        {
            uriMaps.get(mapFromUri, mapToUri);
            if(!mapToUri.isNull())
            {
                mappedUri.remove(0);
                mappedUri.append(mapToUri.data());
                if(mappedUri.data()[mappedUri.length() - 1] != '/' &&
                    uri[mapFromUri.length()] != '/' &&
                    mapFromUri.length() < originalUri.length())
                {
                    // Need a directory seporator
                    mappedUri.append('/');
                }
                mappedUri.append(&(originalUri.data()[mapFromUri.length()]));
                mapFound = TRUE;
                break;
            }
            dirSeparatorIndex = mapFromUri.last('/');
            if(dirSeparatorIndex == 0 && mapFromUri.length() > 1)
            {
               mapFromUri.remove(1);
            }
            else if(dirSeparatorIndex >= 0)
            {
               mapFromUri.remove(dirSeparatorIndex);
            }
            else
            {
               break;
            }
        } while(!mapFound && !mapFromUri.isNull() != 0);
    }

    OsSysLog::add(FAC_SIP, PRI_DEBUG, "Map to uri: \"%s\"", mappedUri.data());

    return(mapFound);
}
/* //////////////////////////// PRIVATE /////////////////////////////////// */


/* ============================ FUNCTIONS ================================= */
