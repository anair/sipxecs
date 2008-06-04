/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 *
 */
package org.sipfoundry.sipxconfig.service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.setting.Setting;

public class SipxProxyConfigurationTest extends SipxServiceTestBase {

    public void testWrite() throws Exception {
        SipxProxyConfiguration out = new SipxProxyConfiguration();
        out.setVelocityEngine(TestHelper.getVelocityEngine());
        out.setTemplate("sipxproxy/sipXproxy-config.vm");
        
        SipxProxyService proxyService = new SipxProxyService();
        initCommonAttributes(proxyService);
        Setting settings = TestHelper.loadSettings("sipxproxy/sipxproxy.xml");
        proxyService.setSettings(settings);

        Setting proxySettings = proxyService.getSettings().getSetting("proxy-configuration");
        proxySettings.getSetting("SIPX_PROXY_DEFAULT_EXPIRES").setValue("35");
        proxySettings.getSetting("SIPX_PROXY_DEFAULT_SERIAL_EXPIRES").setValue("170");
        proxySettings.getSetting("SIPX_PROXY_LOG_LEVEL").setValue("CRIT");

        proxyService.setSecureSipPort("5061");
        proxyService.setSipSrvOrHostport("sipsrv.example.org");
        proxyService.setCallResolverCallStateDb("CALL_RESOLVER_DB");
        
        out.generate(proxyService);
        
        StringWriter actualConfigWriter = new StringWriter();
        out.write(actualConfigWriter);
        
        Reader referenceConfigReader = new InputStreamReader(SipxProxyConfigurationTest.class
                .getResourceAsStream("expected-proxy-config"));
        String referenceConfig = IOUtils.toString(referenceConfigReader);
        
        Reader actualConfigReader = new StringReader(actualConfigWriter.toString());
        String actualConfig = IOUtils.toString(actualConfigReader);

        assertEquals(referenceConfig, actualConfig);
        
    }
}
