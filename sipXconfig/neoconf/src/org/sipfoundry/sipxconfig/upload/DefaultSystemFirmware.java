/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.upload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultSystemFirmware {

    private String m_uploadSpec;
    private List<String> m_fileSettings;
    private List<String> m_uploadFiles;

    public DefaultSystemFirmware(String uploadSpec, List<String> fileSettings, List<String> uploadFiles) {
        m_uploadSpec = uploadSpec;
        m_fileSettings = fileSettings;
        m_uploadFiles = uploadFiles;
    }

    public String getUploadSpec() {
        return m_uploadSpec;
    }

    public List<String> getFileSettings() {
        return m_fileSettings;
    }

    public List<String> getUploadFiles() {
        return m_uploadFiles;
    }

    public String[] getUploadArgs() {
        List<String> args = new ArrayList<String>();
        args.add("");
        args.add(m_uploadSpec);

        if (null != m_fileSettings && null != m_uploadFiles) {
            for (int i = 0; (i < m_fileSettings.size() && i < m_uploadFiles.size()); i++) {
                if ((new File(m_uploadFiles.get(i))).exists()) {
                    args.add(m_fileSettings.get(i));
                    args.add(m_uploadFiles.get(i));
                }
            }
        }

        return args.toArray(new String[args.size()]);
    }
}
