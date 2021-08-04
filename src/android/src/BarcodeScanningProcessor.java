package tl.cordova.plugin.firebase.mlkit.barcode.scanner;

// ----------------------------------------------------------------------------
// |  Android Imports
// ----------------------------------------------------------------------------
import android.content.Context;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.UiThread;

// ----------------------------------------------------------------------------
// |  Google Imports
// ----------------------------------------------------------------------------
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

// ----------------------------------------------------------------------------
// |  Java Imports
// ----------------------------------------------------------------------------
import java.nio.ByteBuffer;
import java.util.List;

// ----------------------------------------------------------------------------
// |  Our Imports
// ----------------------------------------------------------------------------
import tl.cordova.plugin.firebase.mlkit.barcode.scanner.FrameMetadata;

public class BarcodeScanningProcessor {
  // ----------------------------------------------------------------------------
  // | Public Properties
  // ----------------------------------------------------------------------------
  public BarcodeScanningProcessor(Context p_Context) {
    _Detector = BarcodeScanning.getClient();
    if (p_Context instanceof BarcodeUpdateListener) {
      this._BarcodeUpdateListener = (BarcodeUpdateListener) p_Context;
    } else {
      throw new RuntimeException("Hosting activity must implement BarcodeUpdateListener");
    }
  }
  
  // ----------------------------------------------------------------------------
  // | Protected Properties
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // | Private Properties
  // ----------------------------------------------------------------------------
  private static final String TAG = "Barcode-Processor";
  private final BarcodeScanner _Detector;
  private BarcodeUpdateListener _BarcodeUpdateListener;

  // To keep the latest images and its metadata.
  @GuardedBy("this")
  private ByteBuffer _LatestImage;

  @GuardedBy("this")
  private FrameMetadata _LatestImageMetaData;

  // To keep the images and metadata in process.
  @GuardedBy("this")
  private ByteBuffer _ProcessingImage;

  @GuardedBy("this")
  private FrameMetadata _ProcessingMetaData;

  // ----------------------------------------------------------------------------
  // |  Public Functions
  // ----------------------------------------------------------------------------
  public synchronized void Process(ByteBuffer p_Data, FrameMetadata p_FrameMetadata) {
    _LatestImage = p_Data;
    _LatestImageMetaData = p_FrameMetadata;
    if (_ProcessingImage == null && _ProcessingMetaData == null) {
      ProcessLatestImage();
    }
  }

  public void Stop() {
    try {
      _Detector.close();
    } catch(Exception e) {
      Log.e(TAG, "Error on FirebaseVisionBarcodeDetector close.", e);
    }
  }


  // ----------------------------------------------------------------------------
  // |  Protected Functions
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  // |  Private Functions
  // ----------------------------------------------------------------------------
  private synchronized void ProcessLatestImage() {
    _ProcessingImage = _LatestImage;
    _ProcessingMetaData = _LatestImageMetaData;
    _LatestImage = null;
    _LatestImageMetaData = null;
    if (_ProcessingImage != null && _ProcessingMetaData != null) {
        ProcessImage(_ProcessingImage, _ProcessingMetaData);
    }
  }

  private void ProcessImage(ByteBuffer p_Data, final FrameMetadata p_FrameMetadata) {
    InputImage image = InputImage.fromByteBuffer(
            p_Data,
            p_FrameMetadata.getWidth(),
            p_FrameMetadata.getHeight(),
            p_FrameMetadata.getRotation(),
            InputImage.IMAGE_FORMAT_NV21);
    DetectInVisionImage(image);
  }

  private void DetectInVisionImage(InputImage p_Image) {
    _Detector.process(p_Image).addOnSuccessListener(
            new OnSuccessListener<List<Barcode>>() {
              @Override
              public void onSuccess(List<Barcode> results) {
                OnSuccess(results);
                ProcessLatestImage();
              }
            }).addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(Exception e) {
                OnFailure(e);
              }
            });
  }

  private void OnSuccess(List<Barcode> p_Barcodes) {
    for(Barcode barcode: p_Barcodes) {
      _BarcodeUpdateListener.onBarcodeDetected(barcode.getDisplayValue());
    }
  }

  private void OnFailure(Exception e) {
    Log.e(TAG, "Barcode detection failed " + e);
  }

  // ----------------------------------------------------------------------------
  // |  Helper classes
  // ----------------------------------------------------------------------------
  public interface BarcodeUpdateListener {
    @UiThread
    void onBarcodeDetected(String p_Barcode);
  }
}
