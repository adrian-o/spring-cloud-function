/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.cloud.function.core.FluxConsumer;
import org.springframework.cloud.function.core.FluxFunction;
import org.springframework.cloud.function.core.FluxSupplier;
import org.springframework.cloud.function.core.FluxToMonoFunction;
import org.springframework.cloud.function.core.MonoToFluxFunction;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * @param <T> target type
 * @author Dave Syer
 * @author Oleg Zhurakousky
 */
public class FunctionRegistration<T> implements BeanNameAware {

	private final Set<String> names = new LinkedHashSet<>();

	private final Map<String, String> properties = new LinkedHashMap<>();

	private T target;

	private FunctionType type;

	/**
	 * Creates instance of FunctionRegistration.
	 * @param target instance of {@link Supplier}, {@link Function} or {@link Consumer}
	 * @param names additional set of names for this registration. Additional names can be
	 * provided {@link #name(String)} or {@link #names(String...)} operations.
	 */
	public FunctionRegistration(T target, String... names) {
		Assert.notNull(target, "'target' must not be null");
		this.target = target;
		this.names(names);
	}

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public Set<String> getNames() {
		return this.names;
	}

	/**
	 * Will set the names for this registration clearing all previous names first. If you
	 * want to add a name or set or names to the existing set of names use
	 * {@link #names(Collection)} or {@link #name(String)} or {@link #names(String...)}
	 * operations.
	 * @param names - bean names
	 */
	public void setNames(Set<String> names) {
		this.names.clear();
		this.names.addAll(names);
	}

	public FunctionType getType() {
		return this.type;
	}

	public T getTarget() {
		return this.target;
	}

	public FunctionRegistration<T> properties(Map<String, String> properties) {
		this.properties.putAll(properties);
		return this;
	}

	public FunctionRegistration<T> type(Type type) {
		this.type = FunctionType.of(type);
		return this;
	}

	public FunctionRegistration<T> type(FunctionType type) {
		this.type = type;
		return this;
	}

	/**
	 * Allows to override the target of this registration with a new target that typically
	 * wraps the original target. This typically happens when original target is wrapped
	 * into its {@link Flux} counterpart (e.g., Function into FluxFunction)
	 * @param target new target
	 * @return this registration with new target
	 */
	public FunctionRegistration<T> target(T target) {
		this.target = target;
		return this;
	}

	public FunctionRegistration<T> name(String name) {
		return this.names(name);
	}

	public FunctionRegistration<T> names(Collection<String> names) {
		this.names.addAll(names);
		return this;
	}

	public FunctionRegistration<T> names(String... names) {
		return this.names(Arrays.asList(names));
	}

	/**
	 * Transforms (wraps) function identified by the 'target' to its {@code Flux}
	 * equivalent unless it already is. For example, {@code Function<String, String>}
	 * becomes {@code Function<Flux<String>, Flux<String>>}
	 * @param <S> the expected target type of the function (e.g., FluxFunction)
	 * @return {@code FunctionRegistration} with the appropriately wrapped target.
	 *
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <S> FunctionRegistration<S> wrap() {
		FunctionRegistration<S> result;
		if (this.type == null) {
			result = (FunctionRegistration<S>) this;
		}
		else {
			S target = (S) this.target;
			result = new FunctionRegistration<S>(target);
			result.type(this.type.getType());
			boolean flux = type.isWrapper();
			if (!flux) {
				if (target instanceof Function) {
					target = (S) new FluxFunction((Function<?, ?>) target);
				}
				else if (target instanceof Supplier) {
					target = (S) new FluxSupplier((Supplier<?>) target);
				}
				else if (target instanceof Consumer) {
					target = (S) new FluxConsumer((Consumer<?>) target);
				}
			}

			if (Mono.class.isAssignableFrom(type.getOutputWrapper())) {
				target = (S) new FluxToMonoFunction((Function) target);
			}
			else if (Mono.class.isAssignableFrom(type.getInputWrapper())) {
				target = (S) new MonoToFluxFunction((Function) target);
			}
			result = result.target(target).names(this.names)
					.type(result.type.wrap(Flux.class)).properties(this.properties);
		}

		return result;
	}

	@Override
	public void setBeanName(String name) {
		if (CollectionUtils.isEmpty(this.names)) {
			this.name(name);
		}
	}

}
