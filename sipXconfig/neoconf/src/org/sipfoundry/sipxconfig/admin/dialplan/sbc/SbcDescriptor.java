/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 *
 */
package org.sipfoundry.sipxconfig.admin.dialplan.sbc;

import org.sipfoundry.sipxconfig.device.DeviceDescriptor;

public class SbcDescriptor extends DeviceDescriptor {
    /*defines the maximum number of sbc devices that can be created
     *-1 means unlimited
     */
    private int m_maxAllowed = -1;

    public int getMaxAllowed() {
        return m_maxAllowed;
    }

    public void setMaxAllowed(int maxAllowed) {
        m_maxAllowed = maxAllowed;
    }

}
