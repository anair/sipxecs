/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.gateway;

import org.sipfoundry.sipxconfig.admin.dialplan.sbc.SbcDevice;

public class SipTrunk extends Gateway {
    public static final String BEAN_ID = "gwSipTrunk";

    public SipTrunk() {
    }

    public SipTrunk(GatewayModel model) {
        super(model);
    }

    @Override
    public String getRoute() {
        SbcDevice sbcDevice = getSbcDevice();
        if (sbcDevice != null) {
            return getSbcDevice().getAddress();            
        }
        return null;
    }
}
