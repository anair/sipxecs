/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.admin.commserver;

import java.util.List;
import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.hivemind.util.PropertyUtils;
import org.apache.tapestry.test.Creator;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext.Command;
import org.sipfoundry.sipxconfig.admin.commserver.Process;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessModel.ProcessName;

public class RestartReminderPanelTest extends TestCase {
    private final static Process TEST_PROC[] = {
        new Process(ProcessName.PROXY), new Process(ProcessName.PRESENCE_SERVER)
    };

    private Creator m_pageMaker = new Creator();
    private RestartReminderPanel m_restartReminder;

    protected void setUp() throws Exception {
        m_restartReminder = (RestartReminderPanel) m_pageMaker
                .newInstance(RestartReminderPanel.class);
    }

    public void testGetProcessesToRestartLater() {
        m_restartReminder.setRestartLater(true);

        m_restartReminder.setProcesses(null);
        assertNull(m_restartReminder.getProcessesToRestart());

        m_restartReminder.setProcesses(TEST_PROC);
        assertNull(m_restartReminder.getProcessesToRestart());
    }

    public void testGetProcessesToRestartNow() {
        IMocksControl contextCtrl = EasyMock.createControl();
        SipxProcessContext context = contextCtrl.createMock(SipxProcessContext.class);
        context.getRestartable();
        contextCtrl.andReturn(Arrays.asList(TEST_PROC));

        contextCtrl.replay();
        
        m_restartReminder.setRestartLater(false);
        PropertyUtils.write(m_restartReminder, "sipxProcessContext", context);

        m_restartReminder.setProcesses(null);
        List processesToRestart = m_restartReminder.getProcessesToRestart();
        assertEquals(TEST_PROC.length, processesToRestart.size());

        m_restartReminder.setProcesses(TEST_PROC);
        processesToRestart = m_restartReminder.getProcessesToRestart();
        assertEquals(TEST_PROC.length, processesToRestart.size());
        for (int i = 0; i < TEST_PROC.length; i++) {
            assertEquals(TEST_PROC[i], processesToRestart.get(i));
        }

        contextCtrl.verify();
    }

    public void testRestartLater() throws Exception {
        IMocksControl contextCtrl = EasyMock.createControl();
        SipxProcessContext context = contextCtrl.createMock(SipxProcessContext.class);
        contextCtrl.replay();

        m_restartReminder.setRestartLater(true);
        PropertyUtils.write(m_restartReminder, "sipxProcessContext", context);

        m_restartReminder.restart();

        contextCtrl.verify();
    }

    public void testRestartNow() throws Exception {
        Process[] p = new Process[] { new Process("DummyProcess") };
        List l = Arrays.asList(p);
        IMocksControl contextCtrl = EasyMock.createControl();
        SipxProcessContext context = contextCtrl.createMock(SipxProcessContext.class);
        context.getRestartable();
        contextCtrl.andReturn(l);
        context.manageServices(l, Command.RESTART);
        contextCtrl.replay();

        m_restartReminder.setRestartLater(false);
        PropertyUtils.write(m_restartReminder, "sipxProcessContext", context);

        m_restartReminder.restart();

        contextCtrl.verify();
    }
}
