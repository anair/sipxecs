//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
// $$
////////////////////////////////////////////////////////////////////////
//////


#ifdef HAVE_GIPS /* [ */

#ifndef _MpdGIPSiPCMU_h_
#define _MpdGIPSiPCMU_h_

// SYSTEM INCLUDES

// APPLICATION INCLUDES
#include "mp/MpDecoderBase.h"
#include "mp/GIPS/gips_typedefs.h"

// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS

//:Derived class for GIPS iPCMU decoder.
class MpdGIPSiPCMU: public MpDecoderBase
{
/* //////////////////////////// PUBLIC //////////////////////////////////// */
public:

/* ============================ CREATORS ================================== */
   MpdGIPSiPCMU(int payloadType);
     //:Constructor
     // Returns a new decoder object.
     //!param: payloadType - (in) RTP payload type associated with this decoder

   virtual ~MpdGIPSiPCMU(void);
     //:Destructor

   virtual OsStatus initDecode(MpConnection* pConnection);
     //:Initializes a codec data structure for use as a decoder
     //!param: pConnection - (in) Pointer to the MpConnection container
     //!retcode: OS_SUCCESS - Success
     //!retcode: OS_NO_MEMORY - Memory allocation failure

   virtual OsStatus freeDecode(void);
     //:Frees all memory allocated to the decoder by <i>initDecode</i>
     //!retcode: OS_SUCCESS - Success
     //!retcode: OS_DELETED - Object has already been deleted

/* ============================ MANIPULATORS ============================== */

/* ============================ ACCESSORS ================================= */

/* ============================ INQUIRY =================================== */

/* //////////////////////////// PRIVATE /////////////////////////////////// */
private:
   static const MpCodecInfo smCodecInfo;  // static information about the codec
   EG711U_inst* pDecoderState;
   JB_inst* mpJBState;
};

#endif  // _MpdGIPSiPCMU_h_
#endif /* HAVE_GIPS ] */
