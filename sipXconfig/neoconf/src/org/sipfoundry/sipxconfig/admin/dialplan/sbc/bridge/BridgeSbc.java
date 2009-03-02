/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.admin.dialplan.sbc.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext;
import org.sipfoundry.sipxconfig.admin.commserver.SipxReplicationContext;
import org.sipfoundry.sipxconfig.admin.dialplan.sbc.SbcDevice;
import org.sipfoundry.sipxconfig.device.DeviceDefaults;
import org.sipfoundry.sipxconfig.device.ProfileContext;
import org.sipfoundry.sipxconfig.device.ProfileLocation;
import org.sipfoundry.sipxconfig.device.ReplicatedProfileLocation;
import org.sipfoundry.sipxconfig.gateway.GatewayContext;
import org.sipfoundry.sipxconfig.gateway.SipTrunk;
import org.sipfoundry.sipxconfig.service.SipxBridgeService;
import org.sipfoundry.sipxconfig.setting.Setting;
import org.sipfoundry.sipxconfig.setting.SettingEntry;
import org.springframework.beans.factory.annotation.Required;

public class BridgeSbc extends SbcDevice {

    private GatewayContext m_gatewayContext;

    private SipxProcessContext m_processContext;

    private SipxReplicationContext m_sipxReplicationContext;

    private SipxBridgeService m_sipxBridgeService;

    private LocationsManager m_locationsManager;

    private String m_profileName;

    private String m_profileDirectory;

    @Required
    public void setGatewayContext(GatewayContext gatewayContext) {
        m_gatewayContext = gatewayContext;
    }

    @Required
    public void setProcessContext(SipxProcessContext processContext) {
        m_processContext = processContext;
    }

    public void setSipxReplicationContext(SipxReplicationContext sipxReplicationContext) {
        m_sipxReplicationContext = sipxReplicationContext;
    }

    @Required
    public void setSipxBridgeService(SipxBridgeService sipxBridgeService) {
        m_sipxBridgeService = sipxBridgeService;
    }

    @Required
    public void setLocationsManager(LocationsManager locationsManager) {
        m_locationsManager = locationsManager;
    }

    @Required
    public void setProfileName(String profileName) {
        m_profileName = profileName;
    }

    @Required
    public void setProfileDirectory(String profileDirectory) {
        m_profileDirectory = profileDirectory;
    }

    @Override
    protected Setting loadSettings() {
        return getModelFilesContext().loadModelFile("bridge-sbc.xml", "sipxbridge");
    }

    @Override
    protected ProfileContext createContext() {
        return new Context(this, "sipxbridge/bridge.xml.vm");
    }

    @Override
    public String getProfileFilename() {
        return "sipxbridge.xml";
    }

    @Override
    public void initialize() {
        addDefaultBeanSettingHandler(new Defaults(getDefaults(), this));
    }

    @Override
    public ProfileLocation getProfileLocation() {
        ReplicatedProfileLocation profileLocation = new ReplicatedProfileLocation();
        Location location = m_locationsManager.getLocationByAddress(getAddress());
        profileLocation.setLocation(location);
        profileLocation.setName(m_profileName);
        profileLocation.setDirectory(m_profileDirectory);
        profileLocation.setReplicationContext(m_sipxReplicationContext);
        return profileLocation;
    }

    public List<SipTrunk> getMySipTrunks() {
        List<SipTrunk> trunks = new ArrayList<SipTrunk>();
        for (SipTrunk t : m_gatewayContext.getGatewayByType(SipTrunk.class)) {
            if (equals(t.getSbcDevice())) {
                trunks.add(t);
            }
        }
        return trunks;
    }

    public static class Context extends ProfileContext<BridgeSbc> {
        public Context(BridgeSbc device, String profileTemplate) {
            super(device, profileTemplate);
        }

        @Override
        public Map<String, Object> getContext() {
            Map<String, Object> context = super.getContext();
            BridgeSbc device = getDevice();
            context.put("trunks", device.getMySipTrunks());
            return context;
        }
    }

    public static class Defaults {
        private final DeviceDefaults m_defaults;
        private final SbcDevice m_device;

        Defaults(DeviceDefaults defaults, SbcDevice device) {
            m_defaults = defaults;
            m_device = device;
        }

        @SettingEntry(paths = { "bridge-configuration/local-address", "bridge-configuration/external-address" })
        public String getLocalAddress() {
            return m_device.getAddress();
        }

        @SettingEntry(path = "bridge-configuration/local-port")
        public int getLocalPort() {
            return m_device.getPort();
        }

        @SettingEntry(path = "bridge-configuration/sipx-proxy-domain")
        public String getDomainName() {
            return m_defaults.getDomainName();
        }

        @SettingEntry(path = "bridge-configuration/log-directory")
        public String getLogDirectory() {
            return m_defaults.getLogDirectory() + "/";
        }
    }

    @Override
    public void restart() {
        Location location = m_locationsManager.getLocationByAddress(getAddress());
        m_processContext.manageServices(location, Collections.singleton(m_sipxBridgeService),
                SipxProcessContext.Command.RESTART);
    }
}
