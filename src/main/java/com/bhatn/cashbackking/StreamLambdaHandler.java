package com.bhatn.cashbackking;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest; // Modern V2 Request format
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamLambdaHandler implements RequestStreamHandler {

    // 1. Correct generic type to use the V2 request model
    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            // 2. Instantiate the V2-specific handler matching the modern API/Function URL structure
            handler = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(CashbackKingApplication.class);
            // Core Fix: Instruct the proxy to read multipart content natively as raw binary data
            handler.getContainerConfig().addBinaryContentTypes("multipart/form-data");
            handler.getContainerConfig().addBinaryContentTypes("image/png");
            handler.getContainerConfig().addBinaryContentTypes("image/jpeg");
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Could not initialize Spring framework", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
            throws IOException {
        handler.proxyStream(inputStream, outputStream, context);
    }
}