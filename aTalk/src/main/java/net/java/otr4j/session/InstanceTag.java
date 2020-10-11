package net.java.otr4j.session;

import java.util.Random;

/**
 * @author Marin Dzhigarov
 */
public class InstanceTag
{

	private static final Random RANDOM = new Random();

	public static final int ZERO_VALUE = 0;

	/**
	 * The smallest possible valid tag value.
	 */
	public static final int SMALLEST_VALUE = 0x00000100;

	/**
	 * The highest possible tag value.
	 * Note that this is -1 in the decimal representation.
	 */
	public static final int HIGHEST_VALUE = 0xffffffff;

	public static final InstanceTag ZERO_TAG = new InstanceTag(ZERO_VALUE);

	public static final InstanceTag SMALLEST_TAG = new InstanceTag(SMALLEST_VALUE);

	public static final InstanceTag HIGHEST_TAG = new InstanceTag(HIGHEST_VALUE);

	/**
	 * Range for valid instance tag values.
	 * Corrected for existence of smallest value boundary.
	 */
	private static final long RANGE = 0xfffffeffL;

	/**
	 * Value of the instance tag instance.
	 */
	private final int value;

	public static boolean isValidInstanceTag(final int tagValue) {
		// Note that the decimal representation of Java's int is always signed, that means that
		// any value over 0x7fffffff will be interpreted as a negative value. So, instead we
		// verify that the tag value is not in the "forbidden range". Other than the forbidden
		// range, every possible value of the 32 bits of memory is  acceptable.
		return !(0 < tagValue && tagValue < SMALLEST_VALUE);
	}

	public InstanceTag() {
		final long val = (long)(RANDOM.nextDouble() * RANGE) + SMALLEST_VALUE;
		// Because 0xffffffff is the maximum value for both the tag and the 32 bit integer range,
		// we are able to cast to int without loss. The (decimal) interpretation changes, though,
		// because Java's int interprets the last bit as the sign bit. This does
		// not matter, however, since we do not need to do value comparisons / ordering. We only
		// care about equal/not equal.
		this.value = (int)val;
	}

	public int getValue()
	{
		return value;
	}

	InstanceTag(final int value)
	{
		if (!isValidInstanceTag(value)) {
			throw new IllegalArgumentException("Invalid tag value.");
		}
		this.value = value;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
			return true;
		if (!(other instanceof InstanceTag))
			return false;

		InstanceTag otherInstanceTag = (InstanceTag) other;
		return this.value == otherInstanceTag.getValue();
	}

	@Override
	public int hashCode()
	{
		return value;
	}
}
