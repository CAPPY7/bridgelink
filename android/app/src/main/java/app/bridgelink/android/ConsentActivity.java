package app.bridgelink.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/** The Mac asked to see the screen or the camera. The phone must tap Allow. */
public class ConsentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if ("cam".equals(getIntent().getStringExtra("kind"))) {
            if (needsCamera()) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 8);
                return;
            }
            startCamera();
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), 7);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 7 && result == RESULT_OK && data != null) {
            Intent service = new Intent(this, MirrorService.class);
            service.putExtra("code", result);
            service.putExtra("data", data);
            service.putExtra("quality", getIntent().getStringExtra("quality"));
            ContextCompat.startForegroundService(this, service);
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 8 && !needsCamera()) startCamera();
        else finish();
    }

    private boolean needsCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        Intent service = new Intent(this, CamService.class);
        service.putExtra("facing", getIntent().getStringExtra("facing"));
        service.putExtra("zoom", getIntent().getIntExtra("zoom", 1));
        service.putExtra("flash", getIntent().getBooleanExtra("flash", false));
        service.putExtra("mic", getIntent().getBooleanExtra("mic", false));
        ContextCompat.startForegroundService(this, service);
        finish();
    }
}
