/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin.dialplan.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.sipfoundry.sipxconfig.admin.dialplan.IDialingRule;
import org.sipfoundry.sipxconfig.gateway.Gateway;
import org.sipfoundry.sipxconfig.permission.PermissionName;

/**
 * Authorization rule generator.
 * 
 * One dialing rules corresponds to one hostMatch element. All gateways end up in hostPatterns,
 * all dialing patterns are put in userPatterns. Permissions are added to the resulting
 * permissions match code.
 * 
 * <code>
 * <hostMatch>
 *    <hostPattern>gateway addresses</hostPattern>
 *    <userMatch>
 *      <userPattern>sos</userPattern> 
 *      <permissionMatch>name of the permission</permissionMatch>
 *    </userMatch>
 * </hostMatch>
 * </code>
 * 
 */
public class AuthRules extends RulesXmlFile {
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/urlauth-00-00";
    private static final String NO_ACCESS_RULE = "Reject all other calls to the gateways"
            + " that are not handled by the earlier rules";
    private static final String PERMISSION = "permission";
    private static final String PERMISSION_MATCH = "permissionMatch";
    private static final String USER_PATTERN = "userPattern";
    private static final String USER_MATCH = "userMatch";
    private static final String HOST_PATTERN = "hostPattern";
    private static final String HOST_MATCH = "hostMatch";

    private Document m_doc;
    private Set<Gateway> m_gateways;

    public AuthRules() {
    }

    public void begin() {
        m_doc = FACTORY.createDocument();
        QName mappingsName = FACTORY.createQName("mappings", NAMESPACE);
        Element mappings = m_doc.addElement(mappingsName);
        addExternalRules(mappings);

        // Create a new gateways set. It will be populated as part of generate call
        m_gateways = new HashSet<Gateway>();
    }

    public void generate(IDialingRule rule) {
        List<Gateway> gateways = rule.getGateways();
        List<String> permissions = rule.getPermissionNames();
        if (gateways.size() == 0) {
            // nothing to generate
            return;
        }
        Element mappings = m_doc.getRootElement();
        for (Gateway gateway : gateways) {
            Element hostMatch = mappings.addElement(HOST_MATCH);
            addRuleNameComment(hostMatch, rule);
            addRuleDescription(hostMatch, rule);
            Element hostPattern = hostMatch.addElement(HOST_PATTERN);
            hostPattern.setText(gateway.getGatewayAddress());
            m_gateways.add(gateway);
            Element userMatch = hostMatch.addElement(USER_MATCH);
            String[] patterns = rule.getTransformedPatterns(gateway);
            for (int i = 0; i < patterns.length; i++) {
                String pattern = patterns[i];
                Element userPattern = userMatch.addElement(USER_PATTERN);
                userPattern.setText(pattern);
            }
            // even if no permission is specified (permission list is empty) we create empty
            // element
            Element permissionMatch = userMatch.addElement(PERMISSION_MATCH);
            for (String permission : permissions) {
                Element pelement = permissionMatch.addElement(PERMISSION);
                pelement.setText(permission);
            }
        }
    }

    public Document getDocument() {
        return m_doc;
    }

    void generateNoAccess(Collection<Gateway> gateways) {
        Element mappings = m_doc.getRootElement();
        Element hostMatch = mappings.addElement(HOST_MATCH);
        hostMatch.addElement("description").setText(NO_ACCESS_RULE);
        for (Gateway gateway : gateways) {
            Element hostPattern = hostMatch.addElement(HOST_PATTERN);
            hostPattern.setText(gateway.getGatewayAddress());
        }
        Element userMatch = hostMatch.addElement(USER_MATCH);
        userMatch.addElement(USER_PATTERN).setText(".");
        Element permissionMatch = userMatch.addElement(PERMISSION_MATCH);
        permissionMatch.addElement(PERMISSION).setText(PermissionName.NO_ACCESS.getName());
    }

    public void end() {
        generateNoAccess(m_gateways);
    }
}
