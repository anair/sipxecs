/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.gateway.audiocodes;

import org.sipfoundry.sipxconfig.device.DeviceDefaults;
import org.sipfoundry.sipxconfig.service.UnmanagedService;
import org.sipfoundry.sipxconfig.setting.SettingEntry;

/**
 * FIXME: This is currently shared by fxs and fxo gateways. Ideally both gateway classes would be
 * derived from the same base.
 */
public class AudioCodesGatewayDefaults {
    private AudioCodesGateway m_fxoGateway;

    private AudioCodesFxsGateway m_fxsGateway;

    private DeviceDefaults m_defaults;

    AudioCodesGatewayDefaults(AudioCodesGateway fxoGateway, DeviceDefaults defaults) {
        m_fxoGateway = fxoGateway;
        m_defaults = defaults;
    }

    AudioCodesGatewayDefaults(AudioCodesFxsGateway fxsGateway, DeviceDefaults defaults) {
        m_fxsGateway = fxsGateway;
        m_defaults = defaults;
    }

    /**
     * We need to return "Cyclic ascendant(1)" for FXO gateway and "By phone number(0)" for FXO
     * gateways.
     */
    @SettingEntry(path = "SIP_general/ChannelSelectMode")
    public String getChannelSelecMode() {
        if (m_fxoGateway != null) {
            // 
            return "1";
        }
        return "0";
    }

    @SettingEntry(path = "SIP_Proxy_Registration/SIPDestinationPort")
    public String getDestinationPort() {
        return m_defaults.getProxyServerSipPort();
    }

    @SettingEntry(paths = { "SIP_Proxy_Registration/ProxyIP", "SIP_Proxy_Registration/RegistrarIP",
            "SIP_Proxy_Registration/SIPGatewayName" })
    public String getDomainName() {
        return m_defaults.getDomainName();
    }

    @SettingEntry(path = "Network/NTPServerIP")
    public String getNtpServer() {
        return m_defaults.getNtpServer();
    }

    @SettingEntry(path = "Network/DNSPriServerIP")
    public String getDNSPriServerIP() {
        return m_defaults.getServer(0, UnmanagedService.DNS);
    }

    @SettingEntry(path = "Network/DNSSecServerIP")
    public String getDNSSecServerIP() {
        return m_defaults.getServer(1, UnmanagedService.DNS);
    }

    @SettingEntry(path = "Network/EnableSyslog")
    public boolean getEnableSyslog() {
        return null != m_defaults.getServer(0, UnmanagedService.SYSLOG);
    }

    @SettingEntry(paths = { "Network/SyslogServerIP", "advanced_general/CDR/CDRSyslogServerIP" })
    public String getSyslogServerIP() {
        return m_defaults.getServer(0, UnmanagedService.SYSLOG);
    }

    @SettingEntry(path = "advanced_general/MaxActiveCalls")
    public int getMaxActiveCalls() {
        if (m_fxoGateway != null) {
            return m_fxoGateway.getMaxCalls();
        }
        return m_fxsGateway.getLines().size();
    }

    /**
     * Only allow calls from SIP proxy by default.
     */
    @SettingEntry(path = "advanced_general/AllowedIPs")
    public String getAllowedIPs() {
        return m_defaults.getProxyServerAddr();
    }
}
