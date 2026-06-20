package com.vectras.vm.setupwizard;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vectras.vm.main.MainActivity;

/**
 * Bootstrap-compatible setup activity fallback.
 *
 * <p>This activity must not silently jump into MainActivity when the runtime is
 * incomplete. A blind launch hides missing PRoot/rootfs/QEMU state and turns the
 * first VM run into a confusing failure. Keep this gate lightweight: no process
 * execution here, only filesystem/binary presence checks.</p>
 */
public class SetupWizard2Activity extends AppCompatActivity {
    public static final String ACTION_DEBUG_PROOT_SELF_CHECK = "com.vectras.vm.action.DEBUG_PROOT_SELF_CHECK";
    public static final String EXTRA_DEBUG_PROOT_SELF_CHECK = "debug_proot_self_check";
    public static final int ACTION_SYSTEM_UPDATE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SetupFeatureCore.SetupPostCheckResult postCheck = SetupFeatureCore.runSetupPostCheck(this);
        if (postCheck.ok) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        showBlockedSetup(postCheck);
    }

    private void showBlockedSetup(SetupFeatureCore.SetupPostCheckResult postCheck) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Vectras runtime incompleto");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView body = new TextView(this);
        body.setText("O app foi iniciado, mas o runtime ainda não está pronto.\n\n"
                + "Motivo técnico: " + postCheck.technicalReason() + "\n\n"
                + "Isso evita iniciar a Home com QEMU/PRoot/rootfs ausente e reduz falhas confusas no primeiro run.");
        body.setTextSize(15f);
        body.setPadding(0, dp(18), 0, dp(18));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button retry = new Button(this);
        retry.setText("Verificar novamente");
        retry.setOnClickListener(v -> recreate());
        root.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button continueAnyway = new Button(this);
        continueAnyway.setText("Abrir Home mesmo assim");
        continueAnyway.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        root.addView(continueAnyway, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
