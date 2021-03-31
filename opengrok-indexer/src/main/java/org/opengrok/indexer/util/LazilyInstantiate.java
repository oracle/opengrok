/*
 * The contents of this file are Copyright (c) 2014,
 * https://programmingideaswithjake.wordpress.com/about/ made available under
 * free license,
 * https://programmingideaswithjake.wordpress.com/2014/10/05/java-functional-lazy-instantiation/
 * https://github.com/EZGames/functional-java/blob/master/LICENSE :
 * "There is no license on this code.
 * It's true open-ware.  Do what you want with it.
 * It's only meant to be helpful
 * I'd prefer that you don't take credit for it, though.  You don't have to give
 * credit; just don't take it."
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.util.function.Supplier;

/**
 * {@code LazilyInstantiate} is a quick class to make lazily instantiating
 * objects easy. All you need to know for working with this class is its two
 * public methods: one static factory for creating the object, and another for
 * initiating the instantiation and retrieval of the object.
 * <p>
 * Also, another benefit is that it's thread safe, but only blocks when
 * initially instantiating the object. After that, it stops blocking and removes
 * unnecessary checks for whether the object is instantiated.
 * <p>
 * Here's an example of it being used for implementing a singleton:
 * <code>
 * public class Singleton<br>
 * {<br>
 * &nbsp; &nbsp; private static Supplier&lt;Singleton&gt; instance =
 * LazilyInstantiate.using(() -&gt; new Singleton());<br>
 * &nbsp; &nbsp; //other fields<br>
 * <br>
 * &nbsp; &nbsp; public static getInstance()<br>
 * &nbsp; &nbsp; {<br>
 * &nbsp; &nbsp; &nbsp; &nbsp; instance.get();<br>
 * &nbsp; &nbsp; }<br>
 * <br>
 * &nbsp; &nbsp; //other methods<br>
 * <br>
 * &nbsp; &nbsp; private Singleton()<br>
 * &nbsp; &nbsp; {<br>
 * &nbsp; &nbsp; &nbsp; &nbsp; //contructor stuff<br>
 * &nbsp; &nbsp; }<br>
 * }<br>
 * </code>
 * <p>
 * So, here are the changes you'll need to apply in your code:
 * <ul>
 * <li>Change the type of the lazily instantiated object to a {@code Supplier}
 * of that <i>type</i>
 * <li>Have it set to LazilyInstantiate.using() where the argument is
 * {@code () -> <instantiation code>} You could also use a method reference,
 * which, for the example above, would be {@code Singleton::new} instead of
 * {@code () -> new Singleton()}</li>
 * <li>Whatever asks for the object, asks for the {@code Supplier} object, then
 * {@code .get()}</li>
 * </ul>
 *
 * @param <T> the type of object that you're trying to lazily instantiate
 */
public class LazilyInstantiate<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private Supplier<T> current;
    private volatile boolean active;

    public static <T> LazilyInstantiate<T> using(Supplier<T> supplier) {
        return new LazilyInstantiate<>(supplier);
    }

    /**
     * Executes the {@link #using(java.util.function.Supplier)} supplier in a
     * thread-safe manner if it has not yet been executed, and keeps the result
     * to provide to every caller of this method.
     * @return the result of {@code supplier}
     */
    @Override
    public T get() {
        return current.get();
    }

    /**
     * Gets a value indicating if the instance is active and no longer
     * technically lazy, since {@link #get()} has been called.
     */
    public boolean isActive() {
        return active;
    }

    private LazilyInstantiate(Supplier<T> supplier) {
        this.supplier = supplier;
        this.current = this::swapper;
    }

    //swaps the itself out for a supplier of an instantiated object
    private synchronized T swapper() {
        if (!(current instanceof Factory)) {
            T obj = supplier.get();
            current = new Factory<>(obj);
            active = true;
        }
        return current.get();
    }

    private static class Factory<U> implements Supplier<U> {

        private final U obj;

        Factory(U obj) {
            this.obj = obj;
        }

        @Override
        public U get() {
            return obj;
        }
    }
}
