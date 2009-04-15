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
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.sipfoundry.sipxconfig.device.ModelSource;
import org.sipfoundry.sipxconfig.setting.Setting;
import org.sipfoundry.sipxconfig.setting.type.FileSetting;

public class UploadUtil {
    private UploadManager m_uploadManager;
    private ModelSource<UploadSpecification> m_uploadSpecSource;

    /**
     * @params  uploadId, settingName0, fileName0, settingName1, fileName1, ...
     */
    Upload addUpload(String[] args) {
        String uploadSpecId = args[1];
        UploadSpecification spec = m_uploadSpecSource.getModel(uploadSpecId);
        if (spec == null) {
            illegalArgument("No such upload type '%s'", uploadSpecId);
        }

        // If there is anything already active, do not add a new one!
        Upload upload = null;
        if (!m_uploadManager.isActiveUploadById(spec)) {
            upload = m_uploadManager.newUpload(spec);
            upload.setName("System Default - " + spec.getLabel());
            upload.setDescription("Default System Firmware");
            m_uploadManager.saveUpload(upload);
        }

        return upload;
    }

    void setUploads(UploadUtil copyUtil, Upload upload, String[] args) {
        for (int i = 2; i < args.length; i = i + 2) {
            String settingName = args[i];
            String fileName = args[i + 1];
            Setting s = upload.getSettings().getSetting(settingName);
            if (s == null) {
                illegalArgument("No such setting '%s'", settingName);
            }
            FileSetting type = (FileSetting) s.getType();
            File in = new File(fileName);
            File uploadDir = new File(upload.getUploadDirectory());
            File uploadFile = new File(uploadDir, in.getName());
            copyUtil.copyFile(in, uploadFile);
            type.setDirectory(uploadDir.getPath());
            s.setValue(uploadFile.getName());
        }
    }

    public void deploy(Upload upload) {
        m_uploadManager.deploy(upload);
    }

    private void illegalArgument(String template, Object... params) {
        throw new IllegalArgumentException(String.format(template, params));
    }

    void copyFile(File f, File destination) {
        try {
            FileUtils.copyFile(f, destination);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUploadManager(UploadManager uploadManager) {
        m_uploadManager = uploadManager;
    }

    public void setUploadSpecificationSource(ModelSource<UploadSpecification> uploadSpecSource) {
        m_uploadSpecSource = uploadSpecSource;
    }

}
