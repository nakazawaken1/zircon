package zircon.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 現在日時自動設定
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Timestamp {
    /**
     * @return true:更新ごとに自動設定, false:初期値のみ設定
     */
    boolean value() default false;
}
