package com.chameleon.badge;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AppPickerActivity extends AppCompatActivity {

    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_CLS = "cls";

    private ListView listView;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        listView = findViewById(R.id.app_list);

        List<AppItem> items = loadApps();
        adapter = new AppAdapter(this, items);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppItem item = items.get(position);
                Intent data = new Intent();
                data.putExtra(EXTRA_PKG, item.pkg);
                data.putExtra(EXTRA_CLS, item.cls);
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        String self = getPackageName();

        List<AppItem> items = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self)) continue;
            CharSequence label = ri.loadLabel(pm);
            String name = (label == null ? "" : label.toString());
            items.add(new AppItem(pkg,
                    ri.activityInfo.name,
                    name,
                    ri.loadIcon(pm)));
        }
        Collections.sort(items, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return items;
    }

    private static class AppItem {
        final String pkg;
        final String cls;
        final String label;
        final Drawable icon;

        AppItem(String pkg, String cls, String label, Drawable icon) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.icon = icon;
        }
    }

    private class AppAdapter extends ArrayAdapter<AppItem> {
        private final LayoutInflater inflater;

        AppAdapter(AppPickerActivity ctx, List<AppItem> items) {
            super(ctx, R.layout.item_app, items);
            inflater = LayoutInflater.from(ctx);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.app_icon);
                holder.label = convertView.findViewById(R.id.app_label);
                holder.pkg = convertView.findViewById(R.id.app_package);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            AppItem item = getItem(position);
            holder.icon.setImageDrawable(item.icon);
            holder.label.setText(item.label);
            holder.pkg.setText(item.pkg);
            return convertView;
        }
    }

    private static class ViewHolder {
        ImageView icon;
        TextView label;
        TextView pkg;
    }
}
