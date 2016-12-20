package com.rw.velocity;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

/**
 * velocity-android
 * <p>
 * Created by ravindu on 15/12/16.
 * Copyright © 2016 Vortilla. All rights reserved.
 */

class Request
{
    private final RequestBuilder mBuilder;

    private HttpURLConnection mConnection = null;
    private StringBuilder mResponse = new StringBuilder();
    private int mResponseCode = 0;
    private boolean mSuccess = false;

    Request(RequestBuilder builder)
    {
        mBuilder = builder;
    }


    /**
     * DO NOT CALL THIS DIRECTLY
     * Execute the network requet and post the response.
     * This function will be called from a worker thread from within the Threadpool
     */
    void execute()
    {
        NetLog.d("execute rerquest: " + mBuilder.url);

        initializeConnection();

        if(mSuccess) readResponse();

        NetLog.d("HTTP : " + mResponseCode + "/" + mBuilder.requestMethod + " : " + mBuilder.url);

        returnResponse();
    }

    private void setupRequestHeaders()
    {
        if(!mBuilder.headers.isEmpty())
        {
            for(String key : mBuilder.headers.keySet())
                mConnection.setRequestProperty(key, mBuilder.headers.get(key));
        }
    }

    private void setupRequestBody() throws IOException
    {
        if(mBuilder.requestMethod.equalsIgnoreCase("GET") || mBuilder.requestMethod.equalsIgnoreCase("COPY") || mBuilder.requestMethod.equalsIgnoreCase("HEAD")
                || mBuilder.requestMethod.equalsIgnoreCase("PURGE") || mBuilder.requestMethod.equalsIgnoreCase("UNLOCK"))
        {
            //do not send params for these request methods
            return;
        }

        mConnection.setDoInput(true);
        mConnection.setDoOutput(true);

        String params = null;

        if (mBuilder.rawParams != null && mBuilder.rawParams.length() > 0)
        {
            params = mBuilder.rawParams;
        }
        else if (!mBuilder.params.isEmpty())
        {
            params = getFormattedParams();
        }

        if (params != null)
        {
            OutputStream os = mConnection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(params);
            writer.flush();
            writer.close();
            os.close();
        }
    }

    private String getFormattedParams() throws UnsupportedEncodingException
    {
        StringBuilder params = new StringBuilder();
        boolean first = true;

        for (String key : mBuilder.params.keySet())
        {
            if (first)
                first = false;
            else
                params.append("&");

            params.append(URLEncoder.encode(key, "UTF-8"));
            params.append("=");
            params.append(URLEncoder.encode(mBuilder.params.get(key), "UTF-8"));
        }

        return params.toString();
    }

    private void initializeConnection()
    {
        try
        {
            URL url = new URL(mBuilder.url);

            if(url.getProtocol().equalsIgnoreCase("https"))
                mConnection = (HttpsURLConnection) url.openConnection();
            else
                mConnection = (HttpURLConnection) url.openConnection();

            mConnection.setRequestMethod(mBuilder.requestMethod);
            mConnection.setConnectTimeout(Velocity.Settings.TIMEOUT);

            setupRequestHeaders();

            setupRequestBody();

            mConnection.connect();

            mResponseCode = mConnection.getResponseCode();

            mSuccess = true;
        }
        catch (IOException ioe)
        {
            mSuccess = false;
            mResponse = new StringBuilder(ioe.toString());
        }

    }

    private void readResponse()
    {
        try
        {
            if (mResponseCode / 100 == 2) //all 2xx codes are OK
            {
                InputStream in = new BufferedInputStream(mConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = reader.readLine()) != null)
                {
                    mResponse.append(line);
                }

                mSuccess = true;
            }
            else
            {
                InputStream in = new BufferedInputStream(mConnection.getErrorStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                String line;
                while ((line = reader.readLine()) != null)
                {
                    mResponse.append(line);
                }

                mSuccess = false;
            }
        }
        catch (IOException e)
        {
            mSuccess = false;
            mResponse = new StringBuilder(e.toString());
        }
    }

    private void returnResponse()
    {
        final Velocity.Data reply = new Velocity.Data(mBuilder.requestId,
                                                mResponse.toString(),
                                                mResponseCode,
                                                mConnection.getHeaderFields(),
                                                null,
                                                mBuilder.userData);

        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                if(mBuilder.callback != null)
                {
                    if (mSuccess)
                        mBuilder.callback.onVelocitySuccess(reply);
                    else
                        mBuilder.callback.onVelocityFailed(reply);
                }
                else
                    NetLog.d("Warning: No Data callback supplied");
            }
        };

        ThreadPool.getThreadPool().postToUiThread(r);
    }
}