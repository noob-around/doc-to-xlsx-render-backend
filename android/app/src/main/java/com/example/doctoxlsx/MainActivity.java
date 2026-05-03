package com.example.doctoxlsx;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_FILE = 1001;
    private static final int REQUEST_CREATE_XLSX = 1002;
    private static final String BACKEND_URL = "https://doc-to-xlsx-backend.onrender.com/convert";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Button chooseButton;
    private Button uploadButton;
    private Button openButton;
    private ProgressBar progressBar;
    private TextView fileNameText;
    private TextView statusText;
    private Uri selectedFileUri;
    private Uri savedWorkbookUri;
    private String selectedFileName = "";
    private byte[] pendingWorkbook;
    private String pendingOutputName = "converted.xlsx";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private View createContentView() {
        int padding = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(248, 249, 251));

        TextView title = new TextView(this);
        title.setText(R.string.app_title);
        title.setTextColor(Color.rgb(31, 33, 41));
        title.setTextSize(32);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.app_subtitle);
        subtitle.setTextColor(Color.rgb(101, 111, 115));
        subtitle.setTextSize(18);
        subtitle.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(16), 0, dp(40));
        root.addView(subtitle, subtitleParams);

        chooseButton = new Button(this);
        chooseButton.setText(R.string.choose_file);
        chooseButton.setTextSize(18);
        chooseButton.setTextColor(Color.WHITE);
        chooseButton.setBackgroundColor(Color.rgb(67, 132, 128));
        chooseButton.setOnClickListener(view -> openFilePicker());
        root.addView(chooseButton, fullWidthParams(0, 0));

        fileNameText = new TextView(this);
        fileNameText.setText(R.string.no_file_selected);
        fileNameText.setTextColor(Color.rgb(101, 111, 115));
        fileNameText.setTextSize(17);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fileParams.setMargins(0, dp(28), 0, dp(24));
        root.addView(fileNameText, fileParams);

        uploadButton = new Button(this);
        uploadButton.setText(R.string.upload_and_convert);
        uploadButton.setTextSize(17);
        uploadButton.setEnabled(false);
        uploadButton.setOnClickListener(view -> uploadSelectedFile());
        root.addView(uploadButton, fullWidthParams(0, 0));

        statusText = new TextView(this);
        statusText.setText(R.string.status_idle);
        statusText.setTextColor(Color.rgb(101, 111, 115));
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(28), 0, dp(20));
        root.addView(statusText, statusParams);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        openButton = new Button(this);
        openButton.setText(R.string.open_generated_file);
        openButton.setTextSize(17);
        openButton.setEnabled(false);
        openButton.setOnClickListener(view -> openSavedWorkbook());
        root.addView(openButton, fullWidthParams(dp(28), 0));

        TextView backendText = new TextView(this);
        backendText.setText(R.string.backend_label);
        backendText.setTextColor(Color.rgb(132, 142, 146));
        backendText.setTextSize(14);
        backendText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams backendParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backendParams.setMargins(0, dp(48), 0, 0);
        root.addView(backendText, backendParams);

        return root;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "text/plain"
        });
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == REQUEST_PICK_FILE) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                selectedFileUri = fileUri;
                selectedFileName = getDisplayName(fileUri);
                pendingOutputName = makeOutputName(selectedFileName);
                savedWorkbookUri = null;
                pendingWorkbook = null;
                fileNameText.setText(selectedFileName);
                uploadButton.setEnabled(true);
                openButton.setEnabled(false);
                statusText.setText(R.string.status_file_selected);
            }
            return;
        }

        if (requestCode == REQUEST_CREATE_XLSX) {
            Uri saveUri = data.getData();
            if (saveUri != null && pendingWorkbook != null) {
                saveWorkbook(saveUri);
            }
        }
    }

    private void uploadSelectedFile() {
        if (selectedFileUri == null) {
            showError(getString(R.string.error_no_file));
            return;
        }
        uploadFile(selectedFileUri, selectedFileName);
    }

    private void uploadFile(Uri fileUri, String fileName) {
        setBusy(true, getString(R.string.status_reading));

        executor.execute(() -> {
            try {
                byte[] fileBytes = readAllBytes(fileUri);
                pendingOutputName = makeOutputName(fileName);

                runOnUiThread(() -> statusText.setText(R.string.status_uploading));

                RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), fileBytes);
                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", fileName, fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(BACKEND_URL)
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> showError(getString(R.string.error_upload, e.getMessage())));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try (Response closeable = response) {
                            if (!closeable.isSuccessful() || closeable.body() == null) {
                                String message = closeable.body() == null ? "" : closeable.body().string();
                                runOnUiThread(() -> showError(getString(R.string.error_server, closeable.code(), message)));
                                return;
                            }

                            pendingWorkbook = closeable.body().bytes();
                            runOnUiThread(() -> {
                                setBusy(false, getString(R.string.status_ready_to_save));
                                openSavePicker();
                            });
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(getString(R.string.error_upload, e.getMessage())));
            }
        });
    }

    private void openSavePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, pendingOutputName);
        startActivityForResult(intent, REQUEST_CREATE_XLSX);
    }

    private void saveWorkbook(Uri saveUri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(saveUri)) {
            if (outputStream == null) {
                showError(getString(R.string.error_save, getString(R.string.error_open_output)));
                return;
            }
            outputStream.write(pendingWorkbook);
            pendingWorkbook = null;
            savedWorkbookUri = saveUri;
            takePersistableReadPermission(saveUri);
            openButton.setEnabled(true);
            setBusy(false, getString(R.string.status_saved));
            Toast.makeText(this, R.string.status_saved, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            showError(getString(R.string.error_save, e.getMessage()));
        }
    }

    private void takePersistableReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant temporary access only; immediate opening still works.
        }
    }

    private void openSavedWorkbook() {
        if (savedWorkbookUri == null) {
            showError(getString(R.string.error_no_output));
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(savedWorkbookUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)));
        } catch (ActivityNotFoundException e) {
            showError(getString(R.string.error_no_viewer));
        }
    }

    private byte[] readAllBytes(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException("Cannot open selected file");
            }

            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return "upload.docx";
    }

    private String makeOutputName(String inputName) {
        String name = inputName == null || inputName.trim().isEmpty() ? "converted" : inputName.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return String.format(Locale.US, "%s.xlsx", name);
    }

    private void setBusy(boolean busy, String message) {
        chooseButton.setEnabled(!busy);
        uploadButton.setEnabled(!busy && selectedFileUri != null);
        openButton.setEnabled(!busy && savedWorkbookUri != null);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setText(message);
    }

    private void showError(String message) {
        setBusy(false, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams fullWidthParams(int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        params.setMargins(0, topMargin, 0, bottomMargin);
        return params;
    }
}
