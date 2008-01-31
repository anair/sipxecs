/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.common.UserException;

/**
 * Interface to command line restore utility
 */
public class Restore implements Serializable, WaitingListener {
    private static final Log LOG = LogFactory.getLog(Restore.class);

    private static final String ERROR = "Errors when executing restore script: %s";

    private static final String RESTORE_BINARY = "sipx-sudo-restore";

    private static final String SPACE = " ";

    private static final int INCOMPATIBLE_VERSIONS = 5;

    private static final String RESTORE_LOG = "sipx-restore.log";

    private static final String LOG_READ_EX = "log.read.ex";

    private static final String LOG_FOUND_EX = "log.found.ex";

    private String m_binDirectory;
    private String m_logDirectory;

    private List<BackupBean> m_selectedBackups;

    public void afterResponseSent() {
        perform(m_selectedBackups);

    }

    public void perform(List<BackupBean> backups) {
        execute(backups, false);
    }

    public void validate(List<BackupBean> backups) {
        execute(backups, true);
    }

    private void execute(List<BackupBean> backups, boolean verify) {
        String[] cmdLine = getCmdLine(backups, verify);
        try {
            Process process = Runtime.getRuntime().exec(cmdLine);
            int code = process.waitFor();
            if (code == INCOMPATIBLE_VERSIONS && verify) {
                throw new UserException("wrongVersion");
            }
        } catch (IOException e) {
            LOG.error(String.format(ERROR, StringUtils.join(cmdLine, SPACE)));
            throw new UserException("noScriptFound");
        } catch (InterruptedException e) {
            LOG.warn(String.format(ERROR, StringUtils.join(cmdLine, SPACE)));
        }
    }

    String[] getCmdLine(List<BackupBean> backups, boolean verify) {
        File executable = new File(getBinDirectory(), RESTORE_BINARY);
        List<String> cmds = new ArrayList<String>();
        cmds.add(executable.getAbsolutePath());

        for (BackupBean backup : backups) {
            cmds.add(backup.getType().getOption());
            cmds.add(backup.getPath());
        }
        cmds.add("--non-interactive");
        cmds.add("--enforce-version");
        if (verify) {
            cmds.add("--verify");
        }
        return cmds.toArray(new String[cmds.size()]);
    }

    public String getBinDirectory() {
        return m_binDirectory;
    }

    public void setBinDirectory(String binDirectory) {
        m_binDirectory = binDirectory;
    }

    public String getLogDirectory() {
        return m_logDirectory;
    }

    public void setLogDirectory(String logDirectory) {
        m_logDirectory = logDirectory;
    }

    public String getRestoreLogContent() {
        try {
            File log = new File(getLogDirectory(), RESTORE_LOG);
            return IOUtils.toString(new FileReader(log));
        } catch (FileNotFoundException ex) {
            throw new UserException(LOG_FOUND_EX);
        } catch (IOException ex) {
            throw new UserException(LOG_READ_EX);
        }
    }

    public List<BackupBean> getSelectedBackups() {
        return m_selectedBackups;
    }

    public void setSelectedBackups(List<BackupBean> selectedBackups) {
        m_selectedBackups = selectedBackups;
    }

}
