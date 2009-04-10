/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.conference;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.sipfoundry.sipxconfig.common.LazyDaemon;

public class LazyConferenceBridgeProvisioningImpl implements ConferenceBridgeProvisioning {
    private Set<Integer> m_servers = new HashSet<Integer>();

    private ConferenceBridgeProvisioning m_target;

    private int m_sleepInterval;

    public void init() {
        Worker worker = new Worker();
        worker.start();
    }

    /* could be private - workaround for checkstyle bug */
    protected synchronized void waitForWork() throws InterruptedException {
        if (m_servers.isEmpty()) {
            wait();
        }
    }

    private synchronized Set<Integer> getTasks() {
        if (m_servers.isEmpty()) {
            return Collections.emptySet();
        }
        Set oldTasks = m_servers;
        m_servers = new HashSet<Integer>();
        return oldTasks;
    }

    private synchronized void addTasks(Set<Integer> tasks) {
        m_servers.addAll(tasks);
    }

    public void setSleepInterval(int sleepInterval) {
        m_sleepInterval = sleepInterval;
    }

    /**
     * Worker thread: waits till someone needs to be done, sleeps for a bit and does it.
     */
    private class Worker extends LazyDaemon {
        public Worker() {
            super("Conference Bridge Provisioning thread", m_sleepInterval);
        }

        @Override
        protected void waitForWork() throws InterruptedException {
            LazyConferenceBridgeProvisioningImpl.this.waitForWork();
        }

        @Override
        protected boolean work() {
            Set<Integer> tasks = getTasks();
            try {
                for (Iterator<Integer> i = tasks.iterator(); i.hasNext();) {
                    int id = i.next();
                    i.remove();
                    m_target.deploy(id);
                }
                return true;
            } finally {
                // make sure that all tasks that were not removed are re-added to original taks
                // list
                addTasks(tasks);
            }
        }
    }

    public void setTarget(ConferenceBridgeProvisioning target) {
        m_target = target;
    }

    /**
     * Add bridge id to the set and notify worker thread
     */
    public synchronized void deploy(int bridgeId) {
        m_servers.add(bridgeId);
        notify();
    }
}
