package org.ea.sqrl.utils;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import org.ea.sqrl.R;
import org.ea.sqrl.activites.SimplifiedActivity;
import org.ea.sqrl.activites.ClearQuickPassActivity;
import org.ea.sqrl.activites.EnableQuickPassActivity;
import org.ea.sqrl.database.IdentityDBHelper;
import org.ea.sqrl.processors.EntropyHarvester;
import org.ea.sqrl.processors.SQRLStorage;

import java.util.Arrays;
import java.util.Map;

import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_AUTO;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_NO;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_YES;


public class SqrlApplication extends Application {
    private static final String TAG = "SqrlApplication";
    public static final String APPS_PREFERENCES = "org.ea.sqrl.preferences";
    public static final String CURRENT_ID = "current_id";

    static ShortcutInfo scanShortcut;
    static ShortcutInfo logonShortcut;
    static ShortcutInfo clearQuickPassShortcut;

    @Override
    public void onCreate() {
        super.onCreate();
        configureShortcuts(getApplicationContext());
        setApplicationShortcuts(getApplicationContext());
        try {
            long currentId = getCurrentId(getApplicationContext());
            if (currentId > 0) {
                SQRLStorage.getInstance(getApplicationContext()).read(IdentityDBHelper.getInstance(getApplicationContext()).getIdentityData(currentId));
            }
            EntropyHarvester.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get initiate EntropyHarvester or SQRLStorage.");
        }

        toggleNightModes((UiModeManager)getSystemService(Context.UI_MODE_SERVICE), false, true, getApplicationContext());
    }

    public static void setApplicationShortcuts(Context context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SQRLStorage sqrlStorage = SQRLStorage.getInstance(context);
            if (getCurrentId(context) > 0) {
                ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
                if (sqrlStorage.hasQuickPass()) {
                    shortcutManager.setDynamicShortcuts(Arrays.asList(scanShortcut, clearQuickPassShortcut));
                } else {
                    shortcutManager.setDynamicShortcuts(Arrays.asList(scanShortcut, logonShortcut));
                }
            }
        }
    }

    public static void configureShortcuts(Context context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intentQuickScan = new Intent(context, SimplifiedActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setAction(SimplifiedActivity.ACTION_QUICK_SCAN);
            scanShortcut = new ShortcutInfo.Builder(context, "scanQrWeb")
                    .setShortLabel(context.getString(R.string.scan_qr_code))
                    .setLongLabel(context.getString(R.string.scan_qr_code_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_scan_qr_black_24dp))
                    .setIntent(intentQuickScan)
                    .build();

            Intent simplifiedActivity = new Intent(context, SimplifiedActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .setAction("android.intent.action.MAIN");
            Intent intentLogon = new Intent(context, EnableQuickPassActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .setAction(SimplifiedActivity.ACTION_LOGON);
            Intent[] logonIntentList = {simplifiedActivity, intentLogon};
            logonShortcut = new ShortcutInfo.Builder(context, "setQuickpass")
                    .setShortLabel(context.getString(R.string.set_quickpass))
                    .setLongLabel(context.getString(R.string.set_quickpass_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_sqrl_icon_outof_safe_vector_outline))
                    .setIntents(logonIntentList)
                    .build();

            Intent intentClearQuickpass = new Intent(context, ClearQuickPassActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .setAction(ClearQuickPassActivity.ACTION_CLEAR_QUICK_PASS);
            Intent[] clearQuickPassIntentList = {simplifiedActivity, intentClearQuickpass};
            clearQuickPassShortcut = new ShortcutInfo.Builder(context, "clearQuickpass")
                    .setShortLabel(context.getString(R.string.clear_quickpass))
                    .setLongLabel(context.getString(R.string.clear_quickpass_long))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_sqrl_icon_into_safe_vector_outline))
                    .setIntents(clearQuickPassIntentList)
                    .build();
        }
    }

    /**
     * Gets the currently active identity id from the app preferences.
     *
     * @param context    The context of the caller.
     * @return           Returns the currently active identity id, or 0 if non is set.
     */
    public static long getCurrentId(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(APPS_PREFERENCES, Context.MODE_PRIVATE);
        return sharedPref.getLong(CURRENT_ID, 0);
    }

    /**
     * Saves the provided identity id as the currently active id in the app preferences.
     *
     * @param application The caller's application object.
     */
    public static void saveCurrentId(Application application, long newIdentityId) {
        SharedPreferences sharedPref = application.getSharedPreferences(APPS_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong(CURRENT_ID, newIdentityId);
        editor.apply();
    }

    /**
     * Reloads the sqrl storage with the provided identity and updates the currently active
     * identity id in the app preferences accordingly.
     *
     * @param context   The context of the caller.
     * @param id        The id of the identity which should be set as currently active.
     *                  Set this to -1 to select the first available identity.
     */
    public static void setCurrentId(Context context, long id) {
        IdentityDBHelper dbHelper = IdentityDBHelper.getInstance(context);

        if (id == -1) {
            Map<Long,String> identities = dbHelper.getIdentities();
            if (identities.size() > 0) {
                id = identities.keySet().iterator().next();
            }
        }

        if (id == -1) return;

        SqrlApplication.saveCurrentId((Application) context.getApplicationContext(), id);

        SQRLStorage storage = SQRLStorage.getInstance(context.getApplicationContext());
        byte[] identityData = dbHelper.getIdentityData(id);

        if(storage.needsReload(identityData)) {
            storage.clearQuickPass();
            try {
                storage.read(identityData);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    /**
     * 1) Configures night mode display upon app on startup: sets the mode to automatic
     * so that the night display modes will be effective.
     *
     * 2) Allows for developer testing the night UI modes. Each time this method is called it
     * progresses to the next night modes: MODE_NIGHT_AUTO > MODE_NIGHT_YES > MODE_NIGHT_NO
     *
     * @param uiModeManager             A system resource.
     * @param notifyUser                boolean to create toast messages for the user.
     * @param forceAutomaticNightMode   Set to true on startup to default to automatic night mode.
     * @param context                   A system resource to allow toast messages.
     */

    public static void toggleNightModes(UiModeManager uiModeManager, boolean notifyUser, boolean forceAutomaticNightMode, Context context) {
        boolean carModeWasOff = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR;
        int currentNightMode = uiModeManager.getNightMode();
        if (MODE_NIGHT_NO == currentNightMode || currentNightMode < 0 || forceAutomaticNightMode) {
            if (notifyUser) Toast.makeText(context, "Setting night mode 'auto'", Toast.LENGTH_SHORT).show();
            uiModeManager.enableCarMode(0);
            uiModeManager.setNightMode(UiModeManager.MODE_NIGHT_AUTO);
            if (carModeWasOff) uiModeManager.disableCarMode(0);
        } else if (MODE_NIGHT_AUTO == currentNightMode){
            if (notifyUser) Toast.makeText(context, "Setting night mode 'yes'", Toast.LENGTH_SHORT).show();
            uiModeManager.enableCarMode(0);
            uiModeManager.setNightMode(UiModeManager.MODE_NIGHT_YES);
            if (carModeWasOff) uiModeManager.disableCarMode(0);
        } else if (MODE_NIGHT_YES == currentNightMode) {
            if (notifyUser) Toast.makeText(context, "Setting night mode 'no'", Toast.LENGTH_SHORT).show();
            uiModeManager.setNightMode(UiModeManager.MODE_NIGHT_NO);
        } else {
            if (notifyUser) Toast.makeText(context, "Not setting night mode.  Unexpected '" + currentNightMode + "'", Toast.LENGTH_SHORT).show();
        }
    }
}
