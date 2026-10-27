package app.zelar.ocorrencia.entity;

import app.zelar.ocorrencia.dto.FotografiaValidada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "anexo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_anexo_ocorrencia", columnNames = "ocorrencia_id"),
        @UniqueConstraint(name = "uk_anexo_objeto", columnNames = {"bucket", "chave_objeto"})
})
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ocorrencia_id", nullable = false)
    private UUID ocorrenciaId;
    @Column(nullable = false, length = 100)
    private String bucket;
    @Column(name = "chave_objeto", nullable = false, length = 255)
    private String chaveObjeto;
    @Column(name = "tipo_conteudo", nullable = false, length = 50)
    private String tipoConteudo;
    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;
    @Column(nullable = false)
    private int largura;
    @Column(nullable = false)
    private int altura;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_upload", nullable = false, length = 20)
    private EstadoUpload estadoUpload;
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    protected Anexo() {
    }

    public Anexo(UUID ocorrenciaId, String bucket, FotografiaValidada fotografia, LocalDateTime instante) {
        this.ocorrenciaId = ocorrenciaId;
        this.bucket = bucket;
        this.chaveObjeto = UUID.randomUUID() + "." + fotografia.extensao();
        this.tipoConteudo = fotografia.tipoConteudo();
        this.tamanhoBytes = fotografia.tamanhoBytes();
        this.largura = fotografia.largura();
        this.altura = fotografia.altura();
        this.estadoUpload = EstadoUpload.PENDENTE;
        this.dataCriacao = instante;
        this.dataAtualizacao = instante;
    }

    public void disponibilizar(LocalDateTime instante) {
        if (estadoUpload != EstadoUpload.PENDENTE) {
            throw new IllegalStateException("A reserva não está pendente de envio.");
        }
        estadoUpload = EstadoUpload.DISPONIVEL;
        dataAtualizacao = instante;
    }

    public void aguardarLimpeza(LocalDateTime instante) {
        if (estadoUpload == EstadoUpload.DISPONIVEL) {
            throw new IllegalStateException("Uma fotografia disponível não pode ser compensada como envio pendente.");
        }
        estadoUpload = EstadoUpload.LIMPEZA_PENDENTE;
        dataAtualizacao = instante;
    }

    public UUID getId() { return id; }
    public UUID getOcorrenciaId() { return ocorrenciaId; }
    public String getBucket() { return bucket; }
    public String getChaveObjeto() { return chaveObjeto; }
    public String getTipoConteudo() { return tipoConteudo; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public int getLargura() { return largura; }
    public int getAltura() { return altura; }
    public EstadoUpload getEstadoUpload() { return estadoUpload; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
}
