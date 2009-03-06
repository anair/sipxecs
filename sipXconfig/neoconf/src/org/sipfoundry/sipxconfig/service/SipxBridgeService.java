/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.service;

import java.util.ArrayList;
import java.util.List;

import org.sipfoundry.sipxconfig.admin.ConfigurationFile;
import org.sipfoundry.sipxconfig.admin.dialplan.sbc.SbcDeviceManager;
import org.sipfoundry.sipxconfig.admin.dialplan.sbc.bridge.BridgeSbc;
import org.sipfoundry.sipxconfig.device.InMemoryConfiguration;

public class SipxBridgeService extends SipxService {
    public static final String BEAN_ID = "sipxBridgeService";

    private SbcDeviceManager m_sbcDeviceManager;

    public void setSbcDeviceManager(SbcDeviceManager sbcDeviceManager) {
        m_sbcDeviceManager = sbcDeviceManager;
    }

    @Override
    public List< ? extends ConfigurationFile> getConfigurations() {
        List <InMemoryConfiguration> configurations = new ArrayList<InMemoryConfiguration>();
        List <BridgeSbc> sbcs = m_sbcDeviceManager.getBridgeSbcs();
        for (BridgeSbc sbc : sbcs) {
            configurations.addAll(sbc.getConfigurations());
        }

        return configurations;
    }
}
