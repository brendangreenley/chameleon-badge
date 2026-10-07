package com.chameleon.badge;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 100;

    private static final String PREFS_NAME = "badge";
    private static final String KEY_PKG = "pkg";
    private static final String KEY_CLS = "cls";
    private static final String KEY_NAME = "name";
    private static final String KEY_COUNT = "count";

    private MaterialToolbar toolbar;
    private ImageView appIconPreview;
    private TextView appLabelText;
    private MaterialButton pickAppButton;
    private MaterialButton pinButton;
    private TextInputLayout nameInputLayout;
    private TextInputEditText nameInput;
    private MaterialButton applyNameButton;
    private TextInputLayout badgeInputLayout;
    private TextInputEditText badgeInput;
    private MaterialButton setButton;
    private MaterialButton clearButton;
    private TextView statusText;

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<Intent> pickAppLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        appIconPreview = findViewById(R.id.app_icon_preview);
        appLabelText = findViewById(R.id.app_label_text);
        pickAppButton = findViewById(R.id.pick_app_button);
        pinButton = findViewById(R.id.pin_shortcut_button);
        nameInputLayout = findViewById(R.id.name_input_layout);
        nameInput = findViewById(R.id.name_input);
        applyNameButton = findViewById(R.id.apply_name_button);
        badgeInputLayout = findViewById(R.id.badge_input_layout);
        badgeInput = findViewById(R.id.badge_input);
        setButton = findViewById(R.id.set_button);
        clearButton = findViewById(R.id.clear_button);
        statusText = findViewById(R.id.status_text);

        toolbar.setTitle(R.string.app_name);

        notificationPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                applySavedBadge();
                            }
                        });

        pickAppLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onAppPicked);

        // Restore identity from prefs.
        String pkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PKG, null);
        if (pkg != null) {
            String cls = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CLS, null);
            String savedName = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_NAME, null);

            PackageManager pm = getPackageManager();
            CharSequence label = null;
            Bitmap icon = null;
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(appInfo);
                icon = drawableToBitmap(pm.getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            if (label != null) {
                appLabelText.setText(label);
                nameInput.setText(savedName != null ? savedName : label.toString());
            } else {
                appLabelText.setText(R.string.no_app_selected);
                nameInput.setText(R.string.app_name);
            }
            if (icon != null) {
                appIconPreview.setImageBitmap(icon);
            }
            if (savedName != null && !shortcutPinned()) {
                syncShortcut(true);
            } else {
                syncShortcut(false);
            }
        } else {
            appLabelText.setText(R.string.no_app_selected);
            nameInput.setText(R.string.app_name);
        }

        // Prefill badge from saved value.
        int saved = getSavedCount();
        if (saved > 0) {
            badgeInput.setText(String.valueOf(saved));
        }
        updateStatusText(saved > 0 ? saved : 0, saved > 0);

        pickAppButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppPickerActivity.class);
            pickAppLauncher.launch(intent);
        });
        pinButton.setOnClickListener(v -> pinIdentityShortcut());
        applyNameButton.setOnClickListener(v -> onApplyName());
        setButton.setOnClickListener(v -> onSetBadge());
        clearButton.setOnClickListener(v -> onClearBadge());

        // Android 13+: request POST_NOTIFICATIONS once at startup if not granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Re-apply saved badge so it survives reboot (if permission is OK).
        applySavedBadge();
    }

    private void onAppPicked(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) return;
        Intent data = result.getData();
        if (data == null) return;

        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PKG);
        String cls = data.getStringExtra(AppPickerActivity.EXTRA_CLS);
        if (pkg == null) return;

        PackageManager pm = getPackageManager();
        CharSequence label = null;
        Bitmap icon = null;
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(appInfo);
            icon = drawableToBitmap(pm.getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        if (label != null) {
            appLabelText.setText(label);
            nameInput.setText(label);
        }
        if (icon != null) {
            appIconPreview.setImageBitmap(icon);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String existingName = prefs.getString(KEY_NAME, null);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_PKG, pkg)
                .putString(KEY_CLS, cls);
        if (TextUtils.isEmpty(existingName) && label != null) {
            editor.putString(KEY_NAME, label.toString());
            nameInput.setText(label);
        }
        editor.apply();

        syncShortcut(true);
        applySavedBadge();
    }

    /** Saves the display name and syncs the home-screen shortcut. */
    private void onApplyName() {
        String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) return;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_NAME, name)
                .apply();

        syncShortcut(true);
        applySavedBadge();
    }

    /**
     * Syncs the pinned home-screen shortcut with the current identity (name + icon).
     * If the shortcut is already pinned, updates it in place (no dialog).
     * Otherwise, if allowPinPrompt is true, requests a pin dialog.
     */
    private void syncShortcut(boolean allowPinPrompt) {
        String name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_NAME, null);
        String pkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PKG, null);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(pkg)) return;

        Bitmap icon = null;
        try {
            icon = drawableToBitmap(getPackageManager().getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (icon == null) return;

        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null) return;

        Intent launchIntent = new Intent(Intent.ACTION_MAIN).setClass(this, MainActivity.class);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, BadgeHelper.SHORTCUT_ID)
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(launchIntent)
                .build();

        if (shortcutPinned()) {
            sm.updateShortcuts(Collections.singletonList(shortcut));
        } else if (allowPinPrompt && sm.isRequestPinShortcutSupported()) {
            sm.requestPinShortcut(shortcut, null);
        }
    }

    /** Always shows the launcher's pin dialog for the identity shortcut (borrowed name + icon). */
    private void pinIdentityShortcut() {
        String name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_NAME, null);
        String pkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PKG, null);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(pkg)) {
            Toast.makeText(this, R.string.no_app_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap icon = null;
        try {
            icon = drawableToBitmap(getPackageManager().getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (icon == null) return;

        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(this, "Current launcher does not support pinning shortcuts",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent launchIntent = new Intent(Intent.ACTION_MAIN).setClass(this, MainActivity.class);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, BadgeHelper.SHORTCUT_ID)
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(launchIntent)
                .build();
        sm.requestPinShortcut(shortcut, null);
    }

    /** True if our pinned shortcut is currently on the home screen. */
    private boolean shortcutPinned() {
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null) return false;
            for (ShortcutInfo s : sm.getShortcuts(ShortcutManager.FLAG_MATCH_PINNED)) {
                if (BadgeHelper.SHORTCUT_ID.equals(s.getId())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        int w = Math.max(1, drawable.getIntrinsicWidth());
        int h = Math.max(1, drawable.getIntrinsicHeight());
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }

    private int getSavedCount() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_COUNT, 0);
    }

    private void saveCount(int count) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_COUNT, count).apply();
    }

    private void applySavedBadge() {
        int saved = getSavedCount();
        if (saved <= 0) return;

        boolean canPost = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            canPost = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (!canPost) return;

        String pkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PKG, null);
        String cls = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CLS, null);
        String name = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_NAME, null);
        Bitmap icon = null;
        if (pkg != null) {
            try {
                icon = drawableToBitmap(getPackageManager().getApplicationIcon(pkg));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        BadgeHelper.show(this, saved,
                name != null && !name.isEmpty() ? name : getString(R.string.app_name),
                icon, pkg, cls, shortcutPinned());
    }

    private void onSetBadge() {
        String text = badgeInput.getText() == null ? "" : badgeInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            clearBadgeNow();
            return;
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            // Over 19 digits just saturates.
            value = Integer.MAX_VALUE;
        }
        if (value < 0) value = 0;
        if (value > Integer.MAX_VALUE) value = Integer.MAX_VALUE;

        if (value == 0) {
            clearBadgeNow();
        } else {
            saveCount((int) value);
            applySavedBadge();
            updateStatusText((int) value, true);
        }
    }

    private void onClearBadge() {
        badgeInput.setText("");
        clearBadgeNow();
    }

    private void clearBadgeNow() {
        String pkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_PKG, null);
        String cls = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CLS, null);
        BadgeHelper.clear(this, pkg, cls);
        saveCount(0);
        updateStatusText(0, false);
    }

    private void updateStatusText(int count, boolean active) {
        if (active) {
            statusText.setText(getString(R.string.status_badge_set, count));
        } else {
            statusText.setText(R.string.status_badge_cleared);
        }
    }
}
