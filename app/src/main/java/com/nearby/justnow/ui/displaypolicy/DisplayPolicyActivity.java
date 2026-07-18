package com.nearby.justnow.ui.displaypolicy;

import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.observer.DataChangeDispatcher;
import com.nearby.justnow.databinding.ActivityDisplayPolicyBinding;
import com.nearby.justnow.ui.engine.DisplayPolicyRepository;
import com.nearby.justnow.ui.engine.DisplayPolicyValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 展示策略 YAML 管理页。
 */
public class DisplayPolicyActivity extends AppCompatActivity {

    private static final String EXPORT_FILE_NAME = "justnow-display-policy.yaml";

    private ActivityDisplayPolicyBinding mBinding;
    private DisplayPolicyRepository mRepository;
    private String mInitialYaml = "";
    private int mYamlBaseBottomMargin = 0;

    private ActivityResultLauncher<String[]> mImportLauncher;
    private ActivityResultLauncher<String> mExportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(com.nearby.justnow.ui.main.MainFragment.resolveThemeStyle(this));
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityDisplayPolicyBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        JustNowApplication app = (JustNowApplication) getApplication();
        mRepository = app.getDisplayPolicyRepository();

        registerDocumentLaunchers();
        setupToolbar();
        setupEditorInsets();
        setupButtons();
        loadEditableYaml();
    }

    private void registerDocumentLaunchers() {
        mImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importYaml(uri);
            });
        mExportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/x-yaml"),
            uri -> {
                if (uri != null) exportYaml(uri);
            });
    }

    private void setupToolbar() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.menu_display_policy);
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupEditorInsets() {
        ViewGroup.MarginLayoutParams initialParams =
                (ViewGroup.MarginLayoutParams) mBinding.etYaml.getLayoutParams();
        mYamlBaseBottomMargin = initialParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.policyContent, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navBottom);

            int bottomActionsSpace = mBinding.bottomActions.getHeight();
            ViewGroup.MarginLayoutParams actionParams =
                    (ViewGroup.MarginLayoutParams) mBinding.bottomActions.getLayoutParams();
            bottomActionsSpace += actionParams.topMargin + actionParams.bottomMargin;

            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int extraBottomMargin = imeVisible
                    ? Math.max(0, imeBottom - navBottom - bottomActionsSpace)
                    : 0;
            updateYamlBottomMargin(mYamlBaseBottomMargin + extraBottomMargin);
            return insets;
        });

        mBinding.bottomActions.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                          oldLeft, oldTop, oldRight,
                                                          oldBottom) ->
                ViewCompat.requestApplyInsets(mBinding.policyContent));
    }

    private void updateYamlBottomMargin(int bottomMargin) {
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mBinding.etYaml.getLayoutParams();
        if (params.bottomMargin == bottomMargin) return;
        params.bottomMargin = bottomMargin;
        mBinding.etYaml.setLayoutParams(params);
    }

    private void setupButtons() {
        mBinding.btnImport.setOnClickListener(v ->
            mImportLauncher.launch(new String[]{"application/x-yaml", "text/*", "*/*"}));
        mBinding.btnExport.setOnClickListener(v ->
            mExportLauncher.launch(EXPORT_FILE_NAME));
        mBinding.btnSave.setOnClickListener(v -> saveCurrentYaml());
        mBinding.btnReset.setOnClickListener(v -> showResetDialog());
    }

    private void loadEditableYaml() {
        AppDatabase.execute(() -> {
            DisplayPolicyRepository.EditableYaml editable = mRepository.getEditableYamlSync();
            runOnUiThread(() -> {
                mInitialYaml = editable.yamlText != null ? editable.yamlText : "";
                mBinding.etYaml.setText(mInitialYaml);
                renderStatus(editable);
            });
        });
    }

    private void renderStatus(DisplayPolicyRepository.EditableYaml editable) {
        if (editable.customInvalid) {
            mBinding.tvStatus.setText(getString(
                R.string.s_display_policy_status_invalid, safeMessage(editable.message)));
        } else if (editable.usingDefault) {
            if (editable.message != null) {
                mBinding.tvStatus.setText(getString(
                    R.string.s_display_policy_status_default_with_message,
                    safeMessage(editable.message)));
            } else {
                mBinding.tvStatus.setText(R.string.s_display_policy_status_default);
            }
        } else {
            mBinding.tvStatus.setText(R.string.s_display_policy_status_custom);
        }
    }

    private void saveCurrentYaml() {
        String yamlText = mBinding.etYaml.getText().toString();
        AppDatabase.execute(() -> {
            try {
                mRepository.saveCustomYamlSync(yamlText);
                notifyPolicyChanged();
                runOnUiThread(() -> {
                    mInitialYaml = yamlText;
                    mBinding.tvStatus.setText(R.string.s_display_policy_status_custom);
                    showToast(R.string.s_display_policy_save_success);
                });
            } catch (IOException | DisplayPolicyValidationException e) {
                runOnUiThread(() -> showError(R.string.s_display_policy_save_failed, e));
            }
        });
    }

    private void importYaml(Uri uri) {
        AppDatabase.execute(() -> {
            try {
                String yamlText = readUri(uri);
                mRepository.saveCustomYamlSync(yamlText);
                notifyPolicyChanged();
                runOnUiThread(() -> {
                    mInitialYaml = yamlText;
                    mBinding.etYaml.setText(yamlText);
                    mBinding.tvStatus.setText(R.string.s_display_policy_status_custom);
                    showToast(R.string.s_display_policy_import_success);
                });
            } catch (IOException | DisplayPolicyValidationException e) {
                runOnUiThread(() -> showError(R.string.s_display_policy_import_failed, e));
            }
        });
    }

    private void exportYaml(Uri uri) {
        String yamlText = mBinding.etYaml.getText().toString();
        AppDatabase.execute(() -> {
            try {
                writeUri(uri, yamlText);
                runOnUiThread(() -> showToast(R.string.s_display_policy_export_success));
            } catch (IOException e) {
                runOnUiThread(() -> showError(R.string.s_display_policy_export_failed, e));
            }
        });
    }

    private void showResetDialog() {
        String[] items = {
            getString(R.string.s_display_policy_reset_default),
            getString(R.string.s_display_policy_restore_previous)
        };
        new AlertDialog.Builder(this, R.style.ThemeOverlay_JustNow_AlertDialog)
            .setTitle(R.string.s_display_policy_reset_title)
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    resetToDefault();
                } else {
                    mBinding.etYaml.setText(mInitialYaml);
                }
            })
            .setNegativeButton(R.string.s_cancel, null)
            .show();
    }

    private void resetToDefault() {
        AppDatabase.execute(() -> {
            try {
                mRepository.resetToDefaultSync();
                String defaultYaml = mRepository.readDefaultYamlSync();
                notifyPolicyChanged();
                runOnUiThread(() -> {
                    mInitialYaml = defaultYaml;
                    mBinding.etYaml.setText(defaultYaml);
                    mBinding.tvStatus.setText(R.string.s_display_policy_status_default);
                    showToast(R.string.s_display_policy_reset_success);
                });
            } catch (IOException | DisplayPolicyValidationException e) {
                runOnUiThread(() -> showError(R.string.s_display_policy_reset_failed, e));
            }
        });
    }

    private void notifyPolicyChanged() {
        DataChangeDispatcher.notifyTaskDataChanged();
    }

    private String readUri(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException(getString(R.string.s_display_policy_read_failed_detail));
            }
            byte[] buffer = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = input.read(buffer)) >= 0) {
                sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
    }

    private void writeUri(Uri uri, String text) throws IOException {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IOException(getString(R.string.s_display_policy_write_failed_detail));
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void showError(int titleResId, Exception e) {
        mBinding.tvStatus.setText(getString(R.string.s_display_policy_error_format,
            getString(titleResId), safeMessage(e.getMessage())));
        Toast.makeText(this, getString(titleResId), Toast.LENGTH_SHORT).show();
    }

    private void showToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    private String safeMessage(String message) {
        return message != null ? message : getString(R.string.s_display_policy_unknown_error);
    }
}
