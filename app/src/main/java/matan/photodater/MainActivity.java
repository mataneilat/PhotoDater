package matan.photodater;

import android.Manifest;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AlertDialogFragment.AlertableActivity {

    private static final String LOG_TAG = "MAIN";

    private final static int MAX_CALLBACK_CODES = 100;


    private enum ActivityIdentifier {
        CHOOSE_IMAGE;
    }

    private enum CallbackCode {
        UPDATE_IMAGES;
    }

    private Runnable onPermissionResult;

    private DialogCallback dialogCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Button browseImagesButton = (Button) findViewById(R.id.browseImagesButton);
        browseImagesButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getExternalStoragePermissions(new Runnable() {
                    @Override
                    public void run() {
                        browseImages(CallbackCode.UPDATE_IMAGES);
                    }
                });
            }
        });
    }

    private void showDialog(int photosChosen, int photosParsed, DialogCallback dialogCallback) {
        this.dialogCallback = dialogCallback;
        DialogFragment newFragment = AlertDialogFragment.newInstance(formatDialogMessage(photosChosen, photosParsed));
        newFragment.show(getFragmentManager(), "dialog");
    }

    private String formatDialogMessage(int photosChosen, int photosParsed) {
        return String.format("%d photos chosen, out of which %d had a recognizable name format. Update?",
                photosChosen, photosParsed);
    }

    @Override
    public void doPositiveClick() {
        if (dialogCallback != null) {
            dialogCallback.dialogConfirmed();
        }
        dialogCallback = null;
    }

    @Override
    public void doNegativeClick() {
        if (dialogCallback != null) {
            dialogCallback.dialogCancelled();
        }
        dialogCallback = null;
    }

    private void getExternalStoragePermissions(Runnable afterCallback) {
        printExternalStorageStatus();
        if (isPermittedToReadExternalStorage()) {
            afterCallback.run();
            return;
        }
        onPermissionResult = afterCallback;
        askForPermissionToReadExternalStorage();
    }

    private void browseImages(CallbackCode callbackCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select files to update"),
                    packCode(ActivityIdentifier.CHOOSE_IMAGE, callbackCode));
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private int packCode(ActivityIdentifier activityIdentifier, CallbackCode callbackCode) {
        return activityIdentifier.ordinal() * MAX_CALLBACK_CODES + callbackCode.ordinal();
    }

    private Pair<ActivityIdentifier, CallbackCode> unpackCode(int code) throws IllegalArgumentException {
        int activityIdentifierOrdinal = code / MAX_CALLBACK_CODES;
        if (ActivityIdentifier.values().length <= activityIdentifierOrdinal) {
            throw new IllegalArgumentException();
        }
        int callbackCodeOrdinal = code % MAX_CALLBACK_CODES;
        if (CallbackCode.values().length <= callbackCodeOrdinal) {
            throw new IllegalArgumentException();
        }
        return new Pair<>(ActivityIdentifier.values()[activityIdentifierOrdinal],
                CallbackCode.values()[callbackCodeOrdinal]);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Pair<ActivityIdentifier, CallbackCode> activityIdentifierCallbackCodePair = unpackCode(requestCode);
            switch (activityIdentifierCallbackCodePair.first) {
                case CHOOSE_IMAGE:
                    switch (activityIdentifierCallbackCodePair.second) {
                        case UPDATE_IMAGES:
                            ClipData clipData = data.getClipData();
                            List<String> imagesPaths = new ArrayList<>();
                            if (clipData != null) {
                                for (int i = 0; i < clipData.getItemCount(); i++) {
                                    imagesPaths.add(getRealPathFromURI(clipData.getItemAt(i).getUri()));
                                }
                            }
                            updateImages(imagesPaths);
                            break;
                    }
                    break;

            }
        }
    }

    private void updateImages(List<String> imagesPaths) {
        // This can clearly be done using one iteration, but it seems redundant optimization since
        // it is indeed possible that we would like to show some feedback such as promting the user
        // with the number of images found and asking for permission to continue.
        final Map<String, String> pathsToDates = setupPathsToDates(imagesPaths);

        showDialog(imagesPaths.size(), pathsToDates.size(), new DialogCallback() {
            @Override
            public void dialogConfirmed() {
                for (Map.Entry<String, String> pathToDate : pathsToDates.entrySet()) {
                    updateExif(pathToDate.getKey(), pathToDate.getValue());
                }
            }

            @Override
            public void dialogCancelled() {

            }
        });

    }

    private Map<String,String> setupPathsToDates(List<String> imagesPaths) {
        Map<String, String> pathToDate = new HashMap<>();
        for (String imagePath : imagesPaths) {
            File imageFile = new File(imagePath);
            if (!imageFile.isFile()) {
                continue;
            }
            String imageFilename = imageFile.getName();
            String date = parseDate(imageFilename);
            if (date != null) {
                pathToDate.put(imagePath, date);
            }
        }
        return pathToDate;
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private void updateExif(String imagePath, String date) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            System.out.println(exif.getAttribute(ExifInterface.TAG_DATETIME));
            if (date != null) {
                System.out.println("UPDATEEEEE: " + imagePath + " " + date);
                exif.setAttribute(ExifInterface.TAG_DATETIME, date);
                exif.saveAttributes();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
}

    private String parseDate(String filename) {
        String[] fileAndExtension = filename.split("\\.");
        if (fileAndExtension.length != 2) {
            System.out.println("Could not separate filename and extension for: " + filename);
            return null;
        }
        String withoutExtension = fileAndExtension[0];
        if (withoutExtension.startsWith("IMG")) {
            return dateForIMGFormat(withoutExtension);
        } else {
            return null;
        }
    }

    private String dateForIMGFormat(String filename) {
        String[] nameComponents = filename.split("_");
        if (nameComponents.length != 3) {
            System.out.println("Expected three components in filename: " + filename);
            return null;
        }
        String date = nameComponents[1];

        StringDate stringDate = StringDate.StringDateFactory.createStringDate(date);
        if (stringDate == null) {
            return null;
        }

        String time = nameComponents[2];
        StringTime stringTime = StringTime.StringTimeFactory.createStringTime(time);
        if (stringTime == null) {
            return null;
        }

        return formatDateTime(stringDate, stringTime);
    }

    private String formatDateTime(StringDate date, StringTime time) {
        String[] dateTokens = new String[] {date.getYear(), date.getMonth(), date.getDay()};
        String formattedDate = TextUtils.join(":", dateTokens);

        String[] timeTokens = new String[] {time.getHour(), time.getMinute(), time.getSecond()};
        String formattedTime = TextUtils.join(":", timeTokens);
        return TextUtils.join(" ", new String[] {formattedDate, formattedTime});
    }

    private boolean isPermittedToReadExternalStorage() {
        return (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private void askForPermissionToReadExternalStorage() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (onPermissionResult != null) {
                        onPermissionResult.run();
                        onPermissionResult = null;
                    }
                } else {
                    System.out.println("Permission denied");
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void printExternalStorageStatus() {
        System.out.println("Is readable: " + isExternalStorageReadable());
        System.out.println("Is writeable: " + isExternalStorageWritable());
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

}
