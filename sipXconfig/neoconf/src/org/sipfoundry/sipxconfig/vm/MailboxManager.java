/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.vm;

import java.util.List;

import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.vm.attendant.PersonalAttendant;

public interface MailboxManager {

    boolean isEnabled();

    List<Voicemail> getVoicemail(Mailbox mailbox, String folder);

    String getMailstoreDirectory();

    Mailbox getMailbox(String userId);

    void deleteMailbox(String userId);

    void saveMailboxPreferences(Mailbox mailbox, MailboxPreferences preferences);

    MailboxPreferences loadMailboxPreferences(Mailbox mailbox);

    void saveDistributionLists(Mailbox mailbox, DistributionList[] lists);

    DistributionList[] loadDistributionLists(Mailbox mailbox);

    void markRead(Mailbox mailbox, Voicemail voicemail);

    void move(Mailbox mailbox, Voicemail voicemail, String destinationFolderId);

    void delete(Mailbox mailbox, Voicemail voicemail);

    void triggerSipNotify(Mailbox mailbox);

    PersonalAttendant loadPersonalAttendantForUser(User user);

    void removePersonalAttendantForUser(User user);

    void storePersonalAttendant(PersonalAttendant pa);

    void clearPersonalAttendants();
}
