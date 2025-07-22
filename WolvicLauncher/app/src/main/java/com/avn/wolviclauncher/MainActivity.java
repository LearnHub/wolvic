package com.avn.wolviclauncher_XrDino;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_PACKAGE = "com.avn.wolvic";
    private static final String TARGET_ACTIVITY = "com.igalia.wolvic.VRBrowserActivity";

    //private static final String TARGET_URL = "https://aframe.io/a-painter/";
    //private static final String TARGET_URL = "https://moonrider.xyz/";
    private static final String TARGET_URL = "https://xrdinosaurs.com/";

    //private static final String IMMERSIVE_XPATH = "//button[contains(@class,\"a-enter-vr-button\")]";
    //private static final String IMMERSIVE_XPATH = "//div[@id=\"vrButton\"]";
    private static final String IMMERSIVE_XPATH = "//li[@id=\"viewVRButton\"]";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchWolvic();
    }

    private void launchWolvic() {
        PackageManager pm = getPackageManager();

        // Build the VIEW intent we want Wolvic to handle.
        Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(TARGET_PACKAGE, TARGET_ACTIVITY);
            intent.setData(Uri.parse(TARGET_URL));
            intent.putExtra("launch_immersive_element_xpath", IMMERSIVE_XPATH);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Launch failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
