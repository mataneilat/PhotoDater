package matan.photodater;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Created by mataneilat on 26/09/2017.
 */
public class AlertDialogFragment extends DialogFragment {

    private static final String MESSAGE_ARGUMENT_KEY = "message";

    public interface AlertableActivity {

        public void doPositiveClick();

        public void doNegativeClick();
    }

    public static AlertDialogFragment newInstance(String message) {
        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putString(MESSAGE_ARGUMENT_KEY, message);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String message = getArguments().getString(MESSAGE_ARGUMENT_KEY);

        return new AlertDialog.Builder(getActivity())
                .setMessage(message)
                .setPositiveButton(R.string.alert_dialog_ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((AlertableActivity)getActivity()).doPositiveClick();
                            }
                        }
                )
                .setNegativeButton(R.string.alert_dialog_cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ((AlertableActivity)getActivity()).doNegativeClick();
                            }
                        }
                )
                .create();
    }
}