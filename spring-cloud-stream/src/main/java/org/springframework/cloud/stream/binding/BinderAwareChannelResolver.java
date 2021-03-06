/*
 * Copyright 2013-2016 the original author or authors.
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

package org.springframework.cloud.stream.binding;

import java.util.Properties;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.BeanFactoryMessageChannelDestinationResolver;
import org.springframework.messaging.core.DestinationResolutionException;

/**
 * A {@link org.springframework.messaging.core.DestinationResolver} implementation that
 * resolves the channel from the bean factory and, if not present, creates a new channel
 * and adds it to the factory after binding it to the binder. The binder is optionally
 * determined with a prefix preceding a colon.
 * @author Mark Fisher
 * @author Gary Russell
 */
public class BinderAwareChannelResolver extends BeanFactoryMessageChannelDestinationResolver {

	private final BinderFactory<MessageChannel> binderFactory;

	private final Properties producerProperties;

	private DefaultListableBeanFactory beanFactory;

	public BinderAwareChannelResolver(BinderFactory<MessageChannel> binderFactory, Properties producerProperties) {
		this.binderFactory = binderFactory;
		this.producerProperties = producerProperties;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (DefaultListableBeanFactory) beanFactory;
		}
	}

	@Override
	public MessageChannel resolveDestination(String name) {
		MessageChannel channel = null;
		try {
			return super.resolveDestination(name);
		}
		catch (DestinationResolutionException e) {
		}
		synchronized (this) {
			try {
				return super.resolveDestination(name);
			}
			catch (DestinationResolutionException e) {
			}
			if (this.beanFactory != null && this.binderFactory != null) {
				channel = new DirectChannel();
				this.beanFactory.registerSingleton(name, channel);
				channel = (MessageChannel) this.beanFactory.initializeBean(channel, name);
				String transport = null;
				if (name.contains(":")) {
					String[] tokens = name.split(":", 2);
					if (tokens.length == 2) {
						transport = tokens[0];
					}
					else if (tokens.length != 1) {
						throw new IllegalArgumentException("Unrecognized channel naming scheme: " + name + " , should be" +
								" [<transport>:]<name>");
					}
				}
				Binder<MessageChannel> binder = binderFactory.getBinder(transport);
				binder.bindProducer(name, channel, this.producerProperties);
			}
			return channel;
		}
	}

}
