/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 */
package org.sipfoundry.sipxconfig.admin.dialplan.attendant;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.sipfoundry.sipxconfig.admin.commserver.AliasProvider;
import org.sipfoundry.sipxconfig.admin.dialplan.config.XmlFile;
import org.sipfoundry.sipxconfig.admin.forwarding.AliasMapping;
import org.sipfoundry.sipxconfig.common.Closure;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.springframework.beans.factory.annotation.Required;

import static org.sipfoundry.sipxconfig.common.DaoUtils.forAllUsersDo;

public class ValidUsersConfig extends XmlFile {
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/validusers-00-00";

    private static final String ELEMENT_NAME_ALIAS = "alias";
    private static final String ELEMENT_NAME_ALIASES = "aliases";
    private static final String ELEMENT_NAME_CONTACT = "contact";
    private static final String ELEMENT_NAME_DISPLAYNAME = "displayName";
    private static final String ELEMENT_NAME_IDENTITY = "identity";
    private static final String ELEMENT_NAME_INDIRECTORY = "inDirectory";
    private static final String ELEMENT_NAME_PINTOKEN = "pintoken";
    private static final String ELEMENT_NAME_PASSTOKEN = "passtoken";
    private static final String ELEMENT_NAME_USER = "user";
    private static final String ELEMENT_NAME_USERNAME = "userName";
    private static final String ELEMENT_NAME_HASVOICEMAIL = "hasVoicemail";
    private static final String ELEMENT_NAME_CANRECORDPROMPTS = "canRecordPrompts";
    private static final String ELEMENT_NAME_CANTUICHANGEPIN = "canTuiChangePin";
    private CoreContext m_coreContext;

    private DomainManager m_domainManager;

    private AliasProvider m_aliasProvider;



    @Override
    public Document getDocument() {
        Document document = FACTORY.createDocument();
        QName validUsersName = FACTORY.createQName("validusers", NAMESPACE);
        final Element usersEl = document.addElement(validUsersName);
        Closure<User> closure = new Closure<User>() {
            @Override
            public void execute(User user) {
                generateUser(usersEl, user);
            }
        };
        forAllUsersDo(m_coreContext, closure);


        // Load up the specified aliases
        List<AliasMapping> aliasMappings = (List<AliasMapping>) m_aliasProvider
                .getAliasMappings();
        // Generate the aliases
        for (AliasMapping am : aliasMappings) {
            generateAlias(usersEl, am);
        }
        return document;
    }

    private void generateUser(Element usersEl, User user) {
        String domainName = m_domainManager.getDomain().getName();

        Element userEl = usersEl.addElement(ELEMENT_NAME_USER);
        String identity = AliasMapping.createUri(user.getUserName(), domainName);
        String contact = user.getUri(domainName);
        userEl.addElement(ELEMENT_NAME_IDENTITY).setText(identity);
        userEl.addElement(ELEMENT_NAME_USERNAME).setText(user.getUserName());
        Element aliasesEl = userEl.addElement(ELEMENT_NAME_ALIASES);
        for (String alias : user.getAliases()) {
            aliasesEl.addElement(ELEMENT_NAME_ALIAS).setText(alias);
        }
        String displayName = user.getDisplayName();
        if (displayName != null) {
            userEl.addElement(ELEMENT_NAME_DISPLAYNAME).setText(displayName);
        }
        userEl.addElement(ELEMENT_NAME_CONTACT).setText(contact);
        userEl.addElement(ELEMENT_NAME_PINTOKEN).setText(user.getPintoken());

        String realm = m_domainManager.getAuthorizationRealm();
        userEl.addElement(ELEMENT_NAME_PASSTOKEN).setText(user.getSipPasswordHash(realm));
        boolean inDirectory = user.hasPermission(PermissionName.AUTO_ATTENDANT_DIALING);
        userEl.addElement(ELEMENT_NAME_INDIRECTORY).setText(Boolean.toString(inDirectory));
        boolean hasVoiceMail = user.hasPermission(PermissionName.VOICEMAIL);
        userEl.addElement(ELEMENT_NAME_HASVOICEMAIL).setText(Boolean.toString(hasVoiceMail));
        boolean canRecordPrompts = user.hasPermission(PermissionName.RECORD_SYSTEM_PROMPTS);
        userEl.addElement(ELEMENT_NAME_CANRECORDPROMPTS).setText(Boolean.toString(canRecordPrompts));
        boolean canTuiChangePin = user.hasPermission(PermissionName.TUI_CHANGE_PIN);
        userEl.addElement(ELEMENT_NAME_CANTUICHANGEPIN).setText(Boolean.toString(canTuiChangePin));
    }

    private void generateAlias(Element usersEl, AliasMapping am) {
        Element userEl = usersEl.addElement(ELEMENT_NAME_USER);
        String identity = am.getIdentity();
        String contact = am.getContact();
        userEl.addElement(ELEMENT_NAME_IDENTITY).setText(identity);
        userEl.addElement(ELEMENT_NAME_USERNAME).setText(identity.substring(0, identity.indexOf('@')));
        userEl.addElement(ELEMENT_NAME_CONTACT).setText(contact);
        userEl.addElement(ELEMENT_NAME_INDIRECTORY).setText("false");
    }

    @Required
    public void setDomainManager(DomainManager domainManager) {
        m_domainManager = domainManager;
    }

    @Required
    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    @Required
    public void setAliasProvider(AliasProvider aliasProvider) {
        m_aliasProvider = aliasProvider;
    }
}
