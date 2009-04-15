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
import java.util.Arrays;
import java.util.List;

import org.sipfoundry.sipxconfig.test.TestUtil;

import junit.framework.TestCase;

public class DefaultSystemFirmwareInstallTest extends TestCase {

    DefaultSystemFirmwareInstall m_defaultSystemFirmwareInstall;

    protected void setUp() throws IOException {
        File thisDir = new File(TestUtil.getTestSourceDirectory(getClass()));
        m_defaultSystemFirmwareInstall = new DefaultSystemFirmwareInstall();
        m_defaultSystemFirmwareInstall.setFirmwareDirectory(thisDir.getAbsolutePath());
    }

    public void testFindAvailableFirmwares() {
        boolean found_firmware_one = false;
        boolean found_firmware_two = false;
        boolean found_firmware_three = false;

        String rootDir = TestUtil.getTestSourceDirectory(getClass()) + '/';

        String[] firmware_one = {"", "testFirmware", "firmware", rootDir + "firmware.cfg"};
        String[] firmware_two = {"", "test2Firmware", "firmware2", rootDir + "firmware2.cfg"};
        String[] firmware_three = {"", "test3Firmware", "firmware3", rootDir + "firmware3.cfg"};

        m_defaultSystemFirmwareInstall.findAvailableFirmwares();
        List<DefaultSystemFirmware> defaultSystemFirmwares = m_defaultSystemFirmwareInstall.getDefaultSystemFirmwares();
        assertEquals(defaultSystemFirmwares.size(), 3);

        for (DefaultSystemFirmware defaultSystemFirmware : defaultSystemFirmwares) {
            String[] args = defaultSystemFirmware.getUploadArgs();

            if (Arrays.equals(firmware_one, args)) {
                found_firmware_one = true;
            } else if (Arrays.equals(firmware_two, args)) {
                found_firmware_two = true;
            } else if (Arrays.equals(firmware_three, args)) {
                found_firmware_three = true;
            }
        }

        assertEquals(true, found_firmware_one);
        assertEquals(true, found_firmware_two);
        assertEquals(true, found_firmware_three);
    }
}
