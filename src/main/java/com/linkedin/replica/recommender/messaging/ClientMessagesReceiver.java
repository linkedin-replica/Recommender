package com.linkedin.replica.recommender.messaging;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linkedin.replica.recommender.utils.Configuration;
import com.linkedin.replica.recommender.exceptions.BadRequestException;
import com.linkedin.replica.recommender.services.RecommendationService;
import com.linkedin.replica.recommender.services.Workers;
import com.rabbitmq.client.*;

public class ClientMessagesReceiver {
    private final Configuration configuration = Configuration.getInstance();
    private final String QUEUE_NAME = configuration.getAppConfig("rabbitmq.queue.name");
    private final String RABBIT_MQ_IP = configuration.getAppConfig("rabbitmq.ip");
    private final String RABBIT_MQ_USERNAME = configuration.getAppConfig("rabbitmq.username");
    private final String RABBIT_MQ_PASSWORD = configuration.getAppConfig("rabbitmq.password");
    private ConnectionFactory factory;
    private Channel channel;
    private Connection connection;
    private Gson gson = new Gson();

    public ClientMessagesReceiver() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setUsername(RABBIT_MQ_USERNAME);
        factory.setPassword(RABBIT_MQ_PASSWORD);
        factory.setHost(RABBIT_MQ_IP);
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Create queue if not exists
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);

        // set unacknowledged limit to 1 message
        channel.basicQos(1);
        channel.basicConsume(QUEUE_NAME, true, initConsumer());
    }

    private Consumer initConsumer() {
        // Create the messages consumer
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        // Create the response message properties
                        AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                                .Builder()
                                .correlationId(properties.getCorrelationId())
                                .build();

                        // Extract the request arguments
                        JsonObject object = new JsonParser().parse(new String(body)).getAsJsonObject();
                        String commandName = object.get("commandName").getAsString();
                        HashMap<String, Object> args = new HashMap<>();
                        for (String key : object.keySet())
                            if (!key.equals("commandName"))
                                args.put(key, object.get(key).getAsString());

                        // Call the service and form the response
                        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                        try {
                            Object results = new RecommendationService().serve(commandName, args);

                            response.put("statusCode", 200);
                            if (results != null)
                                response.put("results", results);

                        } catch (BadRequestException e) {
                            // set status code to 400
                            response.put("statusCode", "400");
                            response.put("error", e.getMessage());
                        } catch (Exception e) {
                            e.printStackTrace();
                            // set status code to 500
                            response.put("statusCode", "500");
                            response.put("error", "Internal server error.");

                            // TODO write the error to a log
                        }
                        // publish the response to the "replyTo" queue
                        byte[] jsonResponse = gson.toJson(response).getBytes();
                        try {
                            channel.basicPublish("", properties.getReplyTo(), replyProps, jsonResponse);
                        } catch (IOException e) {
                            e.printStackTrace();
                            // TODO write the error to a log
                        }
                    }
                };

                // submit task to workers to assign a worker to handle this request
                Workers.getInstance().submit(runnable);
            }
        };

        return consumer;
    }
}