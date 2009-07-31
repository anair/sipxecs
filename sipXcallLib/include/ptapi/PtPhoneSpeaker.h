//
// Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
// Contributors retain copyright to elements licensed under a Contributor Agreement.
// Licensed to the User under the LGPL license.
//
//
// $$
////////////////////////////////////////////////////////////////////////
//////


#ifndef _PtPhoneSpeaker_h_
#define _PtPhoneSpeaker_h_

// SYSTEM INCLUDES
// APPLICATION INCLUDES
#include "ptapi/PtComponent.h"
#include "os/OsTime.h"
#include "os/OsProtectEventMgr.h"
// DEFINES
// MACROS
// EXTERNAL FUNCTIONS
// EXTERNAL VARIABLES
// CONSTANTS
// STRUCTS
// TYPEDEFS
// FORWARD DECLARATIONS
class TaoClientTask;

//:The PtPhoneSpeaker class models a phone speaker.

class PtPhoneSpeaker : public PtComponent
{
/* //////////////////////////// PUBLIC //////////////////////////////////// */
public:

   enum VolumeLevel
   {
      OFF    = 0,
      MIDDLE = 5,
      FULL   = 10
   };
   //!enumcode: OFF - The speaker is turned off
   //!enumcode: MIDDLE  - The speaker volume level is set to the middle of its range
   //!enumcode: FULL - The speaker volume is set to its maximum level

/* ============================ CREATORS ================================== */
   PtPhoneSpeaker();
     //:Default constructor

   PtPhoneSpeaker(TaoClientTask *pClient);

   PtPhoneSpeaker(const PtPhoneSpeaker& rPtPhoneSpeaker);
     //:Copy constructor

   PtPhoneSpeaker& operator=(const PtPhoneSpeaker& rhs);
     //:Assignment operator
   virtual
   ~PtPhoneSpeaker();
     //:Destructor


/* ============================ MANIPULATORS ============================== */
   virtual PtStatus setVolume(int volume);
     //:Sets the speaker volume to a value between OFF and FULL (inclusive).
     //!param: volume - The speaker volume level
     //!retcode: PT_SUCCESS - Success
     //!retcode: PT_INVALID_ARGUMENT - Invalid volume level
     //!retcode: PT_PROVIDER_UNAVAILABLE - The provider is not available

/* ============================ ACCESSORS ================================= */

   virtual PtStatus getVolume(int& rVolume);
     //:Sets <i>rVolume</i> to the current speaker volume level.
     //!retcode: PT_SUCCESS - Success
     //!retcode: PT_PROVIDER_UNAVAILABLE - The provider is not available



   virtual PtStatus getNominalVolume(int& rVolume);
     //:Sets <i>rVolume</i> to the default speaker volume level.
     //!retcode: PT_SUCCESS - Success
     //!retcode: PT_PROVIDER_UNAVAILABLE - The provider is not available


/* ============================ INQUIRY =================================== */

/* //////////////////////////// PROTECTED ///////////////////////////////// */
protected:
        TaoClientTask   *mpClient;

        OsTime          mTimeOut;
/* //////////////////////////// PRIVATE /////////////////////////////////// */
private:
        OsProtectEventMgr *mpEventMgr;


};

/* ============================ INLINE METHODS ============================ */

#endif  // _PtPhoneSpeaker_h_
