/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.email;

import com.android.email.mail.internet.MimeUtility;
import com.android.email.provider.AttachmentProvider;
import com.android.email.provider.EmailContent.Attachment;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.List;

/**
 * Encapsulates commonly used attachment information related to suitability for viewing and saving,
 * based on the attachment's filename and mime type.
 */
public class AttachmentInfo {
    public final String mName;
    public final String mContentType;
    public final long mSize;
    public final long mId;
    public final boolean mAllowView;
    public final boolean mAllowSave;

    public AttachmentInfo(Context context, Attachment attachment) {
        mSize = attachment.mSize;
        mContentType = AttachmentProvider.inferMimeType(attachment.mFileName, attachment.mMimeType);
        mName = attachment.mFileName;
        mId = attachment.mId;
        boolean canView = true;
        boolean canSave = true;

        // Check for acceptable / unacceptable attachments by MIME-type
        if ((!MimeUtility.mimeTypeMatches(mContentType, Email.ACCEPTABLE_ATTACHMENT_VIEW_TYPES)) ||
            (MimeUtility.mimeTypeMatches(mContentType, Email.UNACCEPTABLE_ATTACHMENT_VIEW_TYPES))) {
            canView = false;
        }

        // Check for unacceptable attachments by filename extension
        String extension = AttachmentProvider.getFilenameExtension(mName);
        if (!TextUtils.isEmpty(extension) &&
                Utility.arrayContains(Email.UNACCEPTABLE_ATTACHMENT_EXTENSIONS, extension)) {
            canView = false;
            canSave = false;
        }

        // Check for installable attachments by filename extension
        extension = AttachmentProvider.getFilenameExtension(mName);
        if (!TextUtils.isEmpty(extension) &&
                Utility.arrayContains(Email.INSTALLABLE_ATTACHMENT_EXTENSIONS, extension)) {
            int sideloadEnabled;
            sideloadEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.INSTALL_NON_MARKET_APPS, 0 /* sideload disabled */);
            canView = false;
            canSave &= (sideloadEnabled == 1);
        }

        // Check for file size exceeded
        // The size limit is overridden when on a wifi connection - any size is OK
        if (mSize > Email.MAX_ATTACHMENT_DOWNLOAD_SIZE) {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = cm.getActiveNetworkInfo();
            if (network == null || network.getType() != ConnectivityManager.TYPE_WIFI) {
                canView = false;
                canSave = false;
            }
        }

        // Check to see if any activities can view this attachment
        // If not, we can't view it
        Intent intent = getAttachmentIntent(context, 0);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activityList = pm.queryIntentActivities(intent, 0);
        if (activityList.isEmpty()) {
            canView = false;
            canSave = false;
        }

        mAllowView = canView;
        mAllowSave = canSave;
    }

    /**
     * Returns an <code>Intent</code> to load the given attachment.
     */
    public Intent getAttachmentIntent(Context context, long accountId) {
        Uri attachmentUri = AttachmentProvider.getAttachmentUri(accountId, mId);
        Uri contentUri = AttachmentProvider.resolveAttachmentIdToContentUri(
                context.getContentResolver(), attachmentUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(contentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        return intent;
    }

    /**
     * An attachment is eligible for download if it can either be viewed or saved (or both)
     * @return whether the attachment is eligible for download
     */
    public boolean eligibleForDownload() {
        return mAllowView || mAllowSave;
    }
}
