/**
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.webbeans.environment.se.deployment;

import java.util.Set;

/**
 * A deployment handler is responsible for processing found resources
 *
 * All deployment handlers should specify a unique name under which they
 * will be registered with the {@link DeploymentStrategy}
 *
 * @author Pete Muir
 *
 */
public interface DeploymentHandler
{

    public Set<FileDescriptor> getResources(  );

    public void setResources( Set<FileDescriptor> resources );
}