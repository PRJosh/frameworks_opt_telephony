/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.IMms;
import com.android.internal.telephony.uicc.IccConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/*
 * TODO(code review): Curious question... Why are a lot of these
 * methods not declared as static, since they do not seem to require
 * any local object state?  Presumably this cannot be changed without
 * interfering with the API...
 */

/**
 * Manages SMS operations such as sending data, text, and pdu SMS messages.
 * Get this object by calling the static method {@link #getDefault()}.
 *
 * <p>For information about how to behave as the default SMS app on Android 4.4 (API level 19)
 * and higher, see {@link android.provider.Telephony}.
 */
public final class SmsManager {
    /**
     * A psuedo-subId that represents the default subId at any given time. The actual subId it
     * represents changes as the default subId is changed.
     */
    private static final int DEFAULT_SUB_ID = -1002;

    /** Singleton object constructed during class initialization. */
    private static final SmsManager sInstance = new SmsManager(DEFAULT_SUB_ID);
    private static final Object sLockObject = new Object();
    private static final Map<Long, SmsManager> sSubInstances = new ArrayMap<Long, SmsManager>();

    /** A concrete subId, or the pseudo DEFAULT_SUB_ID */
    private long mSubId;

    /*
     * Key for the various carrier-dependent configuration values.
     * Some of the values are used by the system in processing SMS or MMS messages. Others
     * are provided for the convenience of SMS applications.
     */

    /**
     * Whether to append transaction id to MMS WAP Push M-Notification.ind's content location URI
     * when constructing the download URL of a new MMS (boolean type)
     */
    public static final String MMS_CONFIG_APPEND_TRANSACTION_ID = "enabledTransID";
    /**
     * Whether MMS is enabled for the current carrier (boolean type)
     */
    public static final String MMS_CONFIG_MMS_ENABLED = "enabledMMS";
    /**
     * Whether group MMS is enabled for the current carrier (boolean type)
     */
    public static final String MMS_CONFIG_GROUP_MMS_ENABLED = "enableGroupMms";
    /**
     * If this is enabled, M-NotifyResp.ind should be sent to the WAP Push content location
     * instead of the default MMSC (boolean type)
     */
    public static final String MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    /**
     * Whether alias is enabled (boolean type)
     */
    public static final String MMS_CONFIG_ALIAS_ENABLED = "aliasEnabled";
    /**
     * Whether audio is allowed to be attached for MMS messages (boolean type)
     */
    public static final String MMS_CONFIG_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    /**
     * Whether multipart SMS is enabled (boolean type)
     */
    public static final String MMS_CONFIG_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    /**
     * Whether SMS delivery report is enabled (boolean type)
     */
    public static final String MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    /**
     * Whether content-disposition field should be expected in an MMS PDU (boolean type)
     */
    public static final String MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION =
            "supportMmsContentDisposition";
    /**
     * Whether multipart SMS should be sent as separate messages
     */
    public static final String MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES =
            "sendMultipartSmsAsSeparateMessages";
    /**
     * Whether MMS read report is enabled (boolean type)
     */
    public static final String MMS_CONFIG_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    /**
     * Whether MMS delivery report is enabled (boolean type)
     */
    public static final String MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    /**
     * Max MMS message size in bytes (int type)
     */
    public static final String MMS_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize";
    /**
     * Max MMS image width (int type)
     */
    public static final String MMS_CONFIG_MAX_IMAGE_WIDTH = "maxImageWidth";
    /**
     * Max MMS image height (int type)
     */
    public static final String MMS_CONFIG_MAX_IMAGE_HEIGHT = "maxImageHeight";
    /**
     * Limit of recipients of MMS messages (int type)
     */
    public static final String MMS_CONFIG_RECIPIENT_LIMIT = "recipientLimit";
    /**
     * Min alias character count (int type)
     */
    public static final String MMS_CONFIG_ALIAS_MIN_CHARS = "aliasMinChars";
    /**
     * Max alias character count (int type)
     */
    public static final String MMS_CONFIG_ALIAS_MAX_CHARS = "aliasMaxChars";
    /**
     * When the number of parts of a multipart SMS reaches this threshold, it should be
     * converted into an MMS (int type)
     */
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD = "smsToMmsTextThreshold";
    /**
     * Some carriers require SMS to be converted into MMS when text length reaches this threshold
     * (int type)
     */
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD =
            "smsToMmsTextLengthThreshold";
    /**
     * Max message text size (int type)
     */
    public static final String MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    /**
     * Max message subject length (int type)
     */
    public static final String MMS_CONFIG_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    /**
     * MMS HTTP socket timeout in milliseconds (int type)
     */
    public static final String MMS_CONFIG_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    /**
     * The name of the UA Prof URL HTTP header for MMS HTTP request (String type)
     */
    public static final String MMS_CONFIG_UA_PROF_TAG_NAME = "uaProfTagName";
    /**
     * The User-Agent header value for MMS HTTP request (String type)
     */
    public static final String MMS_CONFIG_USER_AGENT = "userAgent";
    /**
     * The UA Profile URL header value for MMS HTTP request (String type)
     */
    public static final String MMS_CONFIG_UA_PROF_URL = "uaProfUrl";
    /**
     * A list of HTTP headers to add to MMS HTTP request, separated by "|" (String type)
     */
    public static final String MMS_CONFIG_HTTP_PARAMS = "httpParams";
    /**
     * Email gateway number (String type)
     */
    public static final String MMS_CONFIG_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    /**
     * The suffix to append to the NAI header value for MMS HTTP request (String type)
     */
    public static final String MMS_CONFIG_NAI_SUFFIX = "naiSuffix";

    /**
     * Send a text based SMS.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * <p class="note"><strong>Note:</strong> Beginning with Android 4.4 (API level 19), if
     * <em>and only if</em> an app is not selected as the default SMS app, the system automatically
     * writes messages sent using this method to the SMS Provider (the default SMS app is always
     * responsible for writing its sent messages to the SMS Provider). For information about
     * how to behave as the default SMS app, see {@link android.provider.Telephony}.</p>
     *
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or text are empty
     */
    public void sendTextMessage(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }

        try {
            ISms iccISms = getISmsServiceOrThrow();
            iccISms.sendText(ActivityThread.currentPackageName(), destinationAddress,
                    scAddress, text, sentIntent, deliveryIntent);
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Inject an SMS PDU into the android application framework.
     *
     * The caller should have carrier privileges.
     * @see android.telephony.TelephonyManager.hasCarrierPrivileges
     *
     * @param pdu is the byte array of pdu to be injected into android application framework
     * @param format is the format of SMS pdu (3gpp or 3gpp2)
     * @param receivedIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully received by the
     *  android application framework. This intent is broadcasted at
     *  the same time an SMS received from radio is acknowledged back.
     *
     *  @throws IllegalArgumentException if format is not one of 3gpp and 3gpp2.
     *  {@hide}
     */
    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        if (!format.equals(SmsMessage.FORMAT_3GPP) && !format.equals(SmsMessage.FORMAT_3GPP2)) {
            // Format must be either 3gpp or 3gpp2.
            throw new IllegalArgumentException(
                    "Invalid pdu format. format must be either 3gpp or 3gpp2");
        }
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.injectSmsPdu(pdu, format, receivedIntent);
            }
        } catch (RemoteException ex) {
          // ignore it
        }
    }

    /**
     * Update the status of a pending (send-by-IP) SMS message and resend by PSTN if necessary.
     * This outbound message was handled by the carrier app. If the carrier app fails to send
     * this message, it would be resent by PSTN.
     *
     * The caller should have carrier privileges.
     * @see android.telephony.TelephonyManager.hasCarrierPrivileges
     *
     * @param messageRef the reference number of the SMS message.
     * @param success True if and only if the message was sent successfully. If its value is
     *  false, this message should be resent via PSTN.
     * {@hide}
     */
    public void updateSmsSendStatus(int messageRef, boolean success) {
        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                iccISms.updateSmsSendStatus(messageRef, success);
            }
        } catch (RemoteException ex) {
          // ignore it
        }
    }

    /**
     * Divide a message text into several fragments, none bigger than
     * the maximum SMS message size.
     *
     * @param text the original message.  Must not be null.
     * @return an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     *
     * @throws IllegalArgumentException if text is null
     */
    public ArrayList<String> divideMessage(String text) {
        if (null == text) {
            throw new IllegalArgumentException("text is null");
        }
        return SmsMessage.fragmentText(text);
    }

    /**
     * Send a multi-part text based SMS.  The callee should have already
     * divided the message into correctly sized parts by calling
     * <code>divideMessage</code>.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * <p class="note"><strong>Note:</strong> Beginning with Android 4.4 (API level 19), if
     * <em>and only if</em> an app is not selected as the default SMS app, the system automatically
     * writes messages sent using this method to the SMS Provider (the default SMS app is always
     * responsible for writing its sent messages to the SMS Provider). For information about
     * how to behave as the default SMS app, see {@link android.provider.Telephony}.</p>
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK</code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applications,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendMultipartTextMessage(
            String destinationAddress, String scAddress, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }

        if (parts.size() > 1) {
            try {
                ISms iccISms = getISmsServiceOrThrow();
                iccISms.sendMultipartText(ActivityThread.currentPackageName(),
                        destinationAddress, scAddress, parts,
                        sentIntents, deliveryIntents);
            } catch (RemoteException ex) {
                // ignore it
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, parts.get(0),
                    sentIntent, deliveryIntent);
        }
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * <p class="note"><strong>Note:</strong> Using this method requires that your app has the
     * {@link android.Manifest.permission#SEND_SMS} permission.</p>
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param destinationPort the port to deliver the message to
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendDataMessage(
            String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }

        try {
            ISms iccISms = getISmsServiceOrThrow();
            iccISms.sendData(ActivityThread.currentPackageName(),
                    destinationAddress, scAddress, destinationPort & 0xFFFF,
                    data, sentIntent, deliveryIntent);
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
    * Get the default instance of the SmsManager
    *
    * @return the default instance of the SmsManager
    */
    public static SmsManager getDefault() {
        return sInstance;
    }

    /**
     * Get the the instance of the SmsManager associated with a particular subId
     *
     * @param subId a SMS subscription id, typically accessed using SubscriptionManager
     * @return the instance of the SmsManager associated with subId
     *
     * {@hide}
     */
    public static SmsManager getSmsManagerForSubscriber(long subId) {
        // TODO(shri): Add javadoc link once SubscriptionManager is made public api
        synchronized(sLockObject) {
            SmsManager smsManager = sSubInstances.get(subId);
            if (smsManager == null) {
                smsManager = new SmsManager(subId);
                sSubInstances.put(subId, smsManager);
            }
            return smsManager;
        }
    }

    private SmsManager(long subId) {
        mSubId = subId;
    }

    /**
     * Get the associated subId. If the instance was returned by {@link #getDefault()}, then this
     * method may return different values at different points in time (if the user changes the
     * default subId). It will return SubscriptionManager.INVALID_SUB_ID if the default
     * subId cannot be determined.
     *
     * @return associated subId
     *
     * {@hide}
     */
    public long getSubId() {
        // TODO(shri): Add javadoc link once SubscriptionManager is made public api
        if (mSubId == DEFAULT_SUB_ID) {
            return getDefaultSmsSubId();
        }
        return mSubId;
    }

    /**
     * Returns the ISms service, or throws an UnsupportedOperationException if
     * the service does not exist.
     */
    private static ISms getISmsServiceOrThrow() {
        ISms iccISms = getISmsService();
        if (iccISms == null) {
            throw new UnsupportedOperationException("Sms is not supported");
        }
        return iccISms;
    }

    private static ISms getISmsService() {
        return ISms.Stub.asInterface(ServiceManager.getService("isms"));
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param smsc the SMSC for this message, or NULL for the default SMSC
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return true for success
     *
     * @throws IllegalArgumentException if pdu is NULL
     * {@hide}
     */
    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu,int status) {
        boolean success = false;

        if (null == pdu) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(),
                        status, pdu, smsc);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Delete the specified message from the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex is the record index of the message on ICC
     * @return true for success
     *
     * {@hide}
     */
    public boolean
    deleteMessageFromIcc(int messageIndex) {
        boolean success = false;
        byte[] pdu = new byte[IccConstants.SMS_RECORD_LENGTH-1];
        Arrays.fill(pdu, (byte)0xff);

        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, STATUS_ON_ICC_FREE, pdu);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Update the specified message on the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return true for success
     *
     * {@hide}
     */
    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu) {
        boolean success = false;

        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, newStatus, pdu);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Retrieves all messages currently stored on ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects
     *
     * {@hide}
     */
    public static ArrayList<SmsMessage> getAllMessagesFromIcc() {
        List<SmsRawData> records = null;

        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName());
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return createMessageListFromRawRecords(records);
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.Telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     * @see #disableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean enableCellBroadcast(int messageIdentifier) {
        boolean success = false;

        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.enableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean disableCellBroadcast(int messageIdentifier) {
        boolean success = false;

        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.disableCellBroadcast(messageIdentifier);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.Telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     * @see #disableCellBroadcastRange(int, int)
     *
     * @throws IllegalArgumentException if endMessageId < startMessageId
     * {@hide}
     */
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId) {
        boolean success = false;

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.enableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range. Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastRange(int, int)
     *
     * @throws IllegalArgumentException if endMessageId < startMessageId
     * {@hide}
     */
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId) {
        boolean success = false;

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                success = iccISms.disableCellBroadcastRange(startMessageId, endMessageId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Create a list of <code>SmsMessage</code>s from a list of RawSmsData
     * records returned by <code>getAllMessagesFromIcc()</code>
     *
     * @param records SMS EF records, returned by
     *   <code>getAllMessagesFromIcc</code>
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects.
     */
    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records) {
        ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = records.get(i);
                // List contains all records, including "free" records (null)
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i+1, data.getBytes());
                    if (sms != null) {
                        messages.add(sms);
                    }
                }
            }
        }
        return messages;
    }

    /**
     * SMS over IMS is supported if IMS is registered and SMS is supported
     * on IMS.
     *
     * @return true if SMS over IMS is supported, false otherwise
     *
     * @see #getImsSmsFormat()
     *
     * @hide
     */
    public boolean isImsSmsSupported() {
        boolean boSupported = false;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                boSupported = iccISms.isImsSmsSupported();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return boSupported;
    }

    /**
     * Gets SMS format supported on IMS.  SMS over IMS format is
     * either 3GPP or 3GPP2.
     *
     * @return SmsMessage.FORMAT_3GPP,
     *         SmsMessage.FORMAT_3GPP2
     *      or SmsMessage.FORMAT_UNKNOWN
     *
     * @see #isImsSmsSupported()
     *
     * @hide
     */
    public String getImsSmsFormat() {
        String format = com.android.internal.telephony.SmsConstants.FORMAT_UNKNOWN;
        try {
            ISms iccISms = getISmsService();
            if (iccISms != null) {
                format = iccISms.getImsSmsFormat();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return format;
    }

    /**
     * Get the default sms subId
     *
     * @return the default subId
     * @hide
     */
    public static long getDefaultSmsSubId() {
        ISms iccISms = null;
        try {
            iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            return (long) iccISms.getPreferredSmsSubscription();
        } catch (RemoteException ex) {
            return DEFAULT_SUB_ID;
        } catch (NullPointerException ex) {
            return DEFAULT_SUB_ID;
        }
    }

    /**
     * Get SMS prompt property,  enabled or not
     *
     * @return true if enabled, false otherwise
     * @hide
     */
    public boolean isSMSPromptEnabled() {
        ISms iccISms = null;
        try {
            iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            return iccISms.isSMSPromptEnabled();
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    // see SmsMessage.getStatusOnIcc

    /** Free space (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_FREE      = 0;

    /** Received and read (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_READ      = 1;

    /** Received and unread (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNREAD    = 3;

    /** Stored and sent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_SENT      = 5;

    /** Stored and unsent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNSENT    = 7;

    // SMS send failure result codes

    /** Generic failure cause */
    static public final int RESULT_ERROR_GENERIC_FAILURE    = 1;
    /** Failed because radio was explicitly turned off */
    static public final int RESULT_ERROR_RADIO_OFF          = 2;
    /** Failed because no pdu provided */
    static public final int RESULT_ERROR_NULL_PDU           = 3;
    /** Failed because service is currently unavailable */
    static public final int RESULT_ERROR_NO_SERVICE         = 4;
    /** Failed because we reached the sending queue limit.  {@hide} */
    static public final int RESULT_ERROR_LIMIT_EXCEEDED     = 5;
    /** Failed because FDN is enabled. {@hide} */
    static public final int RESULT_ERROR_FDN_CHECK_FAILURE  = 6;

    static private final String PHONE_PACKAGE_NAME = "com.android.phone";

    /**
     * Send an MMS message
     *
     * @param context application context
     * @param contentUri the content Uri from which the message pdu will be read
     * @param locationUrl the optional location url where message should be sent to
     * @param configOverrides the carrier-specific messaging configuration values to override for
     *  sending the message.
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed
     * @throws IllegalArgumentException if contentUri is empty
     */
    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl,
            Bundle configOverrides, PendingIntent sentIntent) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            final IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms == null) {
                return;
            }
            context.grantUriPermission(PHONE_PACKAGE_NAME, contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            grantCarrierPackageUriPermission(context, contentUri,
                    Telephony.Mms.Intents.MMS_SEND_ACTION, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            iMms.sendMessage(getSubId(), ActivityThread.currentPackageName(), contentUri,
                    locationUrl, configOverrides, sentIntent);
        } catch (RemoteException e) {
            // Ignore it
        }
    }

    private void grantCarrierPackageUriPermission(Context context, Uri contentUri, String action,
            int permission) {
        Intent intent = new Intent(action);
        TelephonyManager telephonyManager =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        List<String> carrierPackages = telephonyManager.getCarrierPackageNamesForIntent(
                intent);
        if (carrierPackages != null && carrierPackages.size() == 1) {
            context.grantUriPermission(carrierPackages.get(0), contentUri, permission);
        }
    }

    /**
     * Download an MMS message from carrier by a given location URL
     *
     * @param context application context
     * @param locationUrl the location URL of the MMS message to be downloaded, usually obtained
     *  from the MMS WAP push notification
     * @param contentUri the content uri to which the downloaded pdu will be written
     * @param configOverrides the carrier-specific messaging configuration values to override for
     *  downloading the message.
     * @param downloadedIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is downloaded, or the download is failed
     * @throws IllegalArgumentException if locationUrl or contentUri is empty
     */
    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri,
            Bundle configOverrides, PendingIntent downloadedIntent) {
        if (TextUtils.isEmpty(locationUrl)) {
            throw new IllegalArgumentException("Empty MMS location URL");
        }
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            final IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms == null) {
                return;
            }
            context.grantUriPermission(PHONE_PACKAGE_NAME, contentUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            grantCarrierPackageUriPermission(context, contentUri,
                    Telephony.Mms.Intents.MMS_DOWNLOAD_ACTION,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            iMms.downloadMessage(getSubId(), ActivityThread.currentPackageName(), locationUrl,
                    contentUri, configOverrides, downloadedIntent);
        } catch (RemoteException e) {
            // Ignore it
        }
    }

    // MMS send/download failure result codes
    public static final int MMS_ERROR_UNSPECIFIED = 1;
    public static final int MMS_ERROR_INVALID_APN = 2;
    public static final int MMS_ERROR_UNABLE_CONNECT_MMS = 3;
    public static final int MMS_ERROR_HTTP_FAILURE = 4;
    public static final int MMS_ERROR_IO_ERROR = 5;
    public static final int MMS_ERROR_RETRY = 6;
    public static final int MMS_ERROR_CONFIGURATION_ERROR = 7;

    // Intent extra name for result data
    public static final String EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA";

    /**
     * Update the status of a pending (send-by-IP) MMS message handled by the carrier app.
     * If the carrier app fails to send this message, it may be resent via carrier network
     * depending on the status code.
     *
     * The caller should have carrier privileges.
     * @see android.telephony.TelephonyManager.hasCarrierPrivileges
     *
     * @param context application context
     * @param messageRef the reference number of the MMS message.
     * @param pdu non-empty (contains the SendConf PDU) if the message was sent successfully,
     *   otherwise, this param should be null.
     * @param status send status. It can be Activity.RESULT_OK or one of the MMS error codes.
     *   If status is Activity.RESULT_OK, the MMS was sent successfully.
     *   If status is MMS_ERROR_RETRY, this message would be resent via carrier
     *   network. The message will not be resent for other MMS error statuses.
     * @param contentUri the URI of the sent message
     * {@hide}
     */
    public void updateMmsSendStatus(Context context, int messageRef, byte[] pdu, int status,
            Uri contentUri) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms == null) {
                return;
            }
            iMms.updateMmsSendStatus(messageRef, pdu, status);
        } catch (RemoteException ex) {
            // ignore it
        }
        if (contentUri != null) {
            context.revokeUriPermission(contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    /**
     * Update the status of a pending (download-by-IP) MMS message handled by the carrier app.
     * If the carrier app fails to download this message, it may be re-downloaded via carrier
     * network depending on the status code.
     *
     * The caller should have carrier privileges.
     * @see android.telephony.TelephonyManager.hasCarrierPrivileges
     *
     * @param context application context
     * @param messageRef the reference number of the MMS message.
     * @param status download status.  It can be Activity.RESULT_OK or one of the MMS error codes.
     *   If status is Activity.RESULT_OK, the MMS was downloaded successfully.
     *   If status is MMS_ERROR_RETRY, this message would be re-downloaded via carrier
     *   network. The message will not be re-downloaded for other MMS error statuses.
     * @param contentUri the URI of the downloaded message
     * {@hide}
     */
    public void updateMmsDownloadStatus(Context context, int messageRef, int status,
            Uri contentUri) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms == null) {
                return;
            }
            iMms.updateMmsDownloadStatus(messageRef, status);
        } catch (RemoteException ex) {
            // ignore it
        }
        if (contentUri != null) {
            context.revokeUriPermission(contentUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    /**
     * Import a text message into system's SMS store
     *
     * Only default SMS apps can import SMS
     *
     * @param address the destination(source) address of the sent(received) message
     * @param type the type of the message
     * @param text the message text
     * @param timestampMillis the message timestamp in milliseconds
     * @param seen if the message is seen
     * @param read if the message is read
     * @return the message URI, null if failed
     * @hide
     */
    public Uri importTextMessage(String address, int type, String text, long timestampMillis,
            boolean seen, boolean read) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.importTextMessage(ActivityThread.currentPackageName(),
                        address, type, text, timestampMillis, seen, read);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return null;
    }

    /** Represents the received SMS message for importing {@hide} */
    public static final int SMS_TYPE_INCOMING = 0;
    /** Represents the sent SMS message for importing {@hide} */
    public static final int SMS_TYPE_OUTGOING = 1;

    /**
     * Import a multimedia message into system's MMS store. Only the following PDU type is
     * supported: Retrieve.conf, Send.req, Notification.ind, Delivery.ind, Read-Orig.ind
     *
     * Only default SMS apps can import MMS
     *
     * @param contentUri the content uri from which to read the PDU of the message to import
     * @param messageId the optional message id. Use null if not specifying
     * @param timestampSecs the optional message timestamp. Use -1 if not specifying
     * @param seen if the message is seen
     * @param read if the message is read
     * @return the message URI, null if failed
     * @throws IllegalArgumentException if pdu is empty
     * {@hide}
     */
    public Uri importMultimediaMessage(Uri contentUri, String messageId, long timestampSecs,
            boolean seen, boolean read) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.importMultimediaMessage(ActivityThread.currentPackageName(),
                        contentUri, messageId, timestampSecs, seen, read);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return null;
    }

    /**
     * Delete a system stored SMS or MMS message
     *
     * Only default SMS apps can delete system stored SMS and MMS messages
     *
     * @param messageUri the URI of the stored message
     * @return true if deletion is successful, false otherwise
     * @throws IllegalArgumentException if messageUri is empty
     * {@hide}
     */
    public boolean deleteStoredMessage(Uri messageUri) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.deleteStoredMessage(ActivityThread.currentPackageName(), messageUri);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return false;
    }

    /**
     * Delete a system stored SMS or MMS thread
     *
     * Only default SMS apps can delete system stored SMS and MMS conversations
     *
     * @param conversationId the ID of the message conversation
     * @return true if deletion is successful, false otherwise
     * {@hide}
     */
    public boolean deleteStoredConversation(long conversationId) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.deleteStoredConversation(
                        ActivityThread.currentPackageName(), conversationId);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return false;
    }

    /**
     * Update the status properties of a system stored SMS or MMS message, e.g.
     * the read status of a message, etc.
     *
     * @param messageUri the URI of the stored message
     * @param statusValues a list of status properties in key-value pairs to update
     * @return true if update is successful, false otherwise
     * @throws IllegalArgumentException if messageUri is empty
     * {@hide}
     */
    public boolean updateStoredMessageStatus(Uri messageUri, ContentValues statusValues) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.updateStoredMessageStatus(ActivityThread.currentPackageName(),
                        messageUri, statusValues);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return false;
    }

    /** Message status property: whether the message has been seen. 1 means seen, 0 not {@hide} */
    public static final String MESSAGE_STATUS_SEEN = "seen";
    /** Message status property: whether the message has been read. 1 means read, 0 not {@hide} */
    public static final String MESSAGE_STATUS_READ = "read";

    /**
     * Archive or unarchive a stored conversation
     *
     * @param conversationId the ID of the message conversation
     * @param archived true to archive the conversation, false to unarchive
     * @return true if update is successful, false otherwise
     * {@hide}
     */
    public boolean archiveStoredConversation(long conversationId, boolean archived) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.archiveStoredConversation(ActivityThread.currentPackageName(),
                        conversationId, archived);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return false;
    }

    /**
     * Add a text message draft to system SMS store
     *
     * Only default SMS apps can add SMS draft
     *
     * @param address the destination address of message
     * @param text the body of the message to send
     * @return the URI of the stored draft message
     * {@hide}
     */
    public Uri addTextMessageDraft(String address, String text) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.addTextMessageDraft(ActivityThread.currentPackageName(), address, text);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return null;
    }

    /**
     * Add a multimedia message draft to system MMS store
     *
     * Only default SMS apps can add MMS draft
     *
     * @param contentUri the content uri from which to read the PDU data of the draft MMS
     * @return the URI of the stored draft message
     * @throws IllegalArgumentException if pdu is empty
     * {@hide}
     */
    public Uri addMultimediaMessageDraft(Uri contentUri) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.addMultimediaMessageDraft(ActivityThread.currentPackageName(),
                        contentUri);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return null;
    }

    /**
     * Send a system stored text message.
     *
     * You can only send a failed text message or a draft text message.
     *
     * @param messageUri the URI of the stored message
     * @param scAddress is the service center address or null to use the current default SMSC
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *
     * @throws IllegalArgumentException if messageUri is empty
     * {@hide}
     */
    public void sendStoredTextMessage(Uri messageUri, String scAddress, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            ISms iccISms = getISmsServiceOrThrow();
            iccISms.sendStoredText(getSubId(), ActivityThread.currentPackageName(), messageUri,
                    scAddress, sentIntent, deliveryIntent);
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Send a system stored multi-part text message.
     *
     * You can only send a failed text message or a draft text message.
     * The provided <code>PendingIntent</code> lists should match the part number of the
     * divided text of the stored message by using <code>divideMessage</code>
     *
     * @param messageUri the URI of the stored message
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK</code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applications,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *
     * @throws IllegalArgumentException if messageUri is empty
     * {@hide}
     */
    public void sendStoredMultipartTextMessage(Uri messageUri, String scAddress,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            ISms iccISms = getISmsServiceOrThrow();
            iccISms.sendStoredMultipartText(getSubId(), ActivityThread.currentPackageName(), messageUri,
                    scAddress, sentIntents, deliveryIntents);
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Send a system stored MMS message
     *
     * This is used for sending a previously sent, but failed-to-send, message or
     * for sending a text message that has been stored as a draft.
     *
     * @param messageUri the URI of the stored message
     * @param configOverrides the carrier-specific messaging configuration values to override for
     *  sending the message.
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed
     * @throws IllegalArgumentException if messageUri is empty
     * {@hide}
     */
    public void sendStoredMultimediaMessage(Uri messageUri, Bundle configOverrides,
            PendingIntent sentIntent) {
        if (messageUri == null) {
            throw new IllegalArgumentException("Empty message URI");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.sendStoredMessage(getSubId(), ActivityThread.currentPackageName(), messageUri,
                        configOverrides, sentIntent);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Turns on/off the flag to automatically write sent/received SMS/MMS messages into system
     *
     * When this flag is on, all SMS/MMS sent/received are stored by system automatically
     * When this flag is off, only SMS/MMS sent by non-default SMS apps are stored by system
     * automatically
     *
     * This flag can only be changed by default SMS apps
     *
     * @param enabled Whether to enable message auto persisting
     * {@hide}
     */
    public void setAutoPersisting(boolean enabled) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.setAutoPersisting(ActivityThread.currentPackageName(), enabled);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Get the value of the flag to automatically write sent/received SMS/MMS messages into system
     *
     * When this flag is on, all SMS/MMS sent/received are stored by system automatically
     * When this flag is off, only SMS/MMS sent by non-default SMS apps are stored by system
     * automatically
     *
     * @return the current value of the auto persist flag
     * {@hide}
     */
    public boolean getAutoPersisting() {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getAutoPersisting();
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return false;
    }

    /**
     * Get carrier-dependent configuration values.
     *
     * @return bundle key/values pairs of configuration values
     */
    public Bundle getCarrierConfigValues() {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getCarrierConfigValues(getSubId());
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return null;
    }

}
