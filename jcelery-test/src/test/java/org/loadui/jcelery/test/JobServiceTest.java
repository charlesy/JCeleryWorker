package org.loadui.jcelery.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.loadui.jcelery.ConnectionProvider;
import org.loadui.jcelery.JobService;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.loadui.jcelery.framework.util.LocalBundleResolver.jCeleryCore;
import static org.loadui.jcelery.framework.util.LocalBundleResolver.jobsApi;
import static org.loadui.jcelery.test.OsgiTestUtils.assertAllBundlesActive;
import static org.loadui.jcelery.test.OsgiTestUtils.springDmBundles;
import static org.ops4j.pax.exam.CoreOptions.*;

@RunWith( PaxExam.class )
@ExamReactorStrategy( PerClass.class )
public class JobServiceTest
{
	@Inject
	BundleContext context;

	@Inject
    JobService celeryService;

	@Inject
    ConnectionProvider connectionProvider;

	@Configuration
	public Option[] config()
	{
		return options(
				systemPackages(
						"com.sun.crypto.provider",
						"com.sun.net.ssl",
						"com.sun.net.ssl.internal.ssl",
						"org.w3c.dom.traversal",
						"javax.transaction.xa",
						"sun.io",
						"javax.net.ssl",
						"org.apache.commons.logging",
						"javax.xml.parsers",
						"javax.net",
						"javax.security.sasl",
						"org.w3c.dom",
						"org.xml.sax",
						"javax.security.auth.callback",
				"sun.misc" ),
		springDmBundles(),
				junitBundles(),
				jobsApi(),
				jCeleryCore(),
				mavenBundle( "com.google.guava", "guava" ).versionAsInProject(),
				mavenBundle( "com.rabbitmq", "amqp-client" ).versionAsInProject(),
				mavenBundle( "com.googlecode.json-simple", "json-simple" ).versionAsInProject()
		);
	}

	@Test
	public void allBundlesAreActive()
	{
		assertAllBundlesActive( context );
	}

	@Test
	public void celeryServiceIsExposed()
	{
		assertThat( celeryService, notNullValue() );
	}

	@Test
	public void celeryServiceUsesDefaultMqConnection()
	{
		assertThat( "should have default host defined in bundle context", connectionProvider.getFactory().getHost(), is( equalTo("platform") ) );
		assertThat( "should have default port defined in bundle context", connectionProvider.getFactory().getPort(), is( equalTo(5672) ) );
	}

}
