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

import java.io.OutputStream;
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
import org.sipfoundry.sipxconfig.device.InMemoryConfiguration;
import org.sipfoundry.sipxconfig.device.Profile;
import org.sipfoundry.sipxconfig.device.ProfileContext;
import org.sipfoundry.sipxconfig.device.ProfileLocation;
import org.sipfoundry.sipxconfig.device.ReplicatedProfileLocation;
import org.sipfoundry.sipxconfig.gateway.GatewayContext;
import org.sipfoundry.sipxconfig.gateway.SipTrunk;
import org.sipfoundry.sipxconfig.nattraversal.NatTraversal;
import org.sipfoundry.sipxconfig.nattraversal.NatTraversalManager;
import org.sipfoundry.sipxconfig.service.SipxBridgeService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.setting.Setting;
import org.sipfoundry.sipxconfig.setting.SettingEntry;
import org.springframework.beans.factory.annotation.Required;

public class BridgeSbc extends SbcDevice {

    private GatewayContext m_gatewayContext;

    private SipxProcessContext m_processContext;

    private SipxReplicationContext m_sipxReplicationContext;

    private SipxServiceManager m_sipxServiceManager;

    private LocationsManager m_locationsManager;

    private NatTraversalManager m_natTraversalManager;

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
    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_sipxServiceManager = sipxServiceManager;
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

    @Required
    public void setNatTraversalManager(NatTraversalManager natTraversalManager) {
        m_natTraversalManager = natTraversalManager;
    }

    public NatTraversal getNatTraversal() {
        return m_natTraversalManager.getNatTraversal();
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

    public List<InMemoryConfiguration> getConfigurations() {
        ProfileLocation profileLocation = getProfileLocation();
        List<InMemoryConfiguration> configurations = new ArrayList<InMemoryConfiguration>();
        beforeProfileGeneration();
        copyFiles(profileLocation);
        Profile[] profileTypes = getProfileTypes();
        if (profileTypes == null) {
            return Collections.emptyList();
        }
        for (Profile profile : profileTypes) {
            OutputStream outputStream = profile.getGeneratedProfile(this, profileLocation);
            if (null != outputStream) {
                InMemoryConfiguration configuration = new InMemoryConfiguration(m_profileDirectory,
                        m_profileName, outputStream.toString());
                configuration.setLocation(getLocation());
                configurations.add(configuration);
            }
        }
        return configurations;
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

    public Location getLocation() {
        return m_locationsManager.getLocationByAddress(getAddress());
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

        @SettingEntry(paths = { "bridge-configuration/global-address" })
        public String getGlobalAddress() {
            NatTraversal natTraversal = ((BridgeSbc) m_device).getNatTraversal();
            if (null != natTraversal) {
                String address = natTraversal.getSettingValue("nattraversal-info/publicaddress");
                if (null != address) {
                    return address;
                }
            }
            return m_device.getAddress();
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
        SipxBridgeService sipxBridgeService = (SipxBridgeService) m_sipxServiceManager.getServiceByBeanId(
                SipxBridgeService.BEAN_ID);
        m_processContext.manageServices(getLocation(), Collections.singleton(sipxBridgeService),
                SipxProcessContext.Command.RESTART);
    }
}
