/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.phone.polycom;

import org.sipfoundry.sipxconfig.device.DeviceVersion;
import org.sipfoundry.sipxconfig.phone.PhoneModel;

/**
 * Static differences in polycom phone models
 */
public final class PolycomModel extends PhoneModel {
    
    /** Firmware 1.6 */
    public static final DeviceVersion VER_1_6 = new DeviceVersion(PolycomPhone.BEAN_ID, "1.6");

    /** Firmware 2.0 */
    public static final DeviceVersion VER_2_0 = new DeviceVersion(PolycomPhone.BEAN_ID, "2.0");

    
    public PolycomModel() {
        super(PolycomPhone.BEAN_ID);
        setVersions(new DeviceVersion[] { 
            VER_1_6, 
            VER_2_0
        });
        setEmergencyConfigurable(true);
    }    
}
