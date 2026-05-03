package com.example.doctoxlsx;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
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
    private ProgressBar progressBar;
    private TextView statusText;
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

        TextView title = new TextView(this);
        title.setText(R.string.app_title);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setText(R.string.status_idle);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(24), 0, dp(24));
        root.addView(statusText, statusParams);

        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        chooseButton = new Button(this);
        chooseButton.setText(R.string.choose_file);
        chooseButton.setOnClickListener(view -> openFilePicker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, dp(24), 0, 0);
        root.addView(chooseButton, buttonParams);

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
                uploadFile(fileUri);
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

    private void uploadFile(Uri fileUri) {
        setBusy(true, getString(R.string.status_reading));

        executor.execute(() -> {
            try {
                String fileName = getDisplayName(fileUri);
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
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, pendingOutputName);
        startActivityForResult(intent, REQUEST_CREATE_XLSX);
    }

    private void saveWorkbook(Uri saveUri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(saveUri)) {
            if (outputStream == null) {
                showError(getString(R.string.error_save, "Cannot open output file"));
                return;
            }
            outputStream.write(pendingWorkbook);
            pendingWorkbook = null;
            setBusy(false, getString(R.string.status_saved));
            Toast.makeText(this, R.string.status_saved, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            showError(getString(R.string.error_save, e.getMessage()));
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
}
