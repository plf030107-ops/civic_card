package hk.eta.civiccard.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import hk.eta.civiccard.R;
import hk.eta.civiccard.config.CardConfig;
import hk.eta.civiccard.config.ConfigStore;

/**
 * 选择卡片要显示的状态项，最多 5 项。
 *
 * 点击整行切换勾选，已选的保持高亮。
 */
public class StatusPickActivity extends Activity {

    private final Set<String> mSelected = new LinkedHashSet<>();
    private ArrayAdapter<String> mAdapter;
    private ListView mLv;
    private TextView mHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_pick);

        mHint = findViewById(R.id.tvHint);
        mLv = findViewById(R.id.lvStatus);

        // 载入已选
        mSelected.addAll(ConfigStore.getStatusKeys(this));

        final List<String> labels = new ArrayList<>();
        for (CardConfig.StatusItem it : CardConfig.ALL_STATUS) {
            labels.add(it.label);
        }

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                CardConfig.StatusItem it = CardConfig.ALL_STATUS[position];
                boolean on = mSelected.contains(it.key);
                tv.setText((on ? "☑ " : "☐ ") + it.label);
                tv.setTextColor(getColor(on ? R.color.ok : R.color.text_primary));
                tv.setPadding(24, 28, 24, 28);
                return tv;
            }
        };
        mLv.setAdapter(mAdapter);

        mLv.setOnItemClickListener((parent, view, position, id) -> {
            CardConfig.StatusItem it = CardConfig.ALL_STATUS[position];
            if (mSelected.contains(it.key)) {
                mSelected.remove(it.key);
            } else {
                if (mSelected.size() >= CardConfig.MAX_STATUS) {
                    Toast.makeText(this,
                            "最多选 " + CardConfig.MAX_STATUS + " 项",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                mSelected.add(it.key);
            }
            mAdapter.notifyDataSetChanged();
            updateHint();
        });

        Button done = findViewById(R.id.btnDone);
        done.setOnClickListener(v -> {
            if (mSelected.isEmpty()) {
                Toast.makeText(this, "至少选一项", Toast.LENGTH_SHORT).show();
                return;
            }
            ConfigStore.setStatusKeys(this, new ArrayList<>(mSelected));
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });

        updateHint();
    }

    private void updateHint() {
        mHint.setText("已选 " + mSelected.size() + " / " + CardConfig.MAX_STATUS + " 项");
    }
}
