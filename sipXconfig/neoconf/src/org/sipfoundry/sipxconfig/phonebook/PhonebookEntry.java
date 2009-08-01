/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.phonebook;


public interface PhonebookEntry {
    public String getFirstName();
    public String getLastName();
    public String getNumber();

    public AddressBookEntry getAddressBookEntry();
}
