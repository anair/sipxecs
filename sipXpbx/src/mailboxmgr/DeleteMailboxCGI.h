//
//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
// $$
//////////////////////////////////////////////////////////////////////////////

#ifndef DELETEMAILBOXCGI_H
#define DELETEMAILBOXCGI_H

// SYSTEM INCLUDES
//#include <...>

// APPLICATION INCLUDES
#include "os/OsDefs.h"
#include "mailboxmgr/DeleteMailboxCGI.h"

// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS
// FORWARD DECLARATIONS

/**
 * Mailbox Class
 *
 * @author John P. Coffey
 * @version 1.0
 */
class DeleteMailboxCGI : public VXMLCGICommand
{
public:
    /**
     * Ctor
     */
    DeleteMailboxCGI ( const UtlString& mailboxIdentity );

    /**
     * Virtual Dtor
     */
    virtual ~DeleteMailboxCGI ();

    /** This does the work */
    virtual OsStatus execute (UtlString* out = NULL);

protected:

private:
    const UtlString m_mailboxIdentity;
};

#endif //DELETEMAILBOXCGI_H
