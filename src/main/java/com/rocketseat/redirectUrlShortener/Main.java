package com.rocketseat.redirectUrlShortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Client s3Client = S3Client.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String pathParameters = (String) input.get("rawPath");
        String shortUrlCode = pathParameters.replace("/", "");

        if (shortUrlCode.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: 'shortUrlCode' is required.");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket("url-shortener-storage-gff")
                .key(shortUrlCode+".json")
                .build();

        // os arquivos do s3 vêm como uma stream de pacotes
        InputStream s3ObjectStream;

        try {
            s3ObjectStream = s3Client.getObject(getObjectRequest);
        } catch (Exception e) {
            throw new RuntimeException("Error fetching URL data from S3: "+ e.getMessage(), e);
        }

        UrlData urlData;
        try {
            urlData = objectMapper.readValue(s3ObjectStream, UrlData.class);
        } catch (Exception e){
            throw new RuntimeException("Error deserializing URL data: "+e.getMessage(), e);
        }

        long currentTimeInSeconds = System.currentTimeMillis() / 1000;
        Map<String, Object> response = new HashMap<>(); // Object = conteúdo da response(json)

        if (urlData.getExpirationTime() < currentTimeInSeconds){ // A timestamp de expiração deve ser maior se for uma data posterior
            response.put("statusCode", 410); // GONE
            response.put("body", "This URL has expired.")   ;
            return response;
        }

        response.put("statusCode", 302); // REDIRECT
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", urlData.getOriginalUrl());
        response.put("headers", headers);

        return response;
    }
}