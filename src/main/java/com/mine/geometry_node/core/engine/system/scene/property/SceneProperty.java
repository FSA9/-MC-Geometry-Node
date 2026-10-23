package com.mine.geometry_node.core.engine.system.scene.property;

import java.util.Objects;
import java.util.function.Consumer;

/** Shared property contract: server validation and client forms use the same registered fields. */
public record SceneProperty(String key,String labelKey,String defaultValue,Consumer<String> validate) {
    public SceneProperty {Objects.requireNonNull(key);Objects.requireNonNull(labelKey);Objects.requireNonNull(defaultValue);Objects.requireNonNull(validate);validate.accept(defaultValue);}
    public static SceneProperty number(String key,double value,double min,double max,boolean integer){
        return new SceneProperty(key,"geometry_node.game_view.scene."+key,Double.toString(value),text->{
            double n=Double.parseDouble(text);
            if(!Double.isFinite(n)||n<min||n>max||(integer&&n!=Math.rint(n)))throw new IllegalArgumentException("Invalid "+key);
        });
    }
}
