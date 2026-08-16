package de.zannagh.eunomia.configuration;

/**
 * Represents a base class for configuration items, encapsulating a single value of a generic type.
 * This class provides mechanisms for managing the value, defaulting it where necessary,
 * and enforcing type safety.<br/>
 *
 * Any inheritor of this class should be registered with {@link ConfigurationItemFactoryRegistry} for
 * serialization.
 *
 * @param <T> The type of the value held by this configuration item.
 *
 * @since 0.1.0
 */
public abstract class ConfigurationItemBase<T extends ConfigurationItem<T>> implements ConfigurationItem<T> {

    protected T value;

    public ConfigurationItemBase(T actualValue) {
        this.value = sanitize(actualValue);
    }

    public ConfigurationItemBase() {
        this.value = getDefaultValue();
    }

    /**
     * @inheritDoc
     */
    public T getValue() {
        return value;
    }

    /**
     * @inheritDoc
     */
    public void setValue(T value) {
        this.value = sanitize(value);
    }

    /**
     * Normalises a candidate value before it is stored. The base contract is only "null becomes the default";
     * subclasses narrow it further (e.g. clamping a range).
     * <p>
     * This must be applied by the value constructor as well as {@link #setValue}, because the Gson read path
     * ({@code ConfigurationItemSerializer.ConfigurationItemTypeAdapter#read}) instantiates items through the
     * single-argument constructor and never calls {@code setValue} - so a check placed only in the setter
     * would let a corrupt on-disk or on-wire value through untouched.
     */
    protected T sanitize(T candidate) {
        return candidate == null ? getDefaultValue() : candidate;
    }

    /**
     * @inheritDoc
     */
    public abstract T getDefaultValue();
}
