package matan.photodater;

/**
 * Created by mataneilat on 26/09/2017.
 */
public class StringTime {

    public static class StringTimeFactory {
        public static StringTime createStringTime(String time) {
            if (time.length() != 6) {
                return null;
            }
            return new StringTime(time.substring(0,2), time.substring(2 ,4), time.substring(4 ,6));
        }

        public static StringTime createStringTime(String hour, String minute, String second) {
            if (!isValidHour(hour) || !isValidMinute(minute) || !isValieSecond(second)) {
                return null;
            }
            return new StringTime(hour, minute, second);
        }

        private static boolean isValidHour(String hour) {
            return DateTimeValidationUtils.isValidDateTimeObject(hour, 2, new RangePredicate<>(0, 23));
        }

        private static boolean isValidMinute(String minute) {
            return DateTimeValidationUtils.isValidDateTimeObject(minute, 2, new RangePredicate<Integer>(0, 59));
        }

        private static boolean isValieSecond(String second) {
            return DateTimeValidationUtils.isValidDateTimeObject(second, 2, new RangePredicate<>(0, 59));
        }
    }

    private String hour;
    private String minute;
    private String second;

    public StringTime(String hour, String minute, String second) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    public String getHour() {
        return hour;
    }

    public String getMinute() {
        return minute;
    }

    public String getSecond() {
        return second;
    }


}
