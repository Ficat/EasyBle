package com.ficat.easyble.utils;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import static android.os.Build.VERSION.SDK_INT;

/**
 * This class provides permission check APIs that verify both the
 * permission and the associated app op for this permission if
 * such is defined.
 * <p>
 * This PermissionChecker contents copy from
 * {@link android.support.v4.content.PermissionChecker}, because I
 * just don't wanna import support-package or androidx
 */
public class PermissionChecker {
    /**
     * Permission result: The permission is granted.
     */
    public static final int PERMISSION_GRANTED = PackageManager.PERMISSION_GRANTED;

    /**
     * Permission result: The permission is denied.
     */
    public static final int PERMISSION_DENIED = PackageManager.PERMISSION_DENIED;

    /**
     * Permission result: The permission is denied because the app op is not allowed.
     */
    public static final int PERMISSION_DENIED_APP_OP = PackageManager.PERMISSION_DENIED - 1;


    public static boolean isPermissionGranted(Context context, String permission) {
        return checkSelfPermission(context, permission) == PERMISSION_GRANTED;
    }

    public static int checkSelfPermission(Context context,
                                          String permission) {
        if (context == null || TextUtils.isEmpty(permission)) {
            throw new IllegalArgumentException(context == null ? "context is null" : "permission is null");
        }
        return checkPermission(context, permission, android.os.Process.myPid(),
                android.os.Process.myUid(), context.getPackageName());
    }

    private static int checkPermission(Context context, String permission,
                                       int pid, int uid, String packageName) {
        if (context.checkPermission(permission, pid, uid) == PackageManager.PERMISSION_DENIED) {
            return PERMISSION_DENIED;
        }

        String op = permissionToOp(permission);
        if (op == null) {
            return PERMISSION_GRANTED;
        }

        if (packageName == null) {
            String[] packageNames = context.getPackageManager().getPackagesForUid(uid);
            if (packageNames == null || packageNames.length <= 0) {
                return PERMISSION_DENIED;
            }
            packageName = packageNames[0];
        }

        if (noteProxyOp(context, op, packageName)
                != 0) {
            return PERMISSION_DENIED_APP_OP;
        }

        return PERMISSION_GRANTED;
    }

    private static String permissionToOp(String permission) {
        if (SDK_INT >= 23) {
            return AppOpsManager.permissionToOp(permission);
        } else {
            return null;
        }
    }

    private static int noteProxyOp(Context context, String op,
                                   String proxiedPackageName) {
        if (SDK_INT >= 23) {
            AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
            return appOpsManager.noteProxyOp(op, proxiedPackageName);
        } else {
            return 1;
        }
    }
}
