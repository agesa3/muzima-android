/*
 * Copyright (c) The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center. All Rights Reserved.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 * this code in a for-profit venture, please contact the copyright holder.
 */

package com.muzima.service;

import java.util.Date;

public class SntpService {

    public Date getTimePerDeviceTimeZone() {
        long nowAsPerDeviceTimeZone = 0;
        SntpClient sntpClient = new SntpClient();

        if (sntpClient.requestTime("0.africa.pool.ntp.org", 30000)) {
            nowAsPerDeviceTimeZone = sntpClient.getNtpTime();
        }
        return new Date(nowAsPerDeviceTimeZone);
    }

    public Date getTimePerDeviceTimeZone(int timeout) {
        long nowAsPerDeviceTimeZone = 0;
        SntpClient sntpClient = new SntpClient();

        if (sntpClient.requestTime("0.africa.pool.ntp.org", timeout)) {
            nowAsPerDeviceTimeZone = sntpClient.getNtpTime();
        }
        return new Date(nowAsPerDeviceTimeZone);
    }
}
