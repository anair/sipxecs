/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin.callgroup;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.sipfoundry.sipxconfig.admin.dialplan.ForkQueueValue;
import org.sipfoundry.sipxconfig.admin.dialplan.MappingRule;
import org.sipfoundry.sipxconfig.admin.forwarding.AliasMapping;
import org.sipfoundry.sipxconfig.common.NamedObject;
import org.sipfoundry.sipxconfig.common.SipUri;
import org.sipfoundry.sipxconfig.common.User;

public class CallGroup extends AbstractCallSequence implements NamedObject {
    private boolean m_enabled;
    private String m_name;
    private String m_extension;
    private String m_description;
    private String m_fallbackDestination;
    private boolean m_voicemailFallback = true;
    private boolean m_userForward = true;

    public CallGroup() {
        // bean usage only
    }

    /**
     * We need a deep clone. Each call can only belong to single collection.
     */
    protected Object clone() throws CloneNotSupportedException {
        CallGroup clone = (CallGroup) super.clone();
        clone.clearRings();
        for (Iterator i = getRings().iterator(); i.hasNext();) {
            UserRing ring = (UserRing) i.next();
            UserRing ringClone = (UserRing) ring.duplicate();
            ringClone.setCallGroup(clone);
            clone.insertRing(ringClone);
        }
        return clone;
    }

    public boolean isEnabled() {
        return m_enabled;
    }

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }

    public String getExtension() {
        return m_extension;
    }
    public String calculateUri(String domainName) {
        return SipUri.format(getName(), domainName, false);
    }
    public void setExtension(String extension) {
        m_extension = extension;
    }

    public String getName() {
        return m_name;
    }

    public void setName(String name) {
        m_name = name;
    }

    public String getDescription() {
        return m_description;
    }

    public void setDescription(String description) {
        m_description = description;
    }

    public String getFallbackDestination() {
        return m_fallbackDestination;
    }

    public void setFallbackDestination(String fallbackDestination) {
        m_fallbackDestination = fallbackDestination;
    }

    public boolean getVoicemailFallback() {
        return m_voicemailFallback;
    }

    public void setVoicemailFallback(boolean voicemailFallback) {
        m_voicemailFallback = voicemailFallback;
    }

    public boolean getUserForward() {
        return m_userForward;
    }

    public void setUserForward(boolean userForward) {
        m_userForward = userForward;
    }

    /**
     * Inserts a new ring for a specific user
     * 
     * @param user
     * @return newly created user ring
     */
    public UserRing insertRingForUser(User user) {
        UserRing ring = new UserRing();
        ring.setCallGroup(this);
        ring.setUser(user);
        insertRing(ring);
        return ring;
    }

    public void insertRings(Collection rings) {
        super.insertRings(rings);
        for (Iterator iter = rings.iterator(); iter.hasNext();) {
            UserRing ring = (UserRing) iter.next();
            ring.setCallGroup(this);
        }
    }

    /**
     * Create list of aliases that descibe forwarding for this group. All aliases have the form
     * group_name@domain => user_name@domain with the specific q value. In addtion alias extension =>
     * group name is added to allow calling to extension
     * 
     * @return list of AliasMapping objects (identity => contact)
     */
    public List<AliasMapping> generateAliases(String domainName) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        String myIdentity = AliasMapping.createUri(m_name, domainName);

        ForkQueueValue forkQueueValue = new ForkQueueValue(getRings().size() + 1);
        List<AliasMapping> aliases = generateAliases(myIdentity, domainName, true, m_userForward,
                forkQueueValue);

        if (m_voicemailFallback) {
            AbstractRing lastRing = getLastRing();
            if (lastRing != null) {
                if (AbstractRing.Type.IMMEDIATE == lastRing.getType()) {
                    // the last ring is a parallel ring
                    // decrement q so that VM contact has lower q than the last contact
                    forkQueueValue.getSerial();
                }
                String vmailContact = lastRing.calculateContact(domainName, forkQueueValue,
                        false, m_userForward, MappingRule.Voicemail.VM_PREFIX);
                aliases.add(new AliasMapping(myIdentity, vmailContact));
            }
        } else if (StringUtils.isNotBlank(m_fallbackDestination)) {
            String falback = SipUri.fix(m_fallbackDestination, domainName);
            String fallbackContact = String
                    .format("<%s>;%s", falback, forkQueueValue.getSerial());
            aliases.add(new AliasMapping(myIdentity, fallbackContact));
        }

        if (StringUtils.isNotBlank(m_extension) && !m_extension.equals(m_name)) {
            AliasMapping extensionAlias = new AliasMapping(AliasMapping.createUri(m_extension,
                    domainName), myIdentity);
            aliases.add(extensionAlias);
        }
        return aliases;
    }
}
