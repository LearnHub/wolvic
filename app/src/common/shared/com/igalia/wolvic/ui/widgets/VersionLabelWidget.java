/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;

/**
 * A small, always-present text label that shows the app build version. It is laid flat on the
 * skybox "floor" beneath the user and re-positioned every frame by the native scene
 * (BrowserWorld::TickWorld), which keys off {@code WidgetPlacement.name == "version_label"}.
 * Because it lives directly below the viewer it is only in view when looking down.
 */
public class VersionLabelWidget extends UIWidget {

    // Must match the string BrowserWorld::TickWorld looks for to head-follow this widget.
    public static final String NAME = "version_label";

    private TextView mText;
    private ViewGroup mLayout;

    public VersionLabelWidget(@NonNull Context aContext) {
        super(aContext);
        updateUI();
    }

    private void updateUI() {
        removeAllViews();
        inflate(getContext(), R.layout.version_label, this);
        mLayout = findViewById(R.id.versionLabelLayout);
        mText = findViewById(R.id.versionLabelText);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;                       // added to the scene the first time show() is called
        aPlacement.width = 0;                             // measured/self-sized in show()
        aPlacement.height = 0;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentHandle = -1;
        aPlacement.scene = WidgetPlacement.SCENE_ROOT_TRANSPARENT;
        aPlacement.cylinder = false;                      // flat quad; the native side lays it on the floor
        aPlacement.layer = false;                         // plain scene-graph quad so its pose is fully driven by SetTransform
        aPlacement.showPointer = false;                   // not interactive
        aPlacement.name = NAME;                           // do not rely on getSimpleName(): ProGuard renames classes in release
        // Set density directly from the context: subclass fields are not yet initialised when this
        // runs (it is called from the UIWidget constructor).
        aPlacement.density = getResources().getDisplayMetrics().density;
    }

    public void setText(String text) {
        mText.setText(text);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUI();
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        // Size the widget to fit its measured content (same approach as TooltipWidget).
        measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int paddingH = getPaddingStart() + getPaddingEnd();
        int paddingV = getPaddingTop() + getPaddingBottom();
        mWidgetPlacement.width = (int) ((getMeasuredWidth() + paddingH) / mWidgetPlacement.density);
        mWidgetPlacement.height = (int) ((getMeasuredHeight() + paddingV) / mWidgetPlacement.density);

        super.show(aShowFlags);
    }
}
