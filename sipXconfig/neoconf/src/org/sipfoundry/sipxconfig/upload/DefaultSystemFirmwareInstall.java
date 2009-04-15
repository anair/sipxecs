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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.sipfoundry.sipxconfig.common.UserException;

public class DefaultSystemFirmwareInstall {

    private static final String FIRMWARE_CONFIGFILE_EXTENSION = ".fmws";
    private static final String UPLOAD_SPEC = "Upload.spec";
    private static final String FILE_SETTING = "File.setting";
    private static final String UPLOAD_FILE = "Upload.file";

    private UploadUtil m_uploadUtil;
    private String m_firmwareDirectory;

    private List<DefaultSystemFirmware> m_defaultSystemFirmwares;

    public void setUploadUtil(UploadUtil uploadUtil) {
        m_uploadUtil = uploadUtil;
    }

    public void setFirmwareDirectory(String firmwareDirectory) {
        m_firmwareDirectory = firmwareDirectory;
    }

    public List<DefaultSystemFirmware> getDefaultSystemFirmwares() {
        return m_defaultSystemFirmwares;
    }

    public void installAvailableFirmwares() {
        findAvailableFirmwares();
        if (null != m_defaultSystemFirmwares) {
            for (DefaultSystemFirmware defaultSystemFirmware : m_defaultSystemFirmwares) {
                installAvailableFirmware(defaultSystemFirmware);
            }
        }
    }

    public void installAvailableFirmware(DefaultSystemFirmware defaultSystemFirmware) {
        String[] args = defaultSystemFirmware.getUploadArgs();

        if (3 < args.length) {
            Upload upload = m_uploadUtil.addUpload(args);
            if (upload != null) {
                m_uploadUtil.setUploads(m_uploadUtil, upload, args);
                m_uploadUtil.deploy(upload);
            }
        }
    }

    public void findAvailableFirmwares() {
        File firmwareDirectory = new File(m_firmwareDirectory);

        if (firmwareDirectory.exists()) {

            File[] firmwareSpecs = firmwareDirectory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(FIRMWARE_CONFIGFILE_EXTENSION);
                }
            });

            m_defaultSystemFirmwares = new ArrayList<DefaultSystemFirmware>();

            for (File firmwareSpec : firmwareSpecs) {
                try {
                    BufferedReader input = new BufferedReader(new FileReader(firmwareSpec));

                    String uploadSpec = null;
                    List<String> fileSettings = new ArrayList<String>();
                    List<String> uploadFiles = new ArrayList<String>();
                    String line = null;
                    while (null != (line = input.readLine())) {
                        String equalsString = "=";
                        String emptyString = "";

                        if (line.startsWith(UPLOAD_SPEC)) {
                            uploadSpec = line.replace(UPLOAD_SPEC + equalsString, emptyString);
                        } else if (line.startsWith(FILE_SETTING)) {
                            fileSettings.add(line.replace(FILE_SETTING + equalsString, emptyString));
                        } else if (line.startsWith(UPLOAD_FILE)) {
                            String uploadFile = line.replace(UPLOAD_FILE + equalsString, emptyString);
                            uploadFile = m_firmwareDirectory + '/' + uploadFile;
                            uploadFiles.add(uploadFile);
                        }
                    }
                    input.close();

                    m_defaultSystemFirmwares.add(new DefaultSystemFirmware(uploadSpec, fileSettings, uploadFiles));

                } catch (FileNotFoundException e) {
                    throw new UserException("&error.file.not.found", e.getLocalizedMessage());
                } catch (IOException e) {
                    throw new UserException("&error.io.exception", e.getLocalizedMessage());
                }
            }
        }
    }
}
