package matan.photodater;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.Call;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public interface SilentCallback<T> {

        void call(T obj);
    }

    private static final String LOG_TAG = "MAIN";

    private final static int MAX_CALLBACK_CODES = 100;


    private enum ActivityIdentifier {
        CHOOSE_IMAGE;
    }

    private enum CallbackCode {
        UPDATE_IMAGES;
    }

    private Runnable onPermissionResult;

    private Button browseImagesButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        browseImagesButton = (Button) findViewById(R.id.browseImagesButton);
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

    private void getExternalStoragePermissions(Runnable afterCallback) {
        printExternalStorageStatus();
        if (isPermittedToReadExternalStorage()) {
            afterCallback.run();
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
        if (ActivityIdentifier.values().length >= activityIdentifierOrdinal) {
            throw new IllegalArgumentException();
        }
        int callbackCodeOrdinal = code % MAX_CALLBACK_CODES;
        if (CallbackCode.values().length >= callbackCodeOrdinal) {
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
        Map<String, String> pathsToDates = setupPathsToDates(imagesPaths);
        for (Map.Entry<String, String> pathToDate : pathsToDates.entrySet()) {

        }
    }

    private Map<String,String> setupPathsToDates(List<String> imagesPaths) {
        Map<String, String> pathToDate = new HashMap<>();
        for (String imagePath : imagesPaths) {
            String date = formatDate(imagePath);
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

    private void accessGranted() {
        File picturesDirectory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Camera");
        if (!picturesDirectory.isDirectory()) {
            System.out.println("The pictures directory is somehow not a directory!");
            return;
        }
        File[] images = picturesDirectory.listFiles();
        if (images == null) {
            System.out.println("The pictures directory cannot be accessed properly");
            return;
        }
        ContentResolver contentResolver = this.getContentResolver();
        for (File image : images) {
            System.out.println(image.getName());
            try {
                ExifInterface exif = new ExifInterface(image.getAbsolutePath());
                System.out.println(exif.getAttribute(ExifInterface.TAG_DATETIME));
                String formattedDate = formatDate(image.getName());
                if (formattedDate != null) {
                    exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate);
                    exif.saveAttributes();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private String formatDate(String filename) {

        String[] fileAndExtension = filename.split("\\.");
        if (fileAndExtension.length != 2) {
            System.out.println("Could not separate filename and extension for: " + filename);
            return null;
        }
        String withoutExtension = fileAndExtension[0];
        String[] nameComponents = withoutExtension.split("_");
        if (nameComponents.length != 3) {
            System.out.println("Expected three components in filename: " + filename);
            return null;
        }
        if (!nameComponents[0].equals("IMG")) {
            System.out.println("Expected first component to be IMG in filename: " + filename);
            return null;
        }
        String date = nameComponents[1];
        if (date.length() != 8) {
            System.out.println("Expected date to have 8 digits in filename: " + filename);
            return null;
        }
        try {  Integer.valueOf(date); }
        catch (NumberFormatException e) {
            System.out.println("Expected date to contain digits in filename: " + filename);
            return null;
        }
        String time = nameComponents[2];
        if (time.length() != 6) {
            System.out.println("Expected time to have 6 digits in filename: " + filename);
            return null;
        }
        try {  Integer.valueOf(time); }
        catch (NumberFormatException e) {
            System.out.println("Expected time to contain digits in filename: " + filename);
            return null;
        }
        String[] dateTokens = new String[] { date.substring(0, 4), date.substring(4, 6), date.substring(6, 8)} ;
        String formattedDate = TextUtils.join(":", dateTokens);
        String[] timeTokens = new String[] { time.substring(0,2), time.substring(2 ,4), time.substring(4 ,6)} ;
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

    private void printImagePaths(String directoryPath) {
        File directoryFile = new File(directoryPath);
        System.out.println("Is directory: " + directoryFile.isDirectory());
        File[] imagesFiles = directoryFile.listFiles();
        for (File imageFile : imagesFiles) {
            System.out.println(imageFile.getName());
        }
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

    /* returns external storage paths (directory of external memory card) as array of Strings */
    public String[] getExternalStorageDirectories() {

        List<String> results = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //Method 1 for KitKat & above
            File[] externalDirs = getExternalFilesDirs(null);

            for (File file : externalDirs) {
                String path = file.getPath().split("/Android")[0];

                boolean addPath = false;

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    addPath = Environment.isExternalStorageRemovable(file);
                }
                else{
                    addPath = Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(file));
                }

                if(addPath){
                    results.add(path);
                }
            }
        }

        if(results.isEmpty()) { //Method 2 for all versions
            // better variation of: http://stackoverflow.com/a/40123073/5002496
            String output = "";
            try {
                final Process process = new ProcessBuilder().command("mount | grep /dev/block/vold")
                        .redirectErrorStream(true).start();
                process.waitFor();
                final InputStream is = process.getInputStream();
                final byte[] buffer = new byte[1024];
                while (is.read(buffer) != -1) {
                    output = output + new String(buffer);
                }
                is.close();
            } catch (final Exception e) {
                e.printStackTrace();
            }
            if(!output.trim().isEmpty()) {
                String devicePoints[] = output.split("\n");
                for(String voldPoint: devicePoints) {
                    results.add(voldPoint.split(" ")[2]);
                }
            }
        }

        //Below few lines is to remove paths which may not be external memory card, like OTG (feel free to comment them out)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < results.size(); i++) {
                if (!results.get(i).toLowerCase().matches(".*[0-9a-f]{4}[-][0-9a-f]{4}")) {
                    Log.d(LOG_TAG, results.get(i) + " might not be extSDcard");
                    results.remove(i--);
                }
            }
        } else {
            for (int i = 0; i < results.size(); i++) {
                if (!results.get(i).toLowerCase().contains("ext") && !results.get(i).toLowerCase().contains("sdcard")) {
                    Log.d(LOG_TAG, results.get(i)+" might not be extSDcard");
                    results.remove(i--);
                }
            }
        }

        String[] storageDirectories = new String[results.size()];
        for(int i=0; i<results.size(); ++i) storageDirectories[i] = results.get(i);

        return storageDirectories;
    }
}
