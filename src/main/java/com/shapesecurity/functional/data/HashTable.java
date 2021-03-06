/*
 * Copyright 2014 Shape Security, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shapesecurity.functional.data;

import java.util.function.Consumer;

import com.shapesecurity.functional.Effect;
import com.shapesecurity.functional.F;
import com.shapesecurity.functional.F2;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.Unit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable hash trie tree implementation.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public abstract class HashTable<K, V> {
    private final static Hasher<Object> EQUALITY_HASHER = new Hasher<Object>() {
        @Override
        public int hash(@NotNull Object data) {
            return data.hashCode();
        }

        @Override
        public boolean eq(@NotNull Object o, @NotNull Object b) {
            return o.equals(b);
        }
    };

    public final static Hasher<Object> IDENTITY_HASHER = new Hasher<Object>() {
        @Override
        public int hash(@NotNull Object data) {
            return System.identityHashCode(data);
        }

        @Override
        public boolean eq(@NotNull Object o, @NotNull Object b) {
            return o == b;
        }
    };

    @NotNull
    public final Hasher<K> hasher;
    public final int length;

    protected HashTable(@NotNull Hasher<K> hasher, int length) {
        super();
        this.hasher = hasher;
        this.length = length;
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public static <K> Hasher<K> equalityHasher() {
        return (Hasher<K>) EQUALITY_HASHER;
    }

    @SuppressWarnings("unchecked")
    @NotNull
    @Deprecated
    public static <K> Hasher<K> defaultHasher() {
        return HashTable.equalityHasher();
    }

    @SuppressWarnings("unchecked")
    @NotNull
    public static <K> Hasher<K> identityHasher() {
        return (Hasher<K>) IDENTITY_HASHER;
    }

    @NotNull
    public static <K, V> HashTable<K, V> empty(@NotNull Hasher<K> hasher) {
        return new Empty<>(hasher);
    }

    @NotNull
    public static <K, V> HashTable<K, V> emptyUsingEquality() {
        return empty(HashTable.equalityHasher());
    }

    @NotNull
    public static <K, V> HashTable<K, V> emptyUsingIdentity() {
        return empty(HashTable.identityHasher());
    }

    @NotNull
    @Deprecated
    public static <K, V> HashTable<K, V> empty() {
        return HashTable.emptyUsingEquality();
    }

    @NotNull
    @Deprecated
    public static <K, V> HashTable<K, V> emptyP() {
        return HashTable.emptyUsingIdentity();
    }

    @NotNull
    public final HashTable<K, V> put(@NotNull K key, @NotNull V value) {
        return this.put(key, value, this.hasher.hash(key));
    }

    @NotNull
    public final HashTable<K, V> remove(@NotNull K key) {
        return this.remove(key, this.hasher.hash(key)).orJust(this);
    }

    @NotNull
    protected abstract HashTable<K, V> put(@NotNull K key, @NotNull V value, int hash);

    @NotNull
    protected abstract Maybe<HashTable<K, V>> remove(@NotNull K key, int hash);

    @NotNull
    public final Maybe<V> get(@NotNull K key) {
        return this.get(key, this.hasher.hash(key));
    }

    @NotNull
    protected abstract Maybe<V> get(@NotNull K key, int hash);

    @SuppressWarnings("unchecked")
    @NotNull
    public final HashTable<K, V> merge(@NotNull HashTable<K, V> tree) {
        return this.merge(tree, (a, b) -> b);
    }

    @NotNull
    public abstract HashTable<K, V> merge(@NotNull HashTable<K, V> tree, @NotNull F2<V, V, V> merger);

    @NotNull
    public abstract <A> A foldLeft(@NotNull F2<A, Pair<K, V>, A> f, @NotNull A init);

    @NotNull
    public abstract <A> A foldRight(@NotNull F2<Pair<K, V>, A, A> f, @NotNull A init);

    @NotNull
    public ImmutableList<Pair<K, V>> entries() {
        //noinspection unchecked
        Pair<K, V>[] pairs = ((Pair<K, V>[]) new Pair[this.length]);
        int[] i = new int[1];
        this.forEach(x -> pairs[i[0]++] = x);
        return ImmutableList.from(pairs);
    }

    public final void foreach(@NotNull Effect<Pair<K, V>> e) {
        this.forEach(e::e);
    }

    public abstract void forEach(@NotNull Consumer<? super Pair<K, V>> e);

    @NotNull
    public abstract Maybe<Pair<K, V>> find(@NotNull F<Pair<K, V>, Boolean> f);

    @NotNull
    public abstract <R> Maybe<R> findMap(@NotNull F<Pair<K, V>, Maybe<R>> f);

    public abstract <B> HashTable<K, B> map(@NotNull F<V, B> f);

    public boolean containsKey(@NotNull K key) {
        return this.containsKey(key, this.hasher.hash(key));
    }

    public abstract boolean containsKey(@NotNull K key, int hash);

    public boolean containsValue(@NotNull V value) {
        return this.find(p -> p.right == value).isJust();
    }

    @NotNull
    public ImmutableSet<K> keys() {
        return new ImmutableSet<>(this.map(F.constant(Unit.unit)));
    }

    /**
     * An empty hash table.
     *
     * @param <K> Key type
     * @param <V> Value type
     */
    private final static class Empty<K, V> extends HashTable<K, V> {
        protected Empty(@NotNull Hasher<K> hasher) {
            super(hasher, 0);
        }

        @NotNull
        @Override
        protected HashTable<K, V> put(@NotNull K key, @NotNull V value, int hash) {
            return new Leaf<>(this.hasher, ImmutableList.of(new Pair<>(key, value)), hash, 1);
        }

        @NotNull
        @Override
        protected Maybe<HashTable<K, V>> remove(@NotNull K key, int hash) {
            return Maybe.empty();
        }

        @NotNull
        @Override
        protected Maybe<V> get(@NotNull K key, int hash) {
            return Maybe.empty();
        }

        @NotNull
        @Override
        public HashTable<K, V> merge(@NotNull HashTable<K, V> tree, @NotNull F2<V, V, V> merger) {
            return tree;
        }

        @NotNull
        @Override
        public <A> A foldLeft(@NotNull F2<A, Pair<K, V>, A> f, @NotNull A init) {
            return init;
        }

        @NotNull
        @Override
        public <A> A foldRight(@NotNull F2<Pair<K, V>, A, A> f, @NotNull A init) {
            return init;
        }

        @Override
        public void forEach(@NotNull Consumer<? super Pair<K, V>> e) {

        }

        @NotNull
        @Override
        public Maybe<Pair<K, V>> find(@NotNull F<Pair<K, V>, Boolean> f) {
            return Maybe.empty();
        }

        @NotNull
        @Override
        public <R> Maybe<R> findMap(@NotNull F<Pair<K, V>, Maybe<R>> f) {
            return Maybe.empty();
        }

        @Override
        public <B> HashTable<K, B> map(@NotNull F<V, B> f) {
            return emptyUsingEquality();
        }

        @Override
        public boolean containsKey(@NotNull K key, int hash) {
            return false;
        }
    }

    /**
     * A leaf node that contains a list of pairs where all the keys have exactly the same hash
     * code.
     *
     * @param <K> Key type
     * @param <V> Value type
     */
    private final static class Leaf<K, V> extends HashTable<K, V> {
        @NotNull
        private final ImmutableList<Pair<K, V>> dataList;
        public int baseHash;

        protected Leaf(@NotNull Hasher<K> hasher, @NotNull ImmutableList<Pair<K, V>> dataList, int baseHash, int length) {
            super(hasher, length);
            this.dataList = dataList;
            this.baseHash = baseHash;
        }

        @NotNull
        @Override
        protected HashTable<K, V> put(@NotNull final K key, @NotNull final V value, final int hash) {
            if (hash == this.baseHash) {
                Pair<Boolean, ImmutableList<Pair<K, V>>> result = this.dataList.mapAccumL((found, kvPair) -> {
                    if (found) {
                        return new Pair<>(true, kvPair);
                    }
                    if (Leaf.this.hasher.eq(kvPair.left, key)) {
                        return new Pair<>(true, new Pair<>(key, value));
                    }
                    return new Pair<>(false, kvPair);
                }, false);
                if (result.left) {
                    return new Leaf<>(this.hasher, result.right, hash, this.length);
                }
                return new Leaf<>(this.hasher, this.dataList.cons(new Pair<>(key, value)), hash, this.length + 1);
            }
            return this.toFork().put(key, value, hash);
        }

        @NotNull
        @Override
        protected Maybe<HashTable<K, V>> remove(@NotNull final K key, int hash) {
            if (this.baseHash != hash) {
                return Maybe.empty();
            }
            Pair<Boolean, ImmutableList<Pair<K, V>>> result = this.dataList.foldRight((i, p) -> {
                if (p.left) {
                    return new Pair<>(true, p.right.cons(i));
                }
                if (Leaf.this.hasher.eq(i.left, key)) {
                    return new Pair<>(true, p.right);
                }
                return new Pair<>(false, p.right.cons(i));
            }, new Pair<>(false, ImmutableList.empty()));
            if (result.left) {
                if (this.length == 1) {
                    return Maybe.of(HashTable.emptyUsingEquality());
                }
                return Maybe.of(new Leaf<>(this.hasher, result.right, this.baseHash, this.length - 1));
            }
            return Maybe.empty();
        }

        @SuppressWarnings("unchecked")
        private Fork<K, V> toFork() {
            int subHash = this.baseHash & 31;
            HashTable<K, V>[] children = new HashTable[32];
            children[subHash] = new Leaf<>(this.hasher, this.dataList, this.baseHash >>> 5, this.length);
            return new Fork<>(this.hasher, children, this.length);
        }

        @NotNull
        @Override
        protected Maybe<V> get(@NotNull final K key, final int hash) {
            if (this.baseHash != hash) {
                return Maybe.empty();
            }
            Maybe<Pair<K, V>> pairMaybe = this.dataList.find(kvPair -> Leaf.this.hasher.eq(kvPair.left, key));
            return pairMaybe.map(p -> p.right);
        }

        @SuppressWarnings("unchecked")
        @NotNull
        @Override
        public HashTable<K, V> merge(@NotNull HashTable<K, V> tree, @NotNull final F2<V, V, V> merger) {
            if (tree instanceof Empty) {
                return this;
            } else if (tree instanceof Leaf) {
                final Leaf<K, V> leaf = (Leaf<K, V>) tree;
                if (leaf.baseHash == this.baseHash) {
                    final Pair<K, V>[] pairs = this.dataList.toArray(new Pair[this.dataList.length]);
                    ImmutableList<Pair<K, V>> right = leaf.dataList.foldLeft(
                            (@NotNull ImmutableList<Pair<K, V>> result, @NotNull Pair<K, V> kvPair) -> {
                                for (int i = 0; i < pairs.length; i++) {
                                    if (Leaf.this.hasher.eq(pairs[i].left, kvPair.left)) {
                                        pairs[i] = new Pair<>(pairs[i].left, merger.apply(pairs[i].right, kvPair.right));
                                        return result;
                                    }
                                }
                                return result.cons(kvPair);
                            }, ImmutableList.empty());
                    ImmutableList<Pair<K, V>> newList = ImmutableList.from(pairs).append(right);
                    return new Leaf<>(this.hasher, newList, this.baseHash, newList.length);
                }
            }
            return this.toFork().merge(tree, merger);
        }

        @NotNull
        public <A> A foldLeft(@NotNull F2<A, Pair<K, V>, A> f, @NotNull A init) {
            return this.dataList.foldLeft(f, init);
        }

        @NotNull
        @Override
        public <A> A foldRight(@NotNull F2<Pair<K, V>, A, A> f, @NotNull A init) {
            return this.dataList.foldRight(f, init);
        }

        @Override
        public void forEach(@NotNull Consumer<? super Pair<K, V>> e) {
            this.dataList.forEach(e);
        }

        @NotNull
        @Override
        public Maybe<Pair<K, V>> find(@NotNull F<Pair<K, V>, Boolean> f) {
            return this.dataList.find(f);
        }

        @NotNull
        @Override
        public <R> Maybe<R> findMap(@NotNull F<Pair<K, V>, Maybe<R>> f) {
            return this.dataList.findMap(f);
        }

        @Override
        public <B> Leaf<K, B> map(@NotNull F<V, B> f) {
            return new Leaf<>(this.hasher, this.dataList.map(pair -> pair.mapRight(f)), this.baseHash, this.length);
        }

        @Override
        public boolean containsKey(@NotNull K key, int hash) {
            return hash == this.baseHash
                    && this.dataList.exists(kvPair -> Leaf.this.hasher.eq(kvPair.left, key));
        }
    }

    private final static class Fork<K, V> extends HashTable<K, V> {
        @NotNull
        private final HashTable<K, V>[] children;

        private Fork(@NotNull Hasher<K> hasher, @NotNull HashTable<K, V>[] children, int length) {
            super(hasher, length);
            this.children = children;
        }

        @NotNull
        @Override
        protected HashTable<K, V> put(@NotNull K key, @NotNull V value, int hash) {
            int subHash = hash & 31;
            HashTable<K, V>[] cloned = Fork.this.children.clone();
            if (cloned[subHash] == null) {
                cloned[subHash] = new Leaf<>(Fork.this.hasher, ImmutableList.empty(), hash >>> 5, 0);
            }
            //noinspection UnnecessaryLocalVariable
            int oldLength = cloned[subHash].length;
            cloned[subHash] = cloned[subHash].put(key, value, hash >>> 5);
            return new Fork<>(this.hasher, cloned, this.length - oldLength + cloned[subHash].length);
        }

        @NotNull
        @Override
        protected Maybe<HashTable<K, V>> remove(@NotNull K key, int hash) {
            final int subHash = hash & 31;
            if (this.children[subHash] == null) {
                return Maybe.empty();
            }
            Maybe<HashTable<K, V>> removed = this.children[subHash].remove(key, hash >>> 5);
            return removed.map(newChild -> {
                HashTable<K, V>[] cloned = Fork.this.children.clone();
                cloned[subHash] = newChild;
                return new Fork<>(Fork.this.hasher, cloned, Fork.this.length - 1);
            });
        }

        @NotNull
        @Override
        protected Maybe<V> get(@NotNull K key, int hash) {
            int subHash = hash & 31;
            if (this.children[subHash] == null) {
                return Maybe.empty();
            }
            return this.children[subHash].get(key, hash >>> 5);
        }

        @NotNull
        @Override
        public Fork<K, V> merge(@NotNull HashTable<K, V> tree, @NotNull F2<V, V, V> merger) {
            if (tree instanceof Empty) {
                return this;
            } else if (tree instanceof Leaf) {
                Leaf<K, V> leaf = (Leaf<K, V>) tree;
                return this.mergeFork(leaf.toFork(), merger);
            }
            return this.mergeFork(((Fork<K, V>) tree), merger);
        }

        @NotNull
        private Fork<K, V> mergeFork(@NotNull Fork<K, V> tree, @NotNull F2<V, V, V> merger) {
            // Mutable array.
            HashTable<K, V>[] cloned = Fork.this.children.clone();
            int count = 0;
            for (int i = 0; i < cloned.length; i++) {
                if (cloned[i] == null) {
                    cloned[i] = tree.children[i];
                } else if (tree.children[i] != null) {
                    cloned[i] = cloned[i].merge(tree.children[i], merger);
                }
                if (cloned[i] != null) {
                    count += cloned[i].length;
                }
            }
            return new Fork<>(this.hasher, cloned, count);
        }

        @NotNull
        @Override
        public <A> A foldLeft(@NotNull F2<A, Pair<K, V>, A> f, @NotNull A init) {
            for (@Nullable HashTable<K, V> child : this.children) {
                if (child != null) {
                    init = child.foldLeft(f, init);
                }
            }
            return init;
        }

        @NotNull
        @Override
        public <A> A foldRight(@NotNull F2<Pair<K, V>, A, A> f, @NotNull A init) {
            for (int i = this.children.length - 1; i >= 0; i--) {
                if (this.children[i] == null) {
                    continue;
                }
                init = this.children[i].foldRight(f, init);
            }
            return init;
        }

        @Override
        public void forEach(@NotNull Consumer<? super Pair<K, V>> e) {
            for (@Nullable HashTable<K, V> child : this.children) {
                if (child != null) {
                    child.forEach(e);
                }
            }
        }

        @NotNull
        @Override
        public Maybe<Pair<K, V>> find(@NotNull F<Pair<K, V>, Boolean> f) {
            HashTable<K, V>[] children = this.children;
            for (HashTable<K, V> child : children) {
                if (child != null) {
                    Maybe<Pair<K, V>> p = child.find(f);
                    if (p.isJust()) {
                        return p;
                    }
                }
            }
            return Maybe.empty();
        }

        @NotNull
        @Override
        public <R> Maybe<R> findMap(@NotNull F<Pair<K, V>, Maybe<R>> f) {
            HashTable<K, V>[] children = this.children;
            for (HashTable<K, V> child : children) {
                if (child != null) {
                    Maybe<R> p = child.findMap(f);
                    if (p.isJust()) {
                        return p;
                    }
                }
            }
            return Maybe.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B> Fork<K, B> map(@NotNull F<V, B> f) {
            HashTable<K, B>[] clone = new HashTable[this.children.length];
            for (int i = 0; i < clone.length; i++) {
                if (this.children[i] != null) {
                    clone[i] = this.children[i].map(f);
                }
            }
            return new Fork<>(this.hasher, clone, this.length);
        }

        @Override
        public boolean containsKey(@NotNull K key, int hash) {
            int subHash = hash & 31;
            return this.children[subHash] != null && this.children[subHash].containsKey(key, hash >>> 5);
        }
    }
}
