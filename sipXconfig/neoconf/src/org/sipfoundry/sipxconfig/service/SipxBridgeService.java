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

import org.sipfoundry.sipxconfig.admin.dialplan.sbc.SbcDeviceManager;

public class SipxBridgeService extends SipxService {
    public static final String BEAN_ID = "sipxBridgeService";

    private SbcDeviceManager m_sbcDeviceManager;

    public SbcDeviceManager getSbcDeviceManager() {
        return m_sbcDeviceManager;
    }

    public void setSbcDeviceManager(SbcDeviceManager sbcDeviceManager) {
        m_sbcDeviceManager = sbcDeviceManager;
    }

    public void tearDown() {
        if (null != getSbcDeviceManager().getBridgeSbc()) {
            getSbcDeviceManager().deleteSbcDevice(getSbcDeviceManager().getBridgeSbc().getId());
        }
    }
}
