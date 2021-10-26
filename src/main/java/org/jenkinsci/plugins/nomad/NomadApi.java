package org.jenkinsci.plugins.nomad;

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.nomad.Api.*;

import java.io.IOException;
import java.util.logging.Level;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Logger;

import hudson.util.Secret;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class NomadApi {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Logger LOGGER = Logger.getLogger(NomadApi.class.getName());
    private final NomadCloud cloud;
    private volatile OkHttpClient client;

    NomadApi(NomadCloud cloud) {
        this.cloud = cloud;
    }

    JobInfo[] getJobs(Request request) {
        JobInfo[] jobs;
        String body = checkResponseAndGetBody(request);
        Gson gson = new Gson();

        jobs = gson.fromJson(body, JobInfo[].class);
        return jobs;
    }

    /**
     * Executes a given request and returns the response body. <b>Note:</b> This method reuses the underlying http client but in case of
     * an error, this client gets destroyed and recreated when this method is called again.
     * @param request Any request (not null)
     * @return Response body as String or an empty String. (not null)
     */
    String checkResponseAndGetBody (Request request) {
        String bodyString = "";
        try (Response response = client().newCall(request).execute();
             ResponseBody responseBody = response.body();
        ) {
            bodyString = responseBody.string();
            if (!response.isSuccessful()) {
                LOGGER.log(Level.SEVERE, "Request was not successful! Code: "+response.code()+", Body: '"+bodyString+"'");
                if (Arrays.asList(401, 403, 500).contains(response.code())) {
                    resetClient();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage() + "\nRequest:\n" + request);
            resetClient();
        } catch (NullPointerException e) {
            LOGGER.log(Level.SEVERE, "Error: Got no Nomad response." + "\nRequest:\n" + request.toString());
        }
        return bodyString;
    }

    /**
     * Reset client so that a new client gets created when {@link #client()} is called.
     */
    private void resetClient() {
        // The fact that a certificate can expire, requires that the client can be recreated at runtime (Assumption: client certificate
        // is renewed somehow but the path is still the same).
        client = null;
        LOGGER.log(Level.INFO, "Client has been reset!");
    }

    /**
     * Provides an {@link OkHttpClient} instance. Create a new client or reuse the existing one. This method is thread-safe.
     * @return OkHttpClient instance (not null but TLS might not work)
     */
    private OkHttpClient client() {
        // We cannot use a static client instance because TLS must be configured when the client gets created.
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
                    if (cloud.isTlsEnabled()) {
                        try {
                            OkHttpClientHelper.initTLS(clientBuilder,
                                    cloud.getClientCertificate(), Secret.toString(cloud.getClientPassword()),
                                    cloud.getServerCertificate(), Secret.toString(cloud.getServerPassword()));
                        } catch (GeneralSecurityException | IOException e) {
                            LOGGER.log(Level.SEVERE, "Nomad TLS configuration failed! " + e.getMessage());
                        }
                    }
                    client = clientBuilder.build();
                }
            }
        }

        return client;
    }

    void startWorker(String workerName, String nomadToken, String jnlpSecret, NomadWorkerTemplate template) {

        String workerJob = buildWorkerJob(
                workerName,
                jnlpSecret,
                template.getJobTemplate()
        );

        LOGGER.log(Level.FINE, workerJob);

        RequestBody body = RequestBody.create(workerJob, JSON);
        Request.Builder builder = new Request.Builder()
                .url(cloud.getNomadUrl() + "/v1/jobs");

        if (StringUtils.isNotEmpty(nomadToken))
            builder = builder.header("X-Nomad-Token", nomadToken);

        Request request = builder.put(body)
                .build();

        checkResponseAndGetBody(request);
    }

    void stopWorker(String workerName, String nomadToken) {

        Request.Builder builder = new Request.Builder()
                .url(cloud.getNomadUrl() + "/v1/job/" + workerName);

        if (StringUtils.isNotEmpty(nomadToken))
            builder = builder.addHeader("X-Nomad-Token", nomadToken);

        Request request = builder.delete()
                .build();

        checkResponseAndGetBody(request);
    }

    JobInfo[] getRunningWorkers(String prefix, String nomadToken) {

        JobInfo[] nomadJobs = null;

        Request.Builder builder = new Request.Builder()
                .url(cloud.getNomadUrl() + "/v1/jobs?prefix=" + prefix)
                .get();

        if (StringUtils.isNotEmpty(nomadToken))
            builder = builder.addHeader("X-Nomad-Token", nomadToken);

        Request request = builder.build();
        nomadJobs = getJobs(request);

        return nomadJobs;
    }

    String buildWorkerJob(
            String name,
            String secret,
            String jobTemplate
    ) {
        return jobTemplate
                .replace("%WORKER_NAME%", name)
                .replace("%WORKER_SECRET%", secret)
                ;
    }

}
