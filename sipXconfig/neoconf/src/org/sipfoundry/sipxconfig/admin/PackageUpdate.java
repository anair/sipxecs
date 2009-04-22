/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */

package org.sipfoundry.sipxconfig.admin;

/**
 * Represents an available package update.
 */
public class PackageUpdate {
    private String m_packageName;
    private String m_currentVersion;
    private String m_updatedVersion;

    public PackageUpdate(String packageName, String currentVersion, String updatedVersion) {
        m_packageName = packageName;
        m_currentVersion = currentVersion;
        m_updatedVersion = updatedVersion;
    }

    /**
     * @return the packageName
     */
    public String getPackageName() {
        return m_packageName;
    }

    /**
     * @param packageName the packageName to set
     */
    public void setPackageName(String packageName) {
        m_packageName = packageName;
    }

    /**
     * @return the currentVersion
     */
    public String getCurrentVersion() {
        return m_currentVersion;
    }

    /**
     * @param currentVersion the currentVersion to set
     */
    public void setCurrentVersion(String currentVersion) {
        m_currentVersion = currentVersion;
    }

    /**
     * @return the updatedVersion
     */
    public String getUpdatedVersion() {
        return m_updatedVersion;
    }

    /**
     * @param updatedVersion the updatedVersion to set
     */
    public void setUpdatedVersion(String updatedVersion) {
        m_updatedVersion = updatedVersion;
    }

    @Override
    public String toString() {
        return String.format("%s|%s|%s", m_packageName, m_currentVersion, m_updatedVersion);
    }
}
