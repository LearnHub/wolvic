package com.avn.wolviclauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A basic Launcher App, that allows us to parametrize Wolvic startup.
 */
public class MainActivity extends Activity {

    private static final String TARGET_PACKAGE = "com.avn.wolvic";
    private static final String TARGET_ACTIVITY = "com.igalia.wolvic.VRBrowserActivity";
    private static final String TARGET_URL = "https://aframe.io/a-painter/";
    private static final String IMMERSIVE_XPATH = "//button[contains(@class,\"a-enter-vr-button\")]";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Optionally update UI
        TextView tv = findViewById(R.id.status_text);
        if (tv != null) {
            tv.setText(R.string.launching);
        }

        launchWolvic();
    }

    private void launchWolvic() {
        // Ensure target package exists
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo(TARGET_PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, R.string.wolvic_not_found, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(TARGET_PACKAGE, TARGET_ACTIVITY);
            intent.setData(Uri.parse(TARGET_URL));
            intent.putExtra("launch_immersive_element_xpath", IMMERSIVE_XPATH);

            // If launching from outside an Activity you'd need FLAG_ACTIVITY_NEW_TASK; not needed here.
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to launch Wolvic: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            // Close this launcher so user lands in Wolvic
            finish();
        }
    }
}
