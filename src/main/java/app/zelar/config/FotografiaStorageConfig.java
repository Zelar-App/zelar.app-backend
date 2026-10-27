package app.zelar.config;

import app.zelar.ocorrencia.storage.FotografiaStorage;
import app.zelar.ocorrencia.storage.SupabaseFotografiaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
public class FotografiaStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.fotografias.habilitado", havingValue = "true")
    public FotografiaStorage fotografiaStorage(@Value("${app.fotografias.url:}") String url,
                                               @Value("${app.fotografias.secret-key:}") String secretKey,
                                               @Value("${app.fotografias.bucket:ocorrencias-fotografias}") String bucket) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new SupabaseFotografiaStorage(RestClient.builder().requestFactory(factory), URI.create(url), secretKey, bucket);
    }
}
