/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */

package org.sipfoundry.sipxconfig.admin.update;

import java.util.List;

import org.sipfoundry.sipxconfig.admin.commserver.Location;

public interface UpdateApi {
    void installUpdates();
    String getCurrentVersion();
    List<PackageUpdate> getAvailableUpdates();
    void restart(Location location);
}
