package app.zelar.ocorrencia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.springframework.util.Assert;

@Embeddable
public class Localizacao {

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "endereco_aproximado", length = 255)
    private String enderecoAproximado;

    protected Localizacao() {
    }

    public Localizacao(Double latitude, Double longitude, String enderecoAproximado) {
        Assert.isTrue(latitude != null && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90,
                "A latitude deve estar entre -90 e 90.");
        Assert.isTrue(longitude != null && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180,
                "A longitude deve estar entre -180 e 180.");
        Assert.isTrue(enderecoAproximado == null || enderecoAproximado.length() <= 255,
                "O endereço aproximado deve possuir no máximo 255 caracteres.");
        this.latitude = latitude;
        this.longitude = longitude;
        this.enderecoAproximado = enderecoAproximado;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getEnderecoAproximado() {
        return enderecoAproximado;
    }
}
