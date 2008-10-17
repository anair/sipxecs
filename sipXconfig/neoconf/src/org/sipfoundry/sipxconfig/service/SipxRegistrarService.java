/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.sipfoundry.sipxconfig.admin.commserver.ConflictingFeatureCodeValidator;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessModel.ProcessName;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.setting.Setting;
import org.sipfoundry.sipxconfig.setting.SettingEntry;

public class SipxRegistrarService extends SipxService {

    public static final String BEAN_ID = "sipxRegistrarService";

    private static final String VOICEMAIL_SERVER = "https://localhost";
    private static final String SIP_REGISTRAR_DOMAIN_ALIASES = "domain/SIP_REGISTRAR_DOMAIN_ALIASES";
    private static final ProcessName PROCESS_NAME = ProcessName.REGISTRAR;

    private String m_registrarSipPort;
    private String m_registrarEventSipPort;
    private String m_mediaServerSipSrvOrHostport;
    private String m_orbitServerSipSrvOrHostport;
    private String m_proxyServerSipHostport;
    private SipxServiceManager m_sipxServiceManager;
    private DomainManager m_domainManager;

    public String getMediaServer() {
        return m_mediaServerSipSrvOrHostport + ";transport=tcp";
    }

    public String getVoicemailServer() {
        StringBuffer voicemailServer = new StringBuffer();
        voicemailServer.append(VOICEMAIL_SERVER);
        if (getVoicemailHttpsPort() != null) {
            voicemailServer.append(':');
            voicemailServer.append(getVoicemailHttpsPort());
        }

        return voicemailServer.toString();
    }

    public String getRegistrarSipPort() {
        return m_registrarSipPort;
    }

    public void setRegistrarSipPort(String sipPort) {
        m_registrarSipPort = sipPort;
    }

    public String getRegistrarEventSipPort() {
        return m_registrarEventSipPort;
    }

    public void setRegistrarEventSipPort(String eventSipPort) {
        m_registrarEventSipPort = eventSipPort;
    }

    public String getMediaServerSipSrvOrHostport() {
        return m_mediaServerSipSrvOrHostport;
    }

    public void setMediaServerSipSrvOrHostport(String serverSipSrvOrHostport) {
        m_mediaServerSipSrvOrHostport = serverSipSrvOrHostport;
    }

    public String getOrbitServerSipSrvOrHostport() {
        return m_orbitServerSipSrvOrHostport;
    }

    public void setOrbitServerSipSrvOrHostport(String serverSipSrvOrHostport) {
        m_orbitServerSipSrvOrHostport = serverSipSrvOrHostport;
    }

    public String getProxyServerSipHostport() {
        return m_proxyServerSipHostport;
    }

    public void setProxyServerSipHostport(String serverSipHostport) {
        m_proxyServerSipHostport = serverSipHostport;
    }

    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_sipxServiceManager = sipxServiceManager;
    }

    public DomainManager getDomainManager() {
        return m_domainManager;
    }

    public void setDomainManager(DomainManager manager) {
        m_domainManager = manager;
    }

    @Override
    public void initialize() {
        addDefaultBeanSettingHandler(new Defaults(this));
    }

    public static class Defaults {
        private final SipxRegistrarService m_sipxRegistrarService;

        Defaults(SipxRegistrarService sipxRegistrarService) {
            m_sipxRegistrarService = sipxRegistrarService;
        }

        @SettingEntry(path = SIP_REGISTRAR_DOMAIN_ALIASES)
        public String getSipRegistrarDomainAliases() {
            Set<String> allAliases = new TreeSet<String>();
            if (m_sipxRegistrarService.getFullHostname() != null) {
                allAliases.add(m_sipxRegistrarService.getFullHostname());
            }
            if (m_sipxRegistrarService.getIpAddress() != null) {
                allAliases.add(m_sipxRegistrarService.getIpAddress());
            }
            Collection<String> aliases = m_sipxRegistrarService.getDomainManager().getDomain().getAliases();
            if (aliases != null) {
                allAliases.addAll(aliases);
            }
            return StringUtils.join(allAliases, ' ');
        }
    }

    /**
     * Validates the data in this service and throws a UserException if there is a problem
     */
    @Override
    public void validate() {
        SipxService presenceService = m_sipxServiceManager
                .getServiceByBeanId(SipxPresenceService.BEAN_ID);
        new ConflictingFeatureCodeValidator().validate(Arrays.asList(new Setting[] {
            getSettings(), presenceService.getSettings()
        }));
    }

    @Override
    public ProcessName getProcessName() {
        return PROCESS_NAME;
    }

    public String getDirectedCallPickupCode() {
        return getSettingValue("call-pick-up/SIP_REDIRECT.180-PICKUP.DIRECTED_CALL_PICKUP_CODE");
    }
}
