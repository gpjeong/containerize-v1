package com.containerize.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

@Configuration
public class RestTemplateConfig {

    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);

    @Autowired
    private AppConfig appConfig;

    @Bean
    public RestTemplate restTemplate() {
        if (appConfig.getSsl().isTrustAll()) {
            logger.warn("SSL trust-all mode is ENABLED. All SSL certificates will be trusted. Do NOT use in production!");
            return createTrustAllRestTemplate();
        }

        logger.info("SSL trust-all mode is disabled. Using default JDK truststore.");
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        return new RestTemplate(factory);
    }

    private RestTemplate createTrustAllRestTemplate() {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(
                            PoolingHttpClientConnectionManagerBuilder.create()
                                    .setSSLSocketFactory(
                                            SSLConnectionSocketFactoryBuilder.create()
                                                    .setSslContext(sslContext)
                                                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);
            factory.setConnectTimeout(10000);

            return new RestTemplate(factory);
        } catch (Exception e) {
            logger.error("Failed to create trust-all RestTemplate, falling back to default", e);
            return new RestTemplate();
        }
    }
}
