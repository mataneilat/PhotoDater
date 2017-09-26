package matan.photodater;

import java.util.Calendar;

/**
 * Created by mataneilat on 25/09/2017.
 */

public class StringDate {

    public static class StringDateFactory {

        public static StringDate createStringDate(String date) {
            if (date.length() != 8) {
                return null;
            }
            return createStringDate(date.substring(0, 4), date.substring(4, 6), date.substring(6, 8));
        }

        public static StringDate createStringDate(String year, String month, String day) {
            if (!isValidYear(year) || !isValidMonth(month) || !isValidDay(day)) {
                return null;
            }
            return new StringDate(year, month, day);
        }

        private static boolean isValidYear(String year) {
            return DateTimeValidationUtils.isValidDateTimeObject(year, 4,
                    new RangePredicate<>(1970, Calendar.getInstance().get(Calendar.YEAR)));
        }

        private static boolean isValidMonth(String month) {
            return DateTimeValidationUtils.isValidDateTimeObject(month, 2, new RangePredicate<>(1, 12));
        }

        private static boolean isValidDay(String day) {
            return DateTimeValidationUtils.isValidDateTimeObject(day, 2, new RangePredicate<>(1, 31));
        }
    }

    private String year;
    private String month;
    private String day;

    public StringDate(String year, String month, String day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public String getYear() {
        return year;
    }

    public String getMonth() {
        return month;
    }

    public String getDay() {
        return day;
    }

}
