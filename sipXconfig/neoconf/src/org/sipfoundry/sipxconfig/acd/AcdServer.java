/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.acd;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang.enums.ValuedEnum;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.service.SipxPresenceService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.setting.SettingEntry;

public class AcdServer extends AcdComponent {
    public static final Log LOG = LogFactory.getLog(AcdServer.class);
    public static final String OBJECT_CLASS = "acd-server";

    public static final String BEAN_NAME = "acdServer";

    static final String ADMIN_STATE = "acd-server/administrative-state";

    static final String UDP_PORT = "acd-server/udp-port";

    static final String DOMAIN = "acd-server/domain";

    static final String PRESENCE_SERVER_URI = "acd-server/presence-server-uri";

    static final String PRESENCE_SERVICE_URI = "acd-server/presence-service-uri";

    private static final String KEY_URI = "uri";

    private static final String KEY_NAME = "name";

    // TODO: only needed to create AcdAudio - we may be able to remove this dependency
    private transient AcdContext m_acdContext;

    private transient SipxServiceManager m_sipxServiceManager;

    private int m_port;

    private int m_agentPort;

    private String m_host;

    private Set m_lines = new HashSet();

    private Set m_queues = new HashSet();

    private Set m_agents = new HashSet();

    private int m_maxQueues;

    private int m_maxAgents;

    private int m_maxLines;

    public AcdServer() {
        super("sipxacd-server.xml", OBJECT_CLASS);
    }

    public Set getLines() {
        return m_lines;
    }

    public void setLines(Set lines) {
        m_lines = lines;
    }

    public Set getQueues() {
        return m_queues;
    }

    public void setQueues(Set queues) {
        m_queues = queues;
    }

    public Set getAgents() {
        return m_agents;
    }

    public void setAgents(Set agents) {
        m_agents = agents;
    }

    public void insertLine(AcdLine acdLine) {
        Collection lines = getLines();
        if (lines.size() >= m_maxLines) {
            throw new LicenseException(acdLine.getName(), m_maxLines);
        }
        acdLine.setAcdServer(this);
        m_lines.add(acdLine);
    }

    public void removeLine(AcdLine line) {
        line.setAcdServer(null);
        m_lines.remove(line);
    }

    public void insertQueue(AcdQueue acdQueue) {
        Collection queues = getQueues();
        if (queues.size() >= m_maxQueues) {
            throw new LicenseException(acdQueue.getName(), m_maxQueues);
        }
        acdQueue.setAcdServer(this);
        queues.add(acdQueue);
    }

    public void removeQueue(AcdQueue queue) {
        queue.setAcdServer(null);
        m_queues.remove(queue);
    }

    public void insertAgent(AcdAgent acdAgent) {
        Collection agents = getAgents();
        if (agents.size() >= m_maxAgents) {
            throw new LicenseException(acdAgent.getName(), m_maxAgents);
        }
        acdAgent.setAcdServer(this);
        agents.add(acdAgent);
    }

    public void removeAgent(AcdAgent agent) {
        agent.setAcdServer(null);
        getAgents().remove(agent);
    }

    void setAdminState(State status) {
        setSettingValue(ADMIN_STATE, status.getValueAsString());
    }

    String getServiceUri() {
        Object[] params = new Object[] {
            m_host, Integer.toString(m_port)
        };
        return MessageFormat.format("http://{0}:{1}/RPC2", params);
    }

    public static class AcdServerDefaults {
        private AcdServer m_server;
        private SipxPresenceService m_presenceService;

        AcdServerDefaults(AcdServer server) {
            m_server = server;
            m_presenceService = (SipxPresenceService) m_server.m_sipxServiceManager
                    .getServiceByBeanId(SipxPresenceService.BEAN_ID);
        }

        @SettingEntry(path = DOMAIN)
        public String getDomain() {
            return m_server.getCoreContext().getDomainName();
        }

        @SettingEntry(path = PRESENCE_SERVER_URI)
        public String getPresenceServerUri() {
            return m_presenceService.getPresenceServerUri();
        }

        @SettingEntry(path = PRESENCE_SERVICE_URI)
        public String getPresenceServiceUri() {
            return m_presenceService.getPresenceServiceUri();
        }
    }

    public void deploy(XmlRpcSettings xmlRpc) {
        Set serverUris = xmlRpc.getAll(OBJECT_CLASS, KEY_URI);
        boolean newServer = serverUris.isEmpty();

        if (newServer) {
            setAdminState(State.STANDBY);
            create(xmlRpc);
        }

        Set queueUris = xmlRpc.getAll(AcdQueue.OBJECT_CLASS, KEY_URI);
        Set audioFiles = new HashSet();
        for (Iterator i = getQueues().iterator(); i.hasNext();) {
            AcdQueue queue = (AcdQueue) i.next();
            audioFiles.addAll(queue.getAudioFiles());
            boolean present = queueUris.remove(queue.calculateUri());
            queue.smartCreate(xmlRpc, present);
        }

        Set lineUris = xmlRpc.getAll(AcdLine.OBJECT_CLASS, KEY_URI);
        for (Iterator i = getLines().iterator(); i.hasNext();) {
            AcdLine line = (AcdLine) i.next();
            boolean present = lineUris.remove(line.calculateUri());
            line.smartCreate(xmlRpc, present);
        }

        Set agentUris = xmlRpc.getAll(AcdAgent.OBJECT_CLASS, KEY_URI);
        for (Iterator i = getAgents().iterator(); i.hasNext();) {
            AcdAgent agent = (AcdAgent) i.next();
            boolean present = agentUris.remove(agent.calculateUri());
            agent.smartCreate(xmlRpc, present);
        }

        Set audioNames = xmlRpc.getAll(AcdAudio.OBJECT_CLASS, KEY_NAME);
        for (Iterator i = audioFiles.iterator(); i.hasNext();) {
            String fileName = (String) i.next();
            if (!audioNames.remove(fileName)) {
                AcdAudio audio = m_acdContext.newAudio();
                audio.setAudioFileName(m_acdContext.getAudioServerUrl(), fileName);
                audio.create(xmlRpc);
            }
        }

        xmlRpc.deleteAll(AcdAudio.OBJECT_CLASS, audioNames, KEY_NAME);
        xmlRpc.deleteAll(AcdLine.OBJECT_CLASS, lineUris, KEY_URI);
        xmlRpc.deleteAll(AcdQueue.OBJECT_CLASS, queueUris, KEY_URI);
        xmlRpc.deleteAll(AcdAgent.OBJECT_CLASS, agentUris, KEY_URI);

        setAdminState(State.ACTIVE);
        set(xmlRpc);
    }

    /**
     * Check neoconf/etc/commserver/sipxacd-sever server-status type before changing
     */
    public static class State extends ValuedEnum {
        public static final State ACTIVE = new State("Active", 1);
        public static final State STANDBY = new State("Standby", 2);
        public static final State DOWN = new State("Down", 3);

        protected State(String name, int value) {
            super(name, value);
        }

        public String getValueAsString() {
            return Integer.toString(getValue());
        }
    }

    public String getHost() {
        return m_host;
    }

    public void setHost(String host) {
        m_host = host;
    }

    public int getPort() {
        return m_port;
    }

    public void setPort(int port) {
        m_port = port;
    }

    public int getAgentPort() {
        return m_agentPort;
    }

    public void setAgentPort(int agentPort) {
        m_agentPort = agentPort;
    }

    public void setMaxAgents(int maxAgents) {
        m_maxAgents = maxAgents;
    }

    public void setMaxQueues(int maxQueues) {
        m_maxQueues = maxQueues;
    }

    public void setMaxLines(int maxLines) {
        m_maxLines = maxLines;
    }

    public void setAcdContext(AcdContext acdContext) {
        m_acdContext = acdContext;
    }

    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_sipxServiceManager = sipxServiceManager;
    }

    /**
     * SIP port is for now hard coded as UDP port
     * 
     * @return port on which server accepts SIP request
     */
    public int getSipPort() {
        Integer port = (Integer) getSettingTypedValue(UDP_PORT);
        return port.intValue();
    }

    public Serializable getAcdServerId() {
        return getId();
    }

    @Override
    public void initialize() {
        addDefaultBeanSettingHandler(new AcdServerDefaults(this));
    }
}
