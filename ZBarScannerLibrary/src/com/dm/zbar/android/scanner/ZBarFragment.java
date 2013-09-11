package com.dm.zbar.android.scanner;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import net.sourceforge.zbar.*;

/**
 * Created with IntelliJ IDEA.
 * User: trhura
 * Date: 9/11/13
 * Time: 10:24 AM
 * To change this template use File | Settings | File Templates.
 */
public class ZBarFragment extends Fragment implements Camera.PreviewCallback {
    static final String TAG = "ZBarFragment";
    private CameraPreview preview;
    private Camera camera;
    private ImageScanner scanner;
    private ResultListener listener;

    static
    {
        System.loadLibrary("iconv");
    }

    public interface ResultListener
    {
        public void onResult(String result);
    }

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);

        /* Check whether there is a camera available */
        PackageManager pm  = activity.getPackageManager();
        Boolean has_camera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);

        if (!has_camera) {
            Log.e(TAG, "The system must have camera feature.");
            throw new RuntimeException(
                    "The system must have camera feature."
            );
        }

        if (!(activity instanceof ResultListener)) {
            Log.e (TAG, "The attached activity must implement ResultListener.");
            throw new RuntimeException(
                    "The attached activity must implement ResultListener."
            );
        }

        listener = (ResultListener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Create and configure the ImageScanner;
        setupScanner();

        // Create a RelativeLayout container that will hold a SurfaceView,
        // and set it as the content of our activity.
        preview = new CameraPreview (getActivity(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return preview;
    }

    private void setupScanner()
    {
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

/*        int[] symbols = getIntent().getIntArrayExtra(SCAN_MODES);
        if (symbols != null) {
            scanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
            for (int symbol : symbols) {
                scanner.setConfig(symbol, Config.ENABLE, 1);
            }
        }*/
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.d (TAG, "Resuming " + TAG);

        // Open the default i.e. the first rear facing camera.
        camera = Camera.open();
        if(camera == null) {
            // Cancel request if camera is null.
            Log.e (TAG, "Unable to open camera.");
            return;
        }

        preview.setCamera(camera);
        preview.showSurfaceView();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        Log.d (TAG, "Pausing " + TAG);

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (camera != null) {
            preview.setCamera(null);
            camera.cancelAutoFocus();
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();

            // According to Jason Kuang on http://stackoverflow.com/questions/6519120/how-to-recover-camera-preview-from-sleep,
            // there might be surface recreation problems when the device goes to sleep. So lets just hide it and
            // recreate on resume
            preview.hideSurfaceView();
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();

        Image barcode = new Image(size.width, size.height, "Y800");
        barcode.setData(data);

        int result = scanner.scanImage(barcode);

        if (result != 0) {
            this.camera.cancelAutoFocus();
            this.camera.setPreviewCallback(null);
            this.camera.stopPreview();
            this.preview.hideSurfaceView();
            //this.camera.release ();

            SymbolSet syms = scanner.getResults();
            Log.d (TAG, "Got some results, looking for non-empty string...");
            for (Symbol sym : syms) {
                String symData = sym.getData();
                if (!TextUtils.isEmpty(symData)) {
                    listener.onResult(symData);
                    break;
                }
            }
        }
    }
}