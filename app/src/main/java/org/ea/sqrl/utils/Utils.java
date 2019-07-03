package org.ea.sqrl.utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;

import com.google.zxing.FormatException;

import org.ea.sqrl.database.IdentityDBHelper;
import org.ea.sqrl.processors.SQRLStorage;
import org.ea.sqrl.services.ClearIdentityReceiver;
import org.ea.sqrl.services.ClearIdentityService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import static android.content.pm.PackageManager.GET_META_DATA;

/**
 *
 * @author Daniel Persson
 */
public class Utils {
    private static final String TAG = "EncryptionUtils";

    /**
     * Wrapper function if we ever need to read more than one byte segment in the future.
     *
     * @param data    String from the QR code read.
     * @return  The string without any extra information.
     */
    public static byte[] readSQRLQRCode(Intent data) throws FormatException {
        byte[] qrCode = new byte[0];
        for(int i=0;; i++) {
            byte[] newSegment = data.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_" + i);
            if(newSegment == null) break;
            qrCode = EncryptionUtils.combine(qrCode, newSegment);
        }

        return qrCode;
    }

    public static String readSQRLQRCodeAsString(Intent data) {
        byte[] qrCode = null;
        try {
            qrCode = readSQRLQRCode(data);
        } catch (FormatException fe) { return null; }

        if (qrCode == null) return null;
        return new String(qrCode);
    }

    public static int getInteger(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    public static void setLanguage(Context context) {
        String lang = getLanguage(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Resources res = context.getResources();
            android.content.res.Configuration conf = res.getConfiguration();
            Locale locale = new Locale(lang);

            conf.setLocale(locale);
            res.updateConfiguration(conf, res.getDisplayMetrics());
        }
    }

    public static String getLanguage(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String lang = sharedPreferences.getString("language", "");
        return lang.isEmpty() ? Locale.getDefault().getLanguage() : lang;
    }

    public static void reloadActivityTitle(Activity activity) {
        try {
            int labelId = activity.getPackageManager().getActivityInfo(
                    activity.getComponentName(), GET_META_DATA).labelRes;
            if (labelId != 0) {
                activity.setTitle(labelId);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void refreshStorageFromDb(Activity activity) throws Exception {
        SharedPreferences sharedPref = activity.getApplication().getSharedPreferences(
                "org.ea.sqrl.preferences",
                Context.MODE_PRIVATE
        );
        long currentId = sharedPref.getLong("current_id", 0);
        IdentityDBHelper aDbHelper = IdentityDBHelper.getInstance(activity);
        byte[] identityData = aDbHelper.getIdentityData(currentId);
        SQRLStorage sqrlStorage = SQRLStorage.getInstance(activity);
        sqrlStorage.read(identityData);
    }

    public static byte[] getFileIntentContent(Context context, Uri contentUri) {
        if (contentUri == null) return null;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);

            while ((len = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }

            inputStream.close();
            return os.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void clearQuickPassDelayed(Context context) {
        long delayMillis = SQRLStorage.getInstance(context.getApplicationContext()).getIdleTimeout() * 60000;

        SqrlApplication.setApplicationShortcuts(context.getApplicationContext());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobInfo jobInfo = new JobInfo.Builder(ClearIdentityService.JOB_NUMBER, new ComponentName(context, ClearIdentityService.class))
                    .setMinimumLatency(delayMillis).build();

            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(jobScheduler != null) jobScheduler.schedule(jobInfo);
        } else {
            AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

            Intent intent = new Intent(context.getApplicationContext(), ClearIdentityReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, 0);

            int SDK_INT = Build.VERSION.SDK_INT;
            long timeInMillis = System.currentTimeMillis() + delayMillis;

            if(alarmManager == null) return;

            if (SDK_INT < Build.VERSION_CODES.KITKAT) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
            } else if (SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
            }
        }
    }
}
