package matan.photodater;

import com.android.internal.util.Predicate;

/**
 * Created by mataneilat on 26/09/2017.
 */
public class RangePredicate<T extends Comparable<T>> implements Predicate<T> {

    private T minimum;
    private T maximum;

    public RangePredicate(T minimum, T maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public boolean apply(T obj) {
        return obj.compareTo(minimum) >= 0 && obj.compareTo(maximum) <= 0;
    }
}
