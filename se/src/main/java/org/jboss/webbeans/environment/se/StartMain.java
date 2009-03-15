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
package org.jboss.webbeans.environment.se;

import javax.event.Observes;
import javax.inject.manager.Manager;

import org.jboss.webbeans.bootstrap.api.Bootstrap;
import org.jboss.webbeans.bootstrap.api.Environments;
import org.jboss.webbeans.bootstrap.spi.WebBeanDiscovery;
import org.jboss.webbeans.context.DependentContext;
import org.jboss.webbeans.context.api.BeanStore;
import org.jboss.webbeans.context.api.helpers.ConcurrentHashMapBeanStore;
import org.jboss.webbeans.environment.se.beans.ParametersFactory;
import org.jboss.webbeans.environment.se.discovery.WebBeanDiscoveryImpl;
import org.jboss.webbeans.environment.se.events.Shutdown;
import org.jboss.webbeans.environment.se.resources.NoNamingContext;
import org.jboss.webbeans.environment.se.util.Reflections;
import org.jboss.webbeans.log.Log;
import org.jboss.webbeans.log.Logging;
import org.jboss.webbeans.resources.spi.NamingContext;

/**
 * This is the main class that should always be called from the command
 * line for a WebBEans SE app. Something like:
 * <code>
 * java -jar MyApp.jar org.jboss.webbeans.environment.se.StarMain arguments
 * </code>
 * @author Peter Royle
 * @author Pete Muir
 */
public class StartMain
{

    private static final String BOOTSTRAP_IMPL_CLASS_NAME = "org.jboss.webbeans.bootstrap.WebBeansBootstrap";
   
    private final Bootstrap bootstrap;
    private final BeanStore applicationBeanStore;
    private String[] args;
    private boolean hasShutdownBeenCalled = false;
    Log log = Logging.getLog( StartMain.class );

    public StartMain( String[] commandLineArgs )
    {
        this.args = commandLineArgs;
        try
        {
            bootstrap = Reflections.newInstance(BOOTSTRAP_IMPL_CLASS_NAME, Bootstrap.class);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Error loading Web Beans bootstrap, check that Web Beans is on the classpath", e);
        }
        this.applicationBeanStore = new ConcurrentHashMapBeanStore();
    }

    private void go()
    { 
        bootstrap.setEnvironment(Environments.SE);
        bootstrap.getServices().add(WebBeanDiscovery.class, new WebBeanDiscoveryImpl());
        bootstrap.getServices().add(NamingContext.class, new NoNamingContext());
        bootstrap.setApplicationContext(applicationBeanStore);
        bootstrap.initialize();
        bootstrap.boot();
        bootstrap.getManager().getInstanceByType(ParametersFactory.class).setArgs(args);
        DependentContext.INSTANCE.setActive(true);
    }

    /**
     * The main method called from the command line. This little puppy
     * will get the ball rolling.
     * @param args the command line arguments
     */
    public static void main( String[] args )
    {
        new StartMain( args ).go();
    }

    /**
     * The observer of the optional shutdown request which will in turn fire the
     * Shutdown event.
     * @param shutdownRequest
     */
    public void shutdown( @Observes @Shutdown Manager shutdownRequest )
    {
        synchronized (this)
        {

            if (!hasShutdownBeenCalled)
            {
                hasShutdownBeenCalled = true;
                bootstrap.shutdown();
            } else
            {
                log.debug( "Skipping spurious call to shutdown");
                log.trace( Thread.currentThread().getStackTrace() );
            }
        }
    }

}