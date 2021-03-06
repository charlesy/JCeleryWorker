package org.loadui.jcelery.base;

import com.google.common.base.Predicate;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.rabbitmq.client.*;
import org.loadui.jcelery.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.loadui.jcelery.utils.JobUtils.waitUntil;

public abstract class AbstractWorker<T extends Job> extends AbstractExecutionThreadService
{
	protected TaskHandler<T> onJob;
	private Connection connection;
	private Channel channel;
	private ConnectionProvider connectionProvider;
	private ConsumerProvider consumerProvider;
	protected MessageConsumer consumer;
	private final Queue queue;
	private final Exchange exchange;

	private Logger log = LoggerFactory.getLogger( this.getClass() );

	protected static final int POLLING_TIMEOUT = 500;
	protected static final boolean AMQP_INITIATED_BY_APPLICATION = true;
	protected static final boolean AMQP_HARD_ERROR = true;
	protected static final boolean AMQP_REQUEUE = true;
	protected static final boolean AMQP_DURABLE = true;
	protected static final boolean AMQP_EXCLUSIVE = false;
	protected static final boolean AMQP_AUTO_DELETE = false;
	protected static final boolean AMQP_AUTO_ACK = false;

	public AbstractWorker( ConnectionProvider connectionProvider, ConsumerProvider consumerProvider,
								  Queue queue, Exchange exchange )
	{
		this.consumerProvider = consumerProvider;
		this.connectionProvider = connectionProvider;
		this.queue = queue;
		this.exchange = exchange;
	}

	public AbstractWorker( String host, int port, String username, String password, String vhost,
								  Queue queue, Exchange exchange )
	{
		this( new RabbitConnectionProvider( host, port, username, password, vhost ), new RabbitConsumerProvider(), queue, exchange );
	}

	public void setTaskHandler( TaskHandler<T> handler )
	{
		this.onJob = handler;
	}

	AbstractWorker startAsynchronous()
	{
		startAsync();
		return this;
	}

	AbstractWorker waitUntilRunning()
	{
		awaitRunning();
		return this;
	}

	AbstractWorker stopAsynchronous()
	{
		stopAsync();
		return this;
	}

	AbstractWorker waitUntilTerminated()
	{
		awaitTerminated();
		return this;
	}

	public void respond( String id, String response )
	{

		log.debug( getClass().getSimpleName() + ": Trying to respond " + response + " for job " + id );
		String rabbitId = id.replaceAll( "-", "" );

		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().contentType( "application/json" ).build();
		try
		{
			channel.exchangeDeclare( getExchange(), "direct", false, false, null );
			channel.basicPublish( getExchange(), rabbitId, properties, response.getBytes() );
		}
		catch( IOException e )
		{
		 	log.error( "Unable to respond a celery message", e );
		}
	}


	protected void connect() throws IOException, ShutdownSignalException
	{
		log.debug( "Connecting to rabbitMQ broker: " + connectionProvider.getFactory().getHost() + ":" + connectionProvider.getFactory().getPort() );

		if( channel != null )
		{
			channel.abort();
		}
		if( connection == null )
		{
			connection = connectionProvider.getFactory().newConnection();
		}

		channel = connection.createChannel();
		consumer = replaceConsumer( channel );

		channel.basicRecover( true );
		channel.queueDeclare( getQueue(), AMQP_DURABLE, AMQP_EXCLUSIVE, AMQP_AUTO_DELETE, new HashMap<String, Object>() );
		channel.basicConsume( getQueue(), AMQP_AUTO_ACK, consumer.getConsumer() );
	}

	protected abstract void run() throws Exception;

	public String getQueue()
	{
		return queue.getQueue();
	}

	public String getExchange()
	{
		return exchange.getExchange();
	}

	public TaskHandler<T> getTaskHandler()
	{
		return onJob;
	}

	public Channel getChannel()
	{
		return channel;
	}

	public Connection getConnection()
	{
		return connection;
	}

	public ConnectionProvider getConnectionProvider()
	{
		return connectionProvider;
	}

	protected void waitAndRecover( int timeout ) throws InterruptedException, IOException
	{
		Thread.sleep( timeout );
		log.debug( "Attempting connection recovery" );
		connect();
	}

	protected void waitUntilJobCompleted( final Job job )
	{
		try
		{
			waitUntil( "Wait for the job to complete or fail", new Callable<Boolean>()
			{
				@Override
				public Boolean call() throws Exception
				{
					return !job.isInProgress();
				}
			}, 180, TimeUnit.SECONDS );
		}
		catch( InterruptedException e )
		{
			log.error( "Interrupted while waiting for job to start." );
		}
		if( job.isInProgress() )
		{
			job.revoke();
		}
	}

	protected abstract MessageConsumer replaceConsumer( Channel channel ) throws IOException, ShutdownSignalException;

	protected abstract void initializeConnection() throws InterruptedException;

	public ConsumerProvider getConsumerProvider()
	{
		return consumerProvider;
	}
}
