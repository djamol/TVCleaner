package com.patilweb.tvremap;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Shared launch logic for accessibility + root listeners. */
public final class ActionRunner {
    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    public interface Host {
        Context context();
        void startActivity(Intent intent);
        void performBack();
        void toast(String msg);
    }

    private ActionRunner() {
    }

    public static void run(Host host, Binding binding) {
        if (binding == null || Binding.TYPE_NONE.equals(binding.type)) return;
        try {
            switch (binding.type) {
                case Binding.TYPE_APP:
                    launchPackage(host, binding.packageName);
                    break;
                case Binding.TYPE_HOME:
                    host.startActivity(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case Binding.TYPE_SETTINGS:
                    host.startActivity(new Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case Binding.TYPE_INPUTS:
                    host.startActivity(new Intent(ACTION_VIEW_INPUTS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case Binding.TYPE_ALL_APPS:
                    host.startActivity(new Intent(Intent.ACTION_ALL_APPS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case Binding.TYPE_BACK:
                    host.performBack();
                    break;
                default:
                    break;
            }
        } catch (ActivityNotFoundException e) {
            host.toast("No target for " + binding.label);
        } catch (Exception e) {
            host.toast("Remap failed: " + e.getMessage());
        }
    }

    private static void launchPackage(Host host, String packageName) throws ActivityNotFoundException {
        if (packageName == null || packageName.isEmpty()) return;
        PackageManager pm = host.context().getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = pm.getLaunchIntentForPackage(packageName);
        if (launch == null) throw new ActivityNotFoundException(packageName);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        host.startActivity(launch);
    }
}
