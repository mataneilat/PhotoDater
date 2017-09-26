package matan.photodater;

import com.android.internal.util.Predicate;

/**
 * Created by mataneilat on 26/09/2017.
 */
public class DateTimeValidationUtils {

    public static boolean isValidDateTimeObject(String str, int expectedLength,
                                                Predicate<Integer> predicate) {
        if (str.length() != expectedLength) {
            return false;
        }
        try {
            Integer intObj = Integer.valueOf(str);
            return intObj != null && predicate.apply(intObj);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
