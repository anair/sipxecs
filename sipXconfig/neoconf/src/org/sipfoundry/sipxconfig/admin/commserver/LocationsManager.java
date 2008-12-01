/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 *
 */
package org.sipfoundry.sipxconfig.admin.commserver;

public interface LocationsManager {
    static final String CONTEXT_BEAN_NAME = "locationsManager";
    Location[] getLocations();
    Location getLocation(int id);
    Location getLocationByFqdn(String fqdn);
    Location getPrimaryLocation();
    void storeLocation(Location location);
    void deleteLocation(Location location);
}
