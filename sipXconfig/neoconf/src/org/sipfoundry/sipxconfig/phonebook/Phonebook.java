/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.phonebook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.sipfoundry.sipxconfig.common.BeanWithId;
import org.sipfoundry.sipxconfig.common.NamedObject;
import org.sipfoundry.sipxconfig.setting.Group;

public class Phonebook extends BeanWithId implements NamedObject {
    private String m_name;
    private String m_description;
    private Set<Group> m_members = new TreeSet<Group>();
    private Set<Group> m_consumers = new TreeSet<Group>();
    private Collection<PhonebookFileEntry> m_entries = new ArrayList<PhonebookFileEntry>();

    public Set<Group> getMembers() {
        return m_members;
    }

    public void setMembers(Set<Group> members) {
        m_members = members;
    }

    public void replaceMembers(Collection<Group> groups) {
        m_members.clear();
        m_members.addAll(groups);
    }

    public void replaceConsumers(Collection<Group> groups) {
        m_consumers.clear();
        m_consumers.addAll(groups);
    }

    public String getName() {
        return m_name;
    }

    public void setName(String name) {
        m_name = name;
    }

    public Set<Group> getConsumers() {
        return m_consumers;
    }

    public void setConsumers(Set<Group> consumers) {
        m_consumers = consumers;
    }

    public String getDescription() {
        return m_description;
    }

    public void setDescription(String description) {
        m_description = description;
    }

    public Collection<PhonebookFileEntry> getEntries() {
        return m_entries;
    }

    public void setEntries(Collection<PhonebookFileEntry> entries) {
        m_entries = entries;
    }

    public void addEntries(Collection<PhonebookFileEntry> entries) {
        for (PhonebookFileEntry entry : entries) {
            addEntry(entry);
        }
    }

    public void addEntry(PhonebookFileEntry entry) {
        entry.setPhonebook(this);
        m_entries.add(entry);
    }
}
